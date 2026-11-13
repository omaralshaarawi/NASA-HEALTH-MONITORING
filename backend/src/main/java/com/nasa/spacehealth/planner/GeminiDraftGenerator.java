package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

// Calls Gemini generateContent with a response schema, so the answer is always JSON in the draft shape.
// The schema is the contract's CountermeasurePlanDraft, tightened: exercise_id is an enum of the
// items allowed at this gravity, and minutes carries the menu limits.
@Component
public class GeminiDraftGenerator implements DraftGenerator {

    private static final List<String> REGIONS =
            List.of("HEAD_NEURO", "CHEST_CARDIO", "SPINE_BONE", "SYSTEMIC_IMMUNE_METABOLIC");

    private final RestClient client;
    private final ObjectMapper mapper;
    private final ExerciseMenu menu;
    private final String apiKey;
    private final String model;
    private final int thinkingBudget;

    public GeminiDraftGenerator(@Qualifier("geminiRestClient") RestClient client,
                                ObjectMapper mapper,
                                ExerciseMenu menu,
                                @Value("${planner.gemini.api-key:}") String apiKey,
                                @Value("${planner.gemini.model:gemini-3.6-flash}") String model,
                                @Value("${planner.gemini.thinking-budget:0}") int thinkingBudget) {
        this.client = client;
        this.mapper = mapper;
        this.menu = menu;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.thinkingBudget = thinkingBudget;
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public JsonNode generateDraft(String systemPrompt, String userPrompt, List<String> allowedExerciseIds) {
        JsonNode response = client.post()
                .uri("/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(systemPrompt, userPrompt, allowedExerciseIds))
                .retrieve()
                .body(JsonNode.class);
        return parseDraft(response);
    }

    ObjectNode requestBody(String systemPrompt, String userPrompt, List<String> allowedExerciseIds) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", userPrompt);

        ObjectNode config = body.putObject("generationConfig");
        config.put("temperature", 0.2);
        // a full draft is well under 1000 tokens; the cap stops a runaway answer from hitting the timeout
        config.put("maxOutputTokens", 2048);
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", responseSchema(allowedExerciseIds));
        if (thinkingBudget >= 0) {
            config.putObject("thinkingConfig").put("thinkingBudget", thinkingBudget);
        }
        return body;
    }

    private ObjectNode responseSchema(List<String> allowedExerciseIds) {
        ObjectNode session = object("exercise_id", "minutes", "target_system", "instructions", "rationale");
        ObjectNode sessionProps = (ObjectNode) session.get("properties");
        sessionProps.set("exercise_id", stringEnum(allowedExerciseIds));
        ObjectNode minutes = sessionProps.putObject("minutes").put("type", "INTEGER");
        minutes.put("minimum", menu.minMinutes());
        minutes.put("maximum", menu.maxMinutesPerSession());
        sessionProps.set("target_system", stringEnum(REGIONS));
        ObjectNode instructions = arrayOf(type("STRING"));
        instructions.put("minItems", 1);
        instructions.put("maxItems", 4);
        sessionProps.set("instructions", instructions);
        sessionProps.set("rationale", type("STRING"));

        ObjectNode nutrition = object("kcal_adjustment", "focus", "notes");
        ObjectNode nutritionProps = (ObjectNode) nutrition.get("properties");
        ObjectNode kcal = nutritionProps.putObject("kcal_adjustment").put("type", "INTEGER");
        kcal.put("minimum", DraftValidator.KCAL_MIN);
        kcal.put("maximum", DraftValidator.KCAL_MAX);
        nutritionProps.set("focus", arrayOf(type("STRING")));
        nutritionProps.set("notes", type("STRING"));

        ObjectNode draft = object("sessions", "nutrition", "escalate_to_flight_surgeon", "escalation_reason",
                "explanation");
        ObjectNode props = (ObjectNode) draft.get("properties");
        ObjectNode sessions = arrayOf(session);
        sessions.put("maxItems", 4);
        props.set("sessions", sessions);
        props.set("nutrition", nutrition);
        props.set("escalate_to_flight_surgeon", type("BOOLEAN"));
        props.set("escalation_reason", type("STRING"));
        props.set("explanation", type("STRING"));
        return draft;
    }

    // an OBJECT with all listed fields required, in this order
    private ObjectNode object(String... fields) {
        ObjectNode o = type("OBJECT");
        o.putObject("properties");
        ArrayNode required = o.putArray("required");
        ArrayNode ordering = o.putArray("propertyOrdering");
        for (String f : fields) {
            required.add(f);
            ordering.add(f);
        }
        return o;
    }

    private ObjectNode type(String name) {
        return mapper.createObjectNode().put("type", name);
    }

    private ObjectNode arrayOf(ObjectNode items) {
        ObjectNode a = type("ARRAY");
        a.set("items", items);
        return a;
    }

    private ObjectNode stringEnum(List<String> values) {
        ObjectNode s = type("STRING");
        ArrayNode e = s.putArray("enum");
        values.forEach(e::add);
        return s;
    }

    JsonNode parseDraft(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Gemini returned an empty response");
        }
        String blocked = response.path("promptFeedback").path("blockReason").asText("");
        if (!blocked.isEmpty()) {
            throw new IllegalStateException("Gemini blocked the prompt: " + blocked);
        }
        JsonNode candidate = response.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("Gemini returned no text (finishReason " + finish + ")");
        }
        try {
            return mapper.readTree(text.toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini answer is not valid JSON (finishReason " + finish + ")");
        }
    }
}
