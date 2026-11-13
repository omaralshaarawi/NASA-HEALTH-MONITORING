package com.nasa.spacehealth.model;

/** Request bodies of the /api/triage routes (snake_case on the wire, like everything else). */
public final class TriageRequests {

    private TriageRequests() {
    }

    /**
     * Only patient_id is required. reporter_id defaults to the patient, environment_id to the habitat the patient's
     * latest day was recorded in, mission_day to the latest day, distance_km to the habitat default.
     */
    public record Start(String patientId, String reporterId, String environmentId, Double distanceKm,
                        Integer missionDay) {
    }

    /**
     * answer is "yes" / "no" for ASK_YES_NO steps and "done" for everything else. step_id is optional: when sent it
     * must equal the incident's current step, which protects against double clicks and a voice/screen race.
     */
    public record Step(String incidentId, String answer, String stepId) {
    }

    /** Manual exit, allowed at any step (for example to leave the CPR loop). reason is optional free text. */
    public record End(String incidentId, String reason) {
    }
}
