package com.nasa.spacehealth.dto;

// Only patientId is required. Defaults: the patient, the latest day and its habitat,
// and that habitat's default distance.
public record StartTriageRequest(
        String patientId,
        String reporterId,
        String environmentId,
        Double distanceKm,
        Integer missionDay) {
}
