package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// The LLM behind the planner. Kept as an interface so tests (and another provider) can replace it.
public interface DraftGenerator {

    boolean isConfigured();

    String model();

    // Returns the raw draft JSON. Throws if the call fails or the answer is not JSON.
    JsonNode generateDraft(String systemPrompt, String userPrompt, List<String> allowedExerciseIds);
}
