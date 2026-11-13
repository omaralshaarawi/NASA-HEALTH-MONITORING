package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.IndicatorAssessment;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.planner.model.ExerciseMenu;

import java.util.List;

// What the LLM is told. It only picks from the menu and writes words; every number it sees
// comes from the assessment, and every number in the final plan is computed or checked by code.
final class PlannerPrompt {

    private PlannerPrompt() {
    }

    static final String SYSTEM = """
            You are the countermeasure planner of a deep-space crew health decision-support prototype. All data is simulated.

            You receive one JSON object: today's health assessment of one astronaut and the list of countermeasures \
            that are allowed in the current habitat. Write a plan for the coming day as JSON that matches the response schema.

            Rules
            1. Choose exercise_id only from allowed_countermeasures. Use each one at most once. Plan at most four sessions.
            2. minutes is a whole number inside limits. target_system is the body region the session is meant to help.
            3. instructions: one to four short steps in plain English. rationale: one sentence that names the triggering \
            indicators in plain words.
            4. Never invent numbers. A number may appear in your text only if it is in the input, copied exactly. \
            Write small counts as words. Do not state calories burned, water, CO2, heart-rate targets or any other figure: \
            the server computes resource use itself.
            5. No diagnosis. Never name a medication or give a dose, a supplement amount, a device setting or a fluid amount. \
            An item with requires_flight_surgeon_direction = true needs escalate_to_flight_surgeon = true, and you say that \
            the flight surgeon sets the details.
            6. If triage_recommended is true or overall is RED: plan no EXERCISE sessions, set escalate_to_flight_surgeon \
            to true and tell the crew to follow the emergency triage checklist first.
            7. If a region is YELLOW, keep the load on that region moderate and say what to re-measure. \
            If three or more regions are not GREEN, set escalate_to_flight_surgeon to true.
            8. nutrition.kcal_adjustment is a whole number from -300 to 600, and 0 when no change is needed. \
            focus lists food groups or habits such as "protein" or "hydration", never products or amounts.
            9. escalation_reason is an empty string when escalate_to_flight_surgeon is false.
            10. explanation: at most 120 words, plain English, written to the crew member.

            NASA Human Research Program grounding. Use these names where they fit and do not add facts about them:
            - HEAD_NEURO (sleep, mood, reaction time): Standard Measures and ARCHeR follow sleep and actigraphy. \
            Circadian LED lighting and crew journaling (Isolation and Confinement hazard) is circadian_led_journaling.
            - HEAD_NEURO (vision, SANS): the veno-constrictive thigh cuff and B-complex protocol is thigh_cuff_bcomplex, \
            only under flight-surgeon direction.
            - CHEST_CARDIO: cardiovascular exercise. Lower-body compression and fluid loading before landing or surface \
            work is compression_fluid_loading, only under flight-surgeon direction.
            - SPINE_BONE: ARED resistive loading counters the bone loss of microgravity. In DEEP_SPACECRAFT prefer the \
            Zero T2 treadmill-free compact workout, zero_t2_ergometer.
            - SYSTEMIC_IMMUNE_METABOLIC: Hostile/Closed Environments hazard, "close the loop". The habitat microbial swab \
            and air check is microbial_swab_air_check. Support nutrition and hydration.
            """;

    static ObjectNode input(ObjectMapper mapper, AssessmentResponse a, ExerciseMenu menu, List<ExerciseMenu.Item> allowed) {
        ObjectNode in = mapper.createObjectNode();
        in.put("astronaut_id", a.astronautId());
        in.put("mission_day", a.missionDay());

        ObjectNode env = in.putObject("environment");
        env.put("id", a.environment().id());
        env.put("label", a.environment().label());
        env.put("gravity_g", a.environment().gravityG());
        env.put("comm_mode", a.comms().mode().name());

        in.put("overall", a.overall().name());
        in.put("triage_recommended", a.triageRecommended());
        in.set("flags", mapper.valueToTree(a.flags()));
        in.set("compliance", mapper.valueToTree(a.compliance()));

        ObjectNode regions = in.putObject("regions");
        a.regions().forEach((id, r) -> regions.put(id, r.status().name()));

        ArrayNode triggers = in.putArray("triggers");
        for (IndicatorAssessment i : a.indicators()) {
            if (i.status() == Status.GREEN) {
                continue;
            }
            ObjectNode t = triggers.addObject();
            t.put("indicator", i.id());
            t.put("label", i.label());
            t.put("region", i.region());
            t.put("value", i.value());
            t.put("unit", i.unit());
            t.put("status", i.status().name());
            t.set("reasons", mapper.valueToTree(i.reasons()));
        }

        ArrayNode items = in.putArray("allowed_countermeasures");
        for (ExerciseMenu.Item item : allowed) {
            ObjectNode o = items.addObject();
            o.put("exercise_id", item.exerciseId());
            o.put("label", item.label());
            o.put("kind", item.kind());
            o.put("target_system", item.targetSystem());
            o.put("requires_flight_surgeon_direction", item.requiresFlightSurgeonDirection());
            o.put("nasa_basis", item.nasaBasis());
        }

        ObjectNode limits = in.putObject("limits");
        limits.put("min_minutes", menu.minMinutes());
        limits.put("max_minutes_per_session", menu.maxMinutesPerSession());
        limits.put("kcal_adjustment_min", DraftValidator.KCAL_MIN);
        limits.put("kcal_adjustment_max", DraftValidator.KCAL_MAX);
        return in;
    }

    static String retryNote(List<String> problems) {
        return "\n\nYour previous answer was rejected by the server for these reasons:\n- "
                + String.join("\n- ", problems)
                + "\nAnswer again with a corrected plan.";
    }
}
