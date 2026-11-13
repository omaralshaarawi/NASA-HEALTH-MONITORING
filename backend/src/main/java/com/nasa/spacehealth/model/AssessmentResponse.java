package com.nasa.spacehealth.model;

import java.util.List;
import java.util.Map;

public record AssessmentResponse(
        String schemaVersion,
        String astronautId,
        int missionDay,
        String generatedAtIso,
        EnvironmentProfile environment,
        Status overall,
        List<IndicatorAssessment> indicators,
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
