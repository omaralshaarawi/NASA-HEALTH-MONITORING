package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.engine.EnvironmentResolver;
import com.nasa.spacehealth.exception.ApiException;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.Contract;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import com.nasa.spacehealth.planner.model.CountermeasurePlan.Trigger;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import com.nasa.spacehealth.planner.model.ResourceBlock;
import com.nasa.spacehealth.service.SchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Assessment in, complete plan out. Where the draft comes from, in order:
//   1. a saved plan for exactly the same LLM input (recorded file or earlier live answer), unless fresh=true
//   2. a live LLM answer
//   3. a saved plan again, if the live call failed
//   4. the rule-based offline planner
// Whatever the source, the draft is clamped and checked, and the resource numbers are computed here.
@Service
public class PlannerService {

    public static final String OFFLINE_MODEL = "offline-fallback";

    public enum Source { SAVED, LIVE, OFFLINE }

    public record Result(CountermeasurePlan plan, Source source) {
    }

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private static final int LLM_ATTEMPTS = 2;
    private static final double MIN_MASS_KG = 30;
    private static final double MAX_MASS_KG = 200;

    // what one assessment turns into before any LLM call
    private record Request(AssessmentResponse assessment, double gravity, List<ExerciseMenu.Item> allowed,
                           String input, String inputHash) {
    }

    private final DraftGenerator llm;
    private final FallbackPlanner fallback;
    private final DraftPolicy policy;
    private final ResourcesService resources;
    private final SavedPlans savedPlans;
    private final ExerciseMenu menu;
    private final EnvironmentResolver environments;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final double defaultMassKg;

    private final Map<String, CountermeasurePlan> latestByAstronaut = new ConcurrentHashMap<>();

    public PlannerService(DraftGenerator llm, FallbackPlanner fallback, DraftPolicy policy, ResourcesService resources,
                          SavedPlans savedPlans, ExerciseMenu menu, EnvironmentResolver environments,
                          SchemaValidator schemaValidator, ObjectMapper mapper, Clock clock,
                          @Value("${planner.default-mass-kg:80}") double defaultMassKg) {
        this.llm = llm;
        this.fallback = fallback;
        this.policy = policy;
        this.resources = resources;
        this.savedPlans = savedPlans;
        this.menu = menu;
        this.environments = environments;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
        this.clock = clock;
        this.defaultMassKg = defaultMassKg;
    }

    public Result generate(JsonNode assessmentJson, Double massKg, boolean fresh) {
        AssessmentResponse assessment = readAssessment(assessmentJson);
        double mass = massKg != null ? massKg : defaultMassKg;
        if (!(mass >= MIN_MASS_KG && mass <= MAX_MASS_KG)) {
            throw ApiException.badRequest("massKg must be between " + (int) MIN_MASS_KG + " and " + (int) MAX_MASS_KG);
        }
        Result result = buildPlan(assessment, mass, newPlanId(), fresh);
        latestByAstronaut.put(result.plan().astronautId(), result.plan());
        return result;
    }

    // Last generated plan, or the recorded day-14 plan for the seeded astronauts.
    public CountermeasurePlan latest(String astronautId) {
        CountermeasurePlan plan = latestByAstronaut.get(astronautId);
        if (plan == null) {
            plan = savedPlans.recordedFor(astronautId);
        }
        if (plan == null) {
            throw ApiException.notFound("No plan for '" + astronautId + "' yet. POST /api/plans/generate first.");
        }
        return plan;
    }

    void forgetGeneratedPlans() {
        latestByAstronaut.clear();
        savedPlans.reset();
    }

    Result buildPlan(AssessmentResponse assessment, double massKg, String planId, boolean fresh) {
        Request req = request(assessment);
        CountermeasurePlan saved = savedPlans.find(req.inputHash());

        if (saved != null && (!fresh || !llm.isConfigured())) {
            CountermeasurePlan plan = fromSaved(req, saved, massKg, planId);
            if (plan != null) {
                return new Result(plan, Source.SAVED);
            }
        }
        if (llm.isConfigured()) {
            JsonNode draft = askLlm(req);
            if (draft != null) {
                CountermeasurePlan plan = assemble(req, draft, llm.model(), massKg, planId);
                savedPlans.remember(plan);
                return new Result(plan, Source.LIVE);
            }
        }
        if (saved != null) {
            CountermeasurePlan plan = fromSaved(req, saved, massKg, planId);
            if (plan != null) {
                return new Result(plan, Source.SAVED);
            }
        }
        return new Result(offlinePlan(assessment, massKg, planId), Source.OFFLINE);
    }

