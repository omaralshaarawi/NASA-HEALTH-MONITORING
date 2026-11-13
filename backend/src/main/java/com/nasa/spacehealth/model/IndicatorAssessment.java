package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

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
        @JsonInclude(JsonInclude.Include.NON_NULL) Double ratio,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer consecutiveLowDays) {

    public record Anchor(double mean, double sdRaw, double sdEff, int n) {
    }
}
