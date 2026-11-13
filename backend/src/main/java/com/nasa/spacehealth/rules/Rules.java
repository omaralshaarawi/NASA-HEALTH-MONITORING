package com.nasa.spacehealth.rules;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

// Typed view of contract/rules.json (only the parts the engine uses).
// Loaded strictly, so a missing key fails start-up. Keys that the snake_case strategy would
// mangle (e.g. red_z_needs_confirmation -> red_zneeds_confirmation) are mapped by hand.
public record Rules(
        String version,
        Constants constants,
        @JsonProperty("global") Global global,
        Radiation radiation,
        ComplianceRules compliance,
        Composites composites,
        Map<String, List<String>> regions,
        List<IndicatorRule> indicators,
        Map<String, EnvironmentPreset> environments) {

    public record Constants(@JsonProperty("c_km_s") double cKmS, double autonomousDelayS) {
    }

    public record Global(
            @JsonProperty("z_yellow") double zYellow,
            @JsonProperty("z_red") double zRed,
            double driftYellow,
            @JsonProperty("drift_min_z") double driftMinZ,
            int anchorDays,
            int driftWindowDays,
            int minPoints,
            @JsonProperty("red_z_needs_confirmation") boolean redZNeedsConfirmation) {
    }

    public record Radiation(double ratioYellow, double ratioRed, String eventLabel) {
    }

    public record ComplianceRules(Resistive resistive, Ration ration) {

        public record Resistive(double lowFractionOfTarget, int lowDaysYellow) {
        }

        public record Ration(int lowPct, int lowDaysYellow, int veryLowPct, int veryLowDaysRed) {
        }
    }

    public record Composites(ImmuneRisk immuneRisk) {

        public record ImmuneRisk(double radiationRatioMin, double mildZ, List<String> members) {
        }
    }

    public record IndicatorRule(String id, String label, String unit, String path, String kind, String bad,
                                Double floorSd, @JsonProperty("abs") Map<String, Double> abs) {
    }

    // distanceKm is either {fixed} (LEO, Moon) or {min, max, default} (Mars)
    public record EnvironmentPreset(
            String label,
            String environment,
            String body,
            double gravityG,
            Map<String, Long> distanceKm,
            String commLabel,
            @JsonProperty("radiation_expected_mSv_day") double radiationExpectedMsvDay,
            @JsonProperty("cabin_co2_default_mmHg") double cabinCo2DefaultMmHg,
            int resistiveTargetMin) {
    }
}
