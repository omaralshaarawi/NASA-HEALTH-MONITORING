package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** contract.ts section 1. Loaded from rules.json "environments", then enriched with computed and live values. */
public record EnvironmentProfile(
        String id,
        String label,
        String environment,
        String body,
        double gravityG,
        long distanceKm,
        /** COMPUTED: distance_km / 299792.458. */
        double oneWayDelayS,
        String commLabel,
        CommMode commMode,
        @JsonProperty("radiation_expected_mSv_day") double radiationExpectedMsvDay,
        @JsonProperty("radiation_mSv_day") Double radiationMsvDay,
        @JsonProperty("cabin_co2_mmHg") Double cabinCo2MmHg,
        int resistiveTargetMin,
        /** Present only when the distance is variable (Mars). */
        @JsonInclude(JsonInclude.Include.NON_NULL) DistanceRange distanceRangeKm) {

    public record DistanceRange(long min, long max) {
    }
}
