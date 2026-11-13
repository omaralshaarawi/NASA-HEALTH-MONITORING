package com.nasa.spacehealth.planner;

import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.CommMode;
import com.nasa.spacehealth.model.IndicatorAssessment;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft.Nutrition;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft.Session;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Rule-based plan with fixed wording, used when the LLM is not configured, not reachable,
// or its answer is rejected twice. No network, same input gives the same plan.
@Component
public class FallbackPlanner {

    private static final String SPINE = "SPINE_BONE";
    private static final String CARDIO = "CHEST_CARDIO";
    private static final String HEAD = "HEAD_NEURO";
    private static final String SYSTEMIC = "SYSTEMIC_IMMUNE_METABOLIC";

    private static final Map<String, List<String>> INSTRUCTIONS = Map.ofEntries(
            Map.entry("resistive_moderate", List.of(
                    "Set up the resistive device or bands and secure yourself.",
                    "Warm up with light loads first.",
                    "Work through squats, deadlifts and heel raises at a moderate, steady effort.",
                    "Stop if you feel pain or dizziness, then log the session.")),
            Map.entry("resistive_vigorous", List.of(
                    "Set up the resistive device and secure yourself.",
                    "Warm up with light loads first.",
                    "Work through squats, deadlifts and heel raises at a hard but controlled effort.",
                    "Stop if you feel pain or dizziness, then log the session.")),
            Map.entry("cycle_ergometer", List.of(
                    "Strap in to the cycle ergometer.",
                    "Start easy, then hold a moderate pace at which you can still talk.",
                    "Cool down at low resistance and log the session.")),
            Map.entry("treadmill_harness", List.of(
                    "Put on the harness and check the bungees.",
                    "Start with a walk, then hold a brisk and comfortable pace.",
                    "Cool down with a slow walk and log the session.")),
            Map.entry("zero_t2_ergometer", List.of(
                    "Set up the compact exercise device and secure yourself.",
                    "Warm up at low load.",
                    "Alternate the cardio and resistive parts at a moderate, steady effort.",
                    "Stop if you feel pain or dizziness, then log the session.")),
            Map.entry("walk_surface", List.of(
                    "Check your suit or indoor walking area with a crewmate.",
                    "Walk at a steady, comfortable pace.",
                    "Log the session when you are back.")),
            Map.entry("stretch_mobility", List.of(
                    "Anchor your feet.",
                    "Stretch back, hips and shoulders slowly, without bouncing.",
                    "Breathe steadily and hold each stretch for a few breaths.")),
            Map.entry("circadian_led_journaling", List.of(
                    "Set the cabin lights to the daytime setting after waking and dim them in the evening.",
                    "Write a short journal entry about today, including anything that frustrated you.",
                    "Keep screens off and the sleep station dark before your sleep period.")),
            Map.entry("microbial_swab_air_check", List.of(
                    "Swab the marked habitat surfaces with the sampling kit.",
                    "Run the air quality check and note the cabin readings.",
                    "Store the samples and log the results.")));

    public CountermeasurePlanDraft draft(AssessmentResponse a, List<ExerciseMenu.Item> allowed) {
        return DraftPolicy.isEmergency(a) ? emergencyDraft(a) : routineDraft(a, allowed);
    }

    // RED or triage recommended: no training today, the triage checklist comes first.
    private CountermeasurePlanDraft emergencyDraft(AssessmentResponse a) {
        List<String> red = a.indicators().stream()
                .filter(i -> i.status() == Status.RED)
                .map(IndicatorAssessment::label)
                .toList();
        String redList = red.isEmpty() ? "today's readings" : String.join(", ", red);
        String contact = a.comms().mode() == CommMode.AUTONOMOUS
                ? "Earth is too far away for a live call, so the triage report is queued for the flight surgeon."
                : "Call the flight surgeon now.";

        String explanation = "These readings are in the red range today: " + redList + ". All exercise is on hold. "
                + "Follow the emergency triage checklist now and keep the crew member at rest with a crewmate nearby. "
                + contact + " Training restarts only after the readings are re-measured and the status is cleared.";
        Nutrition nutrition = new Nutrition(0, List.of("hydration"),
                "Keep to the normal ration unless the medical checklist says otherwise.");
        return new CountermeasurePlanDraft(List.of(), nutrition, true,
                "Overall status is RED (" + redList + "). Follow the emergency triage checklist and report to the "
                        + "flight surgeon.",
                explanation);
    }

