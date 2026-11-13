package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// Java version of validate_draft() in contract/resources.py. Works on raw JSON because the
// draft comes from an LLM and may have missing fields or wrong types.
// Returns the list of problems (empty = OK), with the same wording as the Python version.
@Component
public class DraftValidator {

    public static final int KCAL_MIN = -300;
    public static final int KCAL_MAX = 600;
    public static final int MAX_EXPLANATION_WORDS = 120;

    static final Set<String> REGIONS = Set.of("HEAD_NEURO", "CHEST_CARDIO", "SPINE_BONE", "SYSTEMIC_IMMUNE_METABOLIC");

    private static final Pattern DOSE = Pattern.compile(
            "\\b\\d+(\\.\\d+)?\\s?(mg|mcg|ug|iu)\\b|\\b(tablet|pill|capsule|inject\\w*)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private final ExerciseMenu menu;

    public DraftValidator(ExerciseMenu menu) {
        this.menu = menu;
    }

    public List<String> validate(JsonNode draft, double gravityG) {
        List<String> problems = new ArrayList<>();
        boolean needsDirection = false;

        int i = 0;
        for (JsonNode s : draft.path("sessions")) {
            String at = "sessions[" + i++ + "]: ";
            String exerciseId = s.path("exercise_id").asText(null);
            ExerciseMenu.Item item = exerciseId == null ? null : menu.find(exerciseId);
            if (item == null) {
                problems.add(at + "unknown exercise_id " + repr(s.get("exercise_id")));
                continue;
            }
            JsonNode minutes = s.get("minutes");
            if (!isWholeNumber(minutes) || minutes.asLong() < menu.minMinutes()
                    || minutes.asLong() > menu.maxMinutesPerSession()) {
                problems.add(at + "minutes " + repr(minutes) + " outside " + menu.minMinutes() + "-"
                        + menu.maxMinutesPerSession());
            }
            if (item.gravityMaxG() != null && gravityG > item.gravityMaxG()) {
                problems.add(at + exerciseId + " not allowed above " + item.gravityMaxG() + " g");
            }
            if (item.gravityMinG() != null && gravityG < item.gravityMinG()) {
                problems.add(at + exerciseId + " needs at least " + item.gravityMinG() + " g");
            }
            JsonNode target = s.get("target_system");
            if (target == null || !target.isTextual() || !REGIONS.contains(target.asText())) {
                problems.add(at + "bad target_system " + repr(target));
            }
            needsDirection |= item.requiresFlightSurgeonDirection();

            List<String> texts = texts(s.path("instructions"));
            texts.add(s.path("rationale").asText(""));
            for (String t : texts) {
                if (hasDosing(t)) {
                    problems.add(at + "dosing language in " + repr(head(t)));
                }
            }
        }

        if (needsDirection && !draft.path("escalate_to_flight_surgeon").asBoolean(false)) {
            problems.add("a PROTOCOL needing flight-surgeon direction was chosen but escalate_to_flight_surgeon is false");
        }

        JsonNode nutrition = draft.path("nutrition");
        JsonNode kcal = nutrition.get("kcal_adjustment");
        if (!isWholeNumber(kcal) || kcal.asLong() < KCAL_MIN || kcal.asLong() > KCAL_MAX) {
            problems.add("nutrition.kcal_adjustment " + repr(kcal) + " outside " + KCAL_MIN + ".." + KCAL_MAX);
        }

        String explanation = draft.path("explanation").asText("");
        List<String> texts = texts(nutrition.path("focus"));
        texts.add(nutrition.path("notes").asText(""));
        texts.add(explanation);
        for (String t : texts) {
            if (hasDosing(t)) {
                problems.add("dosing language in " + repr(head(t)));
            }
        }
        if (wordCount(explanation) > MAX_EXPLANATION_WORDS) {
            problems.add("explanation longer than " + MAX_EXPLANATION_WORDS + " words");
        }
        return problems;
    }

    public static boolean hasDosing(String text) {
        return DOSE.matcher(text).find();
    }

    static int wordCount(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private static boolean isWholeNumber(JsonNode n) {
        return n != null && n.isIntegralNumber();
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (n.isTextual()) {
                out.add(n.asText());
            }
        }
        return out;
    }

    private static String head(String t) {
        return t.codePointCount(0, t.length()) <= 50 ? t : t.substring(0, t.offsetByCodePoints(0, 50));
    }

    private static String repr(String s) {
        char quote = s.contains("'") && !s.contains("\"") ? '"' : '\'';
        String escaped = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                .replace(String.valueOf(quote), "\\" + quote);
        return quote + escaped + quote;
    }

    // how Python would print the value (None, True, 'text', 30.5), so messages match resources.py
    private static String repr(JsonNode n) {
        if (n == null || n.isNull()) {
            return "None";
        }
        if (n.isTextual()) {
            return repr(n.asText());
        }
        if (n.isBoolean()) {
            return n.asBoolean() ? "True" : "False";
        }
        if (n.isIntegralNumber()) {
            return n.asText();
        }
        if (n.isNumber()) {
            return Double.toString(n.asDouble());
        }
        return n.toString();
    }
}
