package com.nasa.spacehealth.planner.model;

import java.util.List;

// The part of the plan the LLM writes. Minutes and kcal_adjustment are the only numbers in it,
// and both are clamped and checked by the server.
public record CountermeasurePlanDraft(
        List<Session> sessions,
        Nutrition nutrition,
        boolean escalateToFlightSurgeon,
        String escalationReason,
        String explanation) {

    public record Session(
            String exerciseId,
            int minutes,
            String targetSystem,
            List<String> instructions,
            String rationale) {
    }

    public record Nutrition(int kcalAdjustment, List<String> focus, String notes) {
    }
}
