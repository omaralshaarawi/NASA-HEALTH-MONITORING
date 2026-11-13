package com.nasa.spacehealth.model;

import java.util.List;
import java.util.Map;

/** contract.ts section 3 (OUTPUT). */
public record AssessmentResponse(
        String schemaVersion,
        String astronautId,
        int missionDay,
        String generatedAtIso,
        EnvironmentProfile environment,
        Status overall,
        List<IndicatorAssessment> indicators,
        /** Keyed by RegionId, in rules.json order. */
        Map<String, RegionAssessment> regions,
        ComplianceSummary compliance,
        List<String> flags,
        Comms comms,
        boolean triageRecommended,
        String thresholdsStatus,
        String disclaimer) {

    public record ComplianceSummary(int streakDays, int boneLowDays, int nutritionLowDays) {
    }

    public record Comms(double oneWayDelayS, CommMode mode, String emergencyAction) {
    }
}
