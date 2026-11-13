package com.nasa.spacehealth.dto;

// answer: "yes"/"no" on ASK_YES_NO steps, "done" otherwise.
// stepId is optional; if sent it has to match the current step (guards against double clicks).
public record TriageStepRequest(String incidentId, String answer, String stepId) {
}