    private CountermeasurePlanDraft routineDraft(AssessmentResponse a, List<ExerciseMenu.Item> allowed) {
        boolean boneLow = notGreen(a, SPINE);
        boolean cardioOff = notGreen(a, CARDIO);
        boolean headOff = notGreen(a, HEAD);
        boolean systemicOff = notGreen(a, SYSTEMIC);
        boolean lowRation = a.flags().contains("INADEQUATE_NUTRITION");

        List<Session> sessions = new ArrayList<>();

        // bone and muscle loading every day; the compact device in a transit spacecraft
        String boneFirst = "DEEP_SPACECRAFT".equals(a.environment().id()) ? "zero_t2_ergometer" : "resistive_moderate";
        ExerciseMenu.Item bone = pick(allowed, boneFirst, "resistive_moderate", "stretch_mobility");
        if (bone != null) {
            sessions.add(session(a, bone, boneLow ? 45 : 30,
                    "Routine loading to protect bone and muscle."));
        }
        // no hard cardio while the heart readings are off their baseline
        if (!cardioOff) {
            ExerciseMenu.Item cardio = pick(allowed, "walk_surface", "cycle_ergometer", "treadmill_harness");
            if (cardio != null) {
                sessions.add(session(a, cardio, 30, "Routine cardio to keep the heart and blood vessels conditioned."));
            }
        }
        if (headOff) {
            ExerciseMenu.Item item = pick(allowed, "circadian_led_journaling");
            if (item != null) {
                sessions.add(session(a, item, 20, "Supports sleep and mood."));
            }
        }
        if (systemicOff && a.flags().contains("SYSTEMIC_IMMUNE")) {
            ExerciseMenu.Item item = pick(allowed, "microbial_swab_air_check");
            if (item != null) {
                sessions.add(session(a, item, 15, "Checks the habitat while the immune system is under load."));
            }
        }
        if (!headOff && !systemicOff && bone != null && !bone.exerciseId().equals("stretch_mobility")) {
            ExerciseMenu.Item stretch = pick(allowed, "stretch_mobility");
            if (stretch != null) {
                sessions.add(session(a, stretch, 10, "Routine mobility for the back and hips."));
            }
        }

        List<String> focus = new ArrayList<>();
        if (lowRation) {
            focus.add("full daily ration");
        }
        focus.add("protein");
        focus.add("hydration");
        if (boneLow) {
            focus.add("calcium and vitamin D sources");
        }
        Nutrition nutrition = new Nutrition(lowRation ? 200 : 0, focus, lowRation
                ? "Ration intake has been below target. Finish every planned meal today and log it."
                : "Keep to the planned ration.");

        List<String> regionsOff = a.regions().values().stream()
                .filter(r -> r.status() != Status.GREEN)
                .map(r -> r.id())
                .toList();
        boolean escalate = regionsOff.size() >= 3;
        String reason = escalate
                ? "Several body systems are outside the personal baseline at the same time: "
                + String.join(", ", regionsOff) + "."
                : "";

        return new CountermeasurePlanDraft(sessions, nutrition, escalate, reason,
                routineExplanation(a, cardioOff, lowRation, escalate));
    }

    private String routineExplanation(AssessmentResponse a, boolean cardioOff, boolean lowRation, boolean escalate) {
        List<String> off = a.indicators().stream()
                .filter(i -> i.status() != Status.GREEN)
                .map(IndicatorAssessment::label)
                .toList();
        if (off.isEmpty()) {
            return "All monitored systems are inside your personal baseline today. This is a maintenance day: "
                    + "resistive loading to protect bone and muscle, cardio, and a short stretch. "
                    + "Keep to the planned ration, stay hydrated and log tomorrow morning's vitals as usual.";
        }
        StringBuilder text = new StringBuilder("Some readings are outside your personal baseline today: ")
                .append(String.join(", ", off))
                .append(". The plan targets those areas and keeps the workload moderate.");
        if (cardioOff) {
            text.append(" Hard cardio is left out until the heart readings are back to normal.");
        }
        if (lowRation) {
            text.append(" Ration intake has been low, so finish every planned meal.");
        }
        if (escalate) {
            text.append(" Several systems are affected at once, so the flight surgeon should review this plan at the next contact.");
        }
        text.append(" Re-measure tomorrow morning.");
        return text.toString();
    }

    private Session session(AssessmentResponse a, ExerciseMenu.Item item, int minutes, String routineReason) {
        // quote what triggered it, straight from the assessment
        List<String> triggers = a.indicators().stream()
                .filter(i -> i.status() != Status.GREEN && item.targetSystem().equals(i.region())
                        && !i.reasons().isEmpty())
                .limit(2)
                .map(i -> i.label() + ": " + i.reasons().get(0))
                .toList();
        String rationale = triggers.isEmpty() ? routineReason : routineReason + " Triggered by " + String.join("; ", triggers) + ".";
        return new Session(item.exerciseId(), minutes, item.targetSystem(), INSTRUCTIONS.get(item.exerciseId()), rationale);
    }

    private static boolean notGreen(AssessmentResponse a, String region) {
        return a.regions().containsKey(region) && a.regions().get(region).status() != Status.GREEN;
    }

    // first of the wanted ids that is allowed in this habitat (and that we have wording for)
    private static ExerciseMenu.Item pick(List<ExerciseMenu.Item> allowed, String... wanted) {
        for (String id : wanted) {
            for (ExerciseMenu.Item item : allowed) {
                if (item.exerciseId().equals(id) && INSTRUCTIONS.containsKey(id)) {
                    return item;
                }
            }
        }
        return null;
    }
}
