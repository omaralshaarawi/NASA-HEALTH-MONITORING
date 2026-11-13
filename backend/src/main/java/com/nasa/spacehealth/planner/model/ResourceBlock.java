package com.nasa.spacehealth.planner.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ResourceBlock(Assumptions assumptions, List<SessionImpact> sessions, Totals totals, String label) {

    public static final String FORMULA = "extra_kcal = (MET - rest_met) * mass_kg * hours";
    public static final String LABEL = "decision-support estimate";

    public record Assumptions(
            double massKg,
            double restMet,
            double waterMlPerKcal,
            @JsonProperty("co2_g_per_kcal") double co2GPerKcal,
            String formula) {
    }

    public record SessionImpact(
            String exerciseId,
            int minutes,
            double met,
            long grossKcal,
            long extraKcal,
            long extraWaterMl,
            @JsonProperty("extra_co2_g") long extraCo2G) {
    }

    public record Totals(long extraKcal, long extraWaterMl, @JsonProperty("extra_co2_g") long extraCo2G) {
    }
}
