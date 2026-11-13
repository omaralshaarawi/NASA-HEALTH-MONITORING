package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import com.nasa.spacehealth.service.SchemaValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Server-side rules for a draft, on top of the contract's validate_draft():
// clamp the two numbers the LLM is allowed to write, force escalation where it is not optional,
// and reject anything the contract forbids but the Python validator does not look at.
@Component
public class DraftPolicy {

    // this many body regions off their baseline at once is the flight surgeon's call, not the planner's
    public static final int ESCALATE_AT_REGIONS = 3;

    private static final int MAX_INSTRUCTIONS = 4;
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final ExerciseMenu menu;
    private final DraftValidator validator;
    private final SchemaValidator schemaValidator;
    private final ObjectMapper mapper;

    public DraftPolicy(ExerciseMenu menu, DraftValidator validator, SchemaValidator schemaValidator,
                       ObjectMapper mapper) {
        this.menu = menu;
        this.validator = validator;
        this.schemaValidator = schemaValidator;
        this.mapper = mapper;
    }

    static boolean isEmergency(AssessmentResponse a) {
        return a.triageRecommended() || a.overall() == Status.RED;
    }

    static List<String> regionsOffBaseline(AssessmentResponse a) {
        return a.regions().values().stream()
                .filter(r -> r.status() != Status.GREEN)
                .map(r -> r.id())
                .toList();
    }

    // Returns a cleaned copy. Only fixes what is safe to fix; everything else is left for check().
    public JsonNode clamp(JsonNode raw, AssessmentResponse assessment) {
        if (raw == null || !raw.isObject()) {
            return raw;
        }
        ObjectNode draft = raw.deepCopy();
        List<String> directedLabels = new ArrayList<>();

        for (JsonNode s : draft.path("sessions")) {
            if (!s.isObject()) {
                continue;
            }
            ObjectNode session = (ObjectNode) s;
            clampWholeNumber(session, "minutes", menu.minMinutes(), menu.maxMinutesPerSession());
            if (session.path("instructions").isArray()) {
                ArrayNode kept = mapper.createArrayNode();
                for (JsonNode step : session.get("instructions")) {
                    if (kept.size() < MAX_INSTRUCTIONS && step.isTextual() && !step.asText().isBlank()) {
                        kept.add(step.asText().strip());
                    }
                }
                session.set("instructions", kept);
            }
            ExerciseMenu.Item item = menu.find(session.path("exercise_id").asText(""));
            if (item != null && item.requiresFlightSurgeonDirection()) {
                directedLabels.add(item.label());
            }
        }
        if (draft.path("nutrition").isObject()) {
            clampWholeNumber((ObjectNode) draft.get("nutrition"), "kcal_adjustment",
                    DraftValidator.KCAL_MIN, DraftValidator.KCAL_MAX);
        }

        // escalation is not the LLM's call in these cases
        List<String> regionsOff = regionsOffBaseline(assessment);
        String forcedReason = null;
        if (isEmergency(assessment)) {
            forcedReason = "Overall status is RED. Follow the emergency triage checklist and report to the flight surgeon.";
        } else if (!directedLabels.isEmpty()) {
            forcedReason = "Needs flight-surgeon direction: " + String.join("; ", directedLabels) + ".";
        } else if (regionsOff.size() >= ESCALATE_AT_REGIONS) {
            forcedReason = "Several body systems are outside the personal baseline at the same time: "
                    + String.join(", ", regionsOff) + ".";
        }
        if (forcedReason != null) {
            draft.put("escalate_to_flight_surgeon", true);
            if (draft.path("escalation_reason").asText("").isBlank()) {
                draft.put("escalation_reason", forcedReason);
            }
        }
        return draft;
    }

    private static void clampWholeNumber(ObjectNode node, String field, int min, int max) {
        JsonNode value = node.get(field);
        if (value != null && value.isNumber() && !Double.isNaN(value.asDouble())) {
            long rounded = Math.round(value.asDouble());
            node.put(field, (int) Math.max(min, Math.min(max, rounded)));
        }
    }

    // Empty list = the draft can be used.
    public List<String> check(JsonNode draft, AssessmentResponse assessment, double gravityG) {
        if (draft == null || !draft.isObject()) {
            return List.of("the answer is not a JSON object");
        }
        List<String> schemaErrors = schemaValidator.validate("CountermeasurePlanDraft", draft);
        if (!schemaErrors.isEmpty()) {
            return schemaErrors.stream().map(e -> "schema: " + e).toList();
        }

        List<String> problems = new ArrayList<>(validator.validate(draft, gravityG));
        boolean emergency = isEmergency(assessment);
        Set<String> seen = new HashSet<>();
        Set<BigDecimal> allowedNumbers = numbersIn(mapper.valueToTree(assessment));

        int i = 0;
        for (JsonNode s : draft.get("sessions")) {
            String at = "sessions[" + i++ + "]: ";
            String id = s.path("exercise_id").asText("");
            ExerciseMenu.Item item = menu.find(id);
            if (item == null) {
                continue;   // already reported by the validator
            }
            if (!seen.add(id)) {
                problems.add(at + id + " is planned more than once");
            }
            if (emergency && item.isExercise()) {
                problems.add(at + "exercise is on hold while the overall status is RED or triage is recommended");
            }
            if (s.path("instructions").isEmpty()) {
                problems.add(at + "needs at least one instruction");
            }
            if (s.path("rationale").asText("").isBlank()) {
                problems.add(at + "rationale is empty");
            }
            allowedNumbers.addAll(numbersIn(s.get("minutes")));
            allowedNumbers.addAll(numbersIn(mapper.createObjectNode().put("label", item.label())));
        }
        allowedNumbers.addAll(numbersIn(draft.path("nutrition").get("kcal_adjustment")));

        String explanation = draft.path("explanation").asText("");
        if (explanation.isBlank()) {
            problems.add("explanation is empty");
        }
        Matcher m = NUMBER.matcher(explanation);
        while (m.find()) {
            if (!allowedNumbers.contains(new BigDecimal(m.group()))) {
                problems.add("explanation contains the number " + m.group() + ", which is not in the assessment");
            }
        }
        return problems;
    }

    // Every number in a JSON tree, including the ones inside text ("Z=2.9 vs own normal 61.2").
    // TreeSet compares BigDecimals by value, so 61 and 61.0 are the same number.
    private static Set<BigDecimal> numbersIn(JsonNode node) {
        Set<BigDecimal> out = new TreeSet<>();
        collectNumbers(node, out);
        return out;
    }

    private static void collectNumbers(JsonNode node, Set<BigDecimal> out) {
        if (node == null) {
            return;
        }
        if (node.isNumber()) {
            out.add(node.decimalValue().abs());
        } else if (node.isTextual()) {
            Matcher m = NUMBER.matcher(node.asText());
            while (m.find()) {
                out.add(new BigDecimal(m.group()));
            }
        } else {
            node.forEach(child -> collectNumbers(child, out));
        }
    }
}
