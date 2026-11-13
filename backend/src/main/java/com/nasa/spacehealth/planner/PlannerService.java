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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
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

// Assessment in, complete plan out: the LLM (or the offline planner) writes the draft,
// the draft is clamped and checked, and the resource numbers are computed here.
@Service
public class PlannerService {

    public static final String OFFLINE_MODEL = "offline-fallback";

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);
    private static final int LLM_ATTEMPTS = 2;
    private static final double MIN_MASS_KG = 30;
    private static final double MAX_MASS_KG = 200;

    private final DraftGenerator llm;
    private final FallbackPlanner fallback;
    private final DraftPolicy policy;
    private final ResourcesService resources;
    private final ExerciseMenu menu;
    private final EnvironmentResolver environments;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final double defaultMassKg;

    private final Map<String, CountermeasurePlan> latestByAstronaut = new ConcurrentHashMap<>();

    public PlannerService(DraftGenerator llm, FallbackPlanner fallback, DraftPolicy policy, ResourcesService resources,
                          ExerciseMenu menu, EnvironmentResolver environments, SchemaValidator schemaValidator,
                          ObjectMapper mapper, Clock clock,
                          @Value("${planner.default-mass-kg:80}") double defaultMassKg) {
        this.llm = llm;
        this.fallback = fallback;
        this.policy = policy;
        this.resources = resources;
        this.menu = menu;
        this.environments = environments;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
        this.clock = clock;
        this.defaultMassKg = defaultMassKg;
    }

    public CountermeasurePlan generate(JsonNode assessmentJson, Double massKg) {
        AssessmentResponse assessment = readAssessment(assessmentJson);
        double mass = massKg != null ? massKg : defaultMassKg;
        if (!(mass >= MIN_MASS_KG && mass <= MAX_MASS_KG)) {
            throw ApiException.badRequest("massKg must be between " + (int) MIN_MASS_KG + " and " + (int) MAX_MASS_KG);
        }

        CountermeasurePlan plan = buildPlan(assessment, mass, "plan_" + UUID.randomUUID().toString().substring(0, 8),
                llm.isConfigured());
        latestByAstronaut.put(plan.astronautId(), plan);
        return plan;
    }

    // Last generated plan, or the pre-generated day-14 plan for the seeded astronauts.
    public CountermeasurePlan latest(String astronautId) {
        CountermeasurePlan plan = latestByAstronaut.get(astronautId);
        if (plan == null && astronautId.matches("[A-Za-z0-9_]+")) {
            plan = readFallbackFile(astronautId);
        }
        if (plan == null) {
            throw ApiException.notFound("No plan for '" + astronautId + "' yet. POST /api/plans/generate first.");
        }
        return plan;
    }

    void forgetGeneratedPlans() {
        latestByAstronaut.clear();
    }

    CountermeasurePlan buildPlan(AssessmentResponse assessment, double massKg, String planId, boolean useLlm) {
        double gravity = environments.preset(assessment.environment().id()).gravityG();
        List<ExerciseMenu.Item> allowed = menu.allowedAt(gravity);
        String input = PlannerPrompt.input(mapper, assessment, menu, allowed).toString();

        JsonNode draftJson = useLlm ? askLlm(assessment, input, allowed, gravity) : null;
        String model = llm.model();
        if (draftJson == null) {
            draftJson = offlineDraft(assessment, allowed, gravity);
            model = OFFLINE_MODEL;
        }
        CountermeasurePlanDraft draft = mapper.convertValue(draftJson, CountermeasurePlanDraft.class);
        ResourceBlock resourceBlock = resources.buildResourceBlock(draft.sessions(), massKg);

        List<Trigger> triggers = assessment.indicators().stream()
                .filter(i -> i.status() != Status.GREEN)
                .map(i -> new Trigger(i.id(), i.status(), i.reasons()))
                .toList();

        return new CountermeasurePlan(
                Contract.SCHEMA_VERSION,
                planId,
                assessment.astronautId(),
                assessment.missionDay(),
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString(),
                assessment.environment().id(),
                triggers,
                draft,
                resourceBlock,
                new CountermeasurePlan.Llm(model, sha256(PlannerPrompt.SYSTEM + "\n" + input)),
                Contract.DISCLAIMER);
    }

    // Null means "use the offline planner": the call failed or the answer was rejected twice.
    private JsonNode askLlm(AssessmentResponse assessment, String input, List<ExerciseMenu.Item> allowed,
                            double gravity) {
        List<String> allowedIds = allowed.stream().map(ExerciseMenu.Item::exerciseId).toList();
        String prompt = input;
        for (int attempt = 1; attempt <= LLM_ATTEMPTS; attempt++) {
            try {
                JsonNode draft = policy.clamp(llm.generateDraft(PlannerPrompt.SYSTEM, prompt, allowedIds), assessment);
                List<String> problems = policy.check(draft, assessment, gravity);
                if (problems.isEmpty()) {
                    return draft;
                }
                log.warn("Plan draft from {} rejected (attempt {}): {}", llm.model(), attempt, problems);
                prompt = input + PlannerPrompt.retryNote(problems);
            } catch (RuntimeException e) {
                log.warn("Call to {} failed, using the offline planner: {}", llm.model(), e.getMessage());
                return null;
            }
        }
        return null;
    }

    private JsonNode offlineDraft(AssessmentResponse assessment, List<ExerciseMenu.Item> allowed, double gravity) {
        JsonNode draft = policy.clamp(mapper.valueToTree(fallback.draft(assessment, allowed)), assessment);
        List<String> problems = policy.check(draft, assessment, gravity);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Offline plan failed validation: " + problems);
        }
        return draft;
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

    private CountermeasurePlan readFallbackFile(String astronautId) {
        ClassPathResource file = new ClassPathResource("fallback-plans/" + astronautId + ".json");
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            return mapper.readValue(in, CountermeasurePlan.class);
        } catch (IOException e) {
            log.warn("Cannot read fallback plan for {}: {}", astronautId, e.getMessage());
            return null;
        }
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
