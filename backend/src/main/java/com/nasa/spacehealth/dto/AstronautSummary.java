package com.nasa.spacehealth.dto;

import com.nasa.spacehealth.model.Status;

public record AstronautSummary(
        String astronautId,
        String displayName,
        String scenario,
        int latestMissionDay,
        String environmentId,
        Status overall,
        boolean triageRecommended) {
}
