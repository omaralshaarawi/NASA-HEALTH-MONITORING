package com.nasa.spacehealth.model;

/** One row of GET /api/astronauts. Not part of contract.ts; status fields are computed by the engine. */
public record AstronautSummary(
        String astronautId,
        String displayName,
        String scenario,
        int latestMissionDay,
        String environmentId,
        Status overall,
        boolean triageRecommended) {
}
