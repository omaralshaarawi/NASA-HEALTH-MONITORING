package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// No network: the Gemini endpoint is replaced by a mock server.
class GeminiDraftGeneratorTest {

    private static final String URL = "https://gemini.test/v1beta/models/gemini-2.5-flash:generateContent";
    private static final List<String> ALLOWED = List.of("zero_t2_ergometer", "stretch_mobility");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExerciseMenu menu = new ExerciseMenu(5, 90, List.of());
    private MockRestServiceServer server;
    private GeminiDraftGenerator gemini;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gemini.test/v1beta");
        server = MockRestServiceServer.bindTo(builder).build();
        gemini = new GeminiDraftGenerator(builder.build(), mapper, menu, "test-key", "gemini-2.5-flash", 0);
    }

    private String answer(String text) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode candidate = response.putArray("candidates").addObject();
        candidate.put("finishReason", "STOP");
        candidate.putObject("content").putArray("parts").addObject().put("text", text);
        return response.toString();
    }

    @Test
    void sendsSchemaAndKeyAndReadsTheDraft() {
        server.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("system text"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("user text"))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingBudget").value(0))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.sessions.items.properties"
                        + ".exercise_id.enum[1]").value("stretch_mobility"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.sessions.items.properties"
                        + ".minutes.maximum").value(90))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.nutrition.properties"
                        + ".kcal_adjustment.maximum").value(600))
                .andRespond(withSuccess(answer("{\"sessions\": [], \"explanation\": \"ok\"}"), MediaType.APPLICATION_JSON));

        JsonNode draft = gemini.generateDraft("system text", "user text", ALLOWED);

        assertThat(draft.get("explanation").asText()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void theKeyNeverGoesInTheUrl() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("{}"), MediaType.APPLICATION_JSON));
        gemini.generateDraft("s", "u", ALLOWED);
        server.verify();   // requestTo(URL) would not match a URL with ?key=
    }

    @Test
    void blockedPromptIsAnError() {
        server.expect(requestTo(URL)).andRespond(
                withSuccess("{\"promptFeedback\": {\"blockReason\": \"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gemini.generateDraft("s", "u", ALLOWED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SAFETY");
    }

    @Test
    void textThatIsNotJsonIsAnError() {
        server.expect(requestTo(URL)).andRespond(withSuccess(answer("Sure! Here is the plan"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gemini.generateDraft("s", "u", ALLOWED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void httpErrorIsAnError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> gemini.generateDraft("s", "u", ALLOWED))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void notConfiguredWithoutAKey() {
        GeminiDraftGenerator noKey = new GeminiDraftGenerator(RestClient.create(), mapper, menu, " ", "m", 0);
        assertThat(noKey.isConfigured()).isFalse();
        assertThat(gemini.isConfigured()).isTrue();
    }
}
