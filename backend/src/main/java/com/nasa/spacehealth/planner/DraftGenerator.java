package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

// interface so tests can stub the LLM
public interface DraftGenerator {

    boolean isConfigured();

    String model();

    // raw draft JSON; throws if the call fails or the answer is not JSON
    JsonNode generateDraft(String systemPrompt, String userPrompt, List<String> allowedExerciseIds);
}
