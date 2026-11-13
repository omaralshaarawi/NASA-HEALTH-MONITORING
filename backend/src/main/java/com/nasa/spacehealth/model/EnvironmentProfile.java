package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EnvironmentProfile(
        String id,
        String label,
        String environment,
        String body,
        double gravityG,
        long distanceKm,
        double oneWayDelayS,
        String commLabel,
        CommMode commMode,
        @JsonProperty("radiation_expected_mSv_day") double radiationExpectedMsvDay,
        @JsonProperty("radiation_mSv_day") Double radiationMsvDay,
        @JsonProperty("cabin_co2_mmHg") Double cabinCo2MmHg,
        int resistiveTargetMin,
        // Mars only
        @JsonInclude(JsonInclude.Include.NON_NULL) DistanceRange distanceRangeKm) {

    public record DistanceRange(long min, long max) {
    }
}
