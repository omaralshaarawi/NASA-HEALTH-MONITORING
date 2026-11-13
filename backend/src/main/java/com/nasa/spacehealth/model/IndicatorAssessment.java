package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** contract.ts IndicatorAssessment. Nulls are written explicitly, except the two optional fields at the end. */
public record IndicatorAssessment(
        String id,
        String label,
        String unit,
        String region,
        Double value,
        Status status,
        List<String> reasons,
        Anchor anchor,
        Double z,
        Double slopePerDay,
        Double driftZ,
        int absLevel,
        boolean remeasure,
        List<Double> trend,
        /** radiation only */
        @JsonInclude(JsonInclude.Include.NON_NULL) Double ratio,
        /** resistive / ration only */
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer consecutiveLowDays) {

    public record Anchor(double mean, double sdRaw, double sdEff, int n) {
    }
}