    CountermeasurePlan offlinePlan(AssessmentResponse assessment, double massKg, String planId) {
        Request req = request(assessment);
        JsonNode draft = checked(req, mapper.valueToTree(fallback.draft(assessment, req.allowed())));
        if (draft == null) {
            throw new IllegalStateException("The offline plan failed validation for " + assessment.astronautId());
        }
        return assemble(req, draft, OFFLINE_MODEL, massKg, planId);
    }

    // Live LLM only, nothing else. Used to record the plans in resources/fallback-plans.
    CountermeasurePlan livePlan(AssessmentResponse assessment, double massKg, String planId) {
        Request req = request(assessment);
        JsonNode draft = askLlm(req);
        if (draft == null) {
            throw new IllegalStateException("No usable answer from " + llm.model() + " for " + assessment.astronautId());
        }
        return assemble(req, draft, llm.model(), massKg, planId);
    }

    String inputHash(AssessmentResponse assessment) {
        return request(assessment).inputHash();
    }

    private Request request(AssessmentResponse assessment) {
        double gravity = environments.preset(assessment.environment().id()).gravityG();
        List<ExerciseMenu.Item> allowed = menu.allowedAt(gravity);
        String input = PlannerPrompt.input(mapper, assessment, menu, allowed).toString();
        return new Request(assessment, gravity, allowed, input, sha256(PlannerPrompt.SYSTEM + "\n" + input));
    }

    // Saved drafts go through the same checks as live ones; the numbers are recomputed for this mass.
    private CountermeasurePlan fromSaved(Request req, CountermeasurePlan saved, double massKg, String planId) {
        JsonNode draft = checked(req, mapper.valueToTree(saved.draft()));
        if (draft == null) {
            log.warn("Saved plan {} no longer passes the checks, ignoring it", saved.planId());
            return null;
        }
        return assemble(req, draft, saved.llm().model(), massKg, planId);
    }

    // Null means "use the next source": the call failed or the answer was rejected twice.
    private JsonNode askLlm(Request req) {
        List<String> allowedIds = req.allowed().stream().map(ExerciseMenu.Item::exerciseId).toList();
        String prompt = req.input();
        for (int attempt = 1; attempt <= LLM_ATTEMPTS; attempt++) {
            try {
                JsonNode draft = policy.clamp(llm.generateDraft(PlannerPrompt.SYSTEM, prompt, allowedIds),
                        req.assessment());
                List<String> problems = policy.check(draft, req.assessment(), req.gravity());
                if (problems.isEmpty()) {
                    return draft;
                }
                log.warn("Plan draft from {} rejected (attempt {}): {}", llm.model(), attempt, problems);
                prompt = req.input() + PlannerPrompt.retryNote(problems);
            } catch (RuntimeException e) {
                log.warn("Call to {} failed: {}", llm.model(), e.getMessage());
                return null;
            }
        }
        return null;
    }

    private JsonNode checked(Request req, JsonNode draft) {
        JsonNode clamped = policy.clamp(draft, req.assessment());
        return policy.check(clamped, req.assessment(), req.gravity()).isEmpty() ? clamped : null;
    }

    private CountermeasurePlan assemble(Request req, JsonNode draftJson, String model, double massKg, String planId) {
        AssessmentResponse a = req.assessment();
        CountermeasurePlanDraft draft = mapper.convertValue(draftJson, CountermeasurePlanDraft.class);
        ResourceBlock resourceBlock = resources.buildResourceBlock(draft.sessions(), massKg);
        List<Trigger> triggers = a.indicators().stream()
                .filter(i -> i.status() != Status.GREEN)
                .map(i -> new Trigger(i.id(), i.status(), i.reasons()))
                .toList();

        return new CountermeasurePlan(
                Contract.SCHEMA_VERSION,
                planId,
                a.astronautId(),
                a.missionDay(),
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(),
                a.environment().id(),
                triggers,
                draft,
                resourceBlock,
                new CountermeasurePlan.Llm(model, req.inputHash()),
                Contract.DISCLAIMER);
    }

    private AssessmentResponse readAssessment(JsonNode body) {
        List<String> errors = schemaValidator.validate("AssessmentResponse", body);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("Body is not a valid AssessmentResponse: " + String.join("; ", errors));
        }
        try {
            return mapper.convertValue(body, AssessmentResponse.class);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Body is not a valid AssessmentResponse");
        }
    }

    private static String newPlanId() {
        return "plan_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
