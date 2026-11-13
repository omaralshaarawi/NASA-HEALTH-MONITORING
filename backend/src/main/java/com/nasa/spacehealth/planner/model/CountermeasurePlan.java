package com.nasa.spacehealth.planner.model;

import com.nasa.spacehealth.model.Status;

import java.util.List;

public record CountermeasurePlan(
        String schemaVersion,
        String planId,
        String astronautId,
        int generatedForMissionDay,
        String generatedAtIso,
        String environmentId,
        List<Trigger> triggers,
        CountermeasurePlanDraft draft,
        ResourceBlock resources,
        Llm llm,
        String disclaimer) {

    // copied from the assessment, not written by the LLM
    public record Trigger(String indicator, Status status, List<String> reasons) {
    }

    public record Llm(String model, String inputHash) {
    }
}
