package com.nasa.spacehealth.dto;

// Only patientId is required. The rest default to: the patient, the habitat of the latest day,
// the habitat's default distance and the latest mission day.
public record StartTriageRequest(
        String patientId,
        String reporterId,
        String environmentId,
        Double distanceKm,
        Integer missionDay) {
}
