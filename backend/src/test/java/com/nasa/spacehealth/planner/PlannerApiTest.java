package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.JsonDiff;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import com.nasa.spacehealth.planner.model.ResourceBlock;
import com.nasa.spacehealth.repository.MeasurementRepository;
import com.nasa.spacehealth.rules.Rules;
import com.nasa.spacehealth.service.AssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The planner end to end with the LLM replaced by a stub, so nothing here needs a key or a network.
@SpringBootTest(properties = "planner.gemini.api-key=")
@AutoConfigureMockMvc
class PlannerApiTest {

    static class StubLlm implements DraftGenerator {
        boolean configured;
        final Deque<Object> answers = new ArrayDeque<>();   // JsonNode to return or RuntimeException to throw
        final List<String> prompts = new ArrayList<>();
        List<String> lastAllowedIds;

        public boolean isConfigured() {
            return configured;
        }

        public String model() {
            return "stub-model";
        }

        public JsonNode generateDraft(String systemPrompt, String userPrompt, List<String> allowedExerciseIds) {
            prompts.add(userPrompt);
            lastAllowedIds = allowedExerciseIds;
            Object next = answers.removeFirst();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (JsonNode) next;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        StubLlm stubLlm() {
            return new StubLlm();
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-14T08:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    StubLlm llm;
    @Autowired
    PlannerService planner;
    @Autowired
    AssessmentService assessments;
    @Autowired
    MeasurementRepository store;
    @Autowired
    ResourcesService resources;
    @Autowired
    DraftValidator validator;
    @Autowired
    Rules rules;

    @BeforeEach
    void reset() {
        store.reset();
        planner.forgetGeneratedPlans();
        llm.configured = false;
        llm.answers.clear();
        llm.prompts.clear();
    }

    private JsonNode assessment(String astronautId) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/assessments/" + astronautId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode generate(JsonNode assessment) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/plans/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(assessment.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ObjectNode draft(String exerciseId, int minutes, String region) {
        ObjectNode d = mapper.createObjectNode();
        ObjectNode s = d.putArray("sessions").addObject();
        s.put("exercise_id", exerciseId);
        s.put("minutes", minutes);
        s.put("target_system", region);
        s.putArray("instructions").add("Warm up").add("Work at a steady effort");
        s.put("rationale", "Keeps bone and muscle loaded.");
        ObjectNode n = d.putObject("nutrition");
        n.put("kcal_adjustment", 0);
        n.putArray("focus").add("protein");
        n.put("notes", "Keep to the planned ration.");
        d.put("escalate_to_flight_surgeon", false);
        d.put("escalation_reason", "");
        d.put("explanation", "A steady maintenance day.");
        return d;
    }

    // offline planner

    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin"})
    void offlinePlanIsCompleteAndValid(String astronautId) throws Exception {
        JsonNode a = assessment(astronautId);
        JsonNode plan = generate(a);

        assertThat(plan.get("schema_version").asText()).isEqualTo("1.0.0");
        assertThat(plan.get("plan_id").asText()).startsWith("plan_");
        assertThat(plan.get("astronaut_id").asText()).isEqualTo(astronautId);
        assertThat(plan.get("generated_for_mission_day").asInt()).isEqualTo(14);
        assertThat(plan.get("generated_at_iso").asText()).isEqualTo("2026-11-14T08:00:00Z");
        assertThat(plan.get("environment_id").asText()).isEqualTo("DEEP_SPACECRAFT");
        assertThat(plan.get("llm").get("model").asText()).isEqualTo(PlannerService.OFFLINE_MODEL);
        assertThat(plan.get("llm").get("input_hash").asText()).matches("[0-9a-f]{64}");
        assertThat(plan.get("disclaimer").asText()).isEqualTo(a.get("disclaimer").asText());
        assertThat(validator.validate(plan.get("draft"), 0.0)).isEmpty();

        // triggers are the non-green indicators, copied as they are
        List<String> expectedTriggers = new ArrayList<>();
        for (JsonNode i : a.get("indicators")) {
            if (!i.get("status").asText().equals("GREEN")) {
                expectedTriggers.add(i.get("id").asText() + i.get("status").asText() + i.get("reasons"));
            }
        }
        List<String> triggers = new ArrayList<>();
        plan.get("triggers").forEach(t -> triggers.add(t.get("indicator").asText() + t.get("status").asText()
                + t.get("reasons")));
        assertThat(triggers).isEqualTo(expectedTriggers);

        // resources are computed from the sessions of the draft, for the default 80 kg
        CountermeasurePlan typed = mapper.treeToValue(plan, CountermeasurePlan.class);
        ResourceBlock expected = resources.buildResourceBlock(typed.draft().sessions(), 80);
        assertThat(JsonDiff.diff(mapper.valueToTree(expected), plan.get("resources"))).isEmpty();
    }

    @Test
    void offlinePlansFitTheThreeStories() throws Exception {
        JsonNode nova = generate(assessment("ast_nova")).get("draft");
        assertThat(nova.get("sessions")).hasSize(3);
        assertThat(nova.get("sessions").get(0).get("exercise_id").asText()).isEqualTo("zero_t2_ergometer");
        assertThat(nova.get("escalate_to_flight_surgeon").asBoolean()).isFalse();
        assertThat(nova.get("nutrition").get("kcal_adjustment").asInt()).isZero();

        JsonNode kai = generate(assessment("ast_kai")).get("draft");
        assertThat(kai.get("sessions").findValuesAsText("exercise_id"))
                .containsExactly("zero_t2_ergometer", "circadian_led_journaling", "microbial_swab_air_check");
        assertThat(kai.get("sessions").get(0).get("minutes").asInt()).isEqualTo(45);
        assertThat(kai.get("nutrition").get("kcal_adjustment").asInt()).isEqualTo(200);
        assertThat(kai.get("escalate_to_flight_surgeon").asBoolean()).isTrue();

        JsonNode lin = generate(assessment("ast_lin"));
        assertThat(lin.get("draft").get("sessions")).isEmpty();
        assertThat(lin.get("draft").get("escalate_to_flight_surgeon").asBoolean()).isTrue();
        assertThat(lin.get("draft").get("explanation").asText()).contains("triage checklist");
        assertThat(lin.get("resources").get("totals").get("extra_kcal").asInt()).isZero();
    }

    @Test
    void offlinePlanIsValidInEveryHabitatOnEveryDay() {
        for (String astronaut : List.of("ast_nova", "ast_kai", "ast_lin")) {
            for (String env : rules.environments().keySet()) {
                for (int day = 1; day <= 14; day++) {
                    AssessmentResponse a = assessments.assess(astronaut, day, env, null);
                    // buildPlan throws if the offline draft breaks any rule
                    CountermeasurePlan plan = planner.buildPlan(a, 80, "plan_test", false);
                    double gravity = rules.environments().get(env).gravityG();
                    assertThat(validator.validate(mapper.valueToTree(plan.draft()), gravity))
                            .as("%s %s day %d", astronaut, env, day).isEmpty();
                    if (a.triageRecommended()) {
                        assertThat(plan.draft().sessions()).isEmpty();
                    }
                }
            }
        }
    }

    @Test
    void walkingIsOnlyPlannedWhereThereIsGravity() {
        CountermeasurePlan moon = planner.buildPlan(assessments.assess("ast_nova", 14, "PLANETARY_BASE_MOON", null),
                80, "plan_test", false);
        assertThat(moon.draft().sessions()).extracting("exerciseId").contains("walk_surface", "resistive_moderate");
        CountermeasurePlan transit = planner.buildPlan(assessments.assess("ast_nova", 14, "DEEP_SPACECRAFT", null),
                80, "plan_test", false);
        assertThat(transit.draft().sessions()).extracting("exerciseId").doesNotContain("walk_surface");
    }

    // LLM path

    @Test
    void llmDraftIsUsedAndItsNumbersAreClamped() throws Exception {
        llm.configured = true;
        ObjectNode d = draft("zero_t2_ergometer", 200, "SPINE_BONE");
        ((ObjectNode) d.get("nutrition")).put("kcal_adjustment", 900.4);
        llm.answers.add(d);

        JsonNode plan = generate(assessment("ast_nova"));

        assertThat(plan.get("llm").get("model").asText()).isEqualTo("stub-model");
        assertThat(plan.get("draft").get("sessions").get(0).get("minutes").asInt()).isEqualTo(90);
        assertThat(plan.get("draft").get("nutrition").get("kcal_adjustment").asInt()).isEqualTo(600);
        // (5.0 - 1.0) MET * 80 kg * 1.5 h, computed by the server for the clamped 90 minutes
        assertThat(plan.get("resources").get("totals").get("extra_kcal").asInt()).isEqualTo(480);
        assertThat(plan.get("resources").get("totals").get("extra_water_ml").asInt()).isEqualTo(552);
        assertThat(plan.get("resources").get("totals").get("extra_co2_g").asInt()).isEqualTo(163);
        assertThat(llm.prompts).hasSize(1);
        assertThat(llm.lastAllowedIds).contains("zero_t2_ergometer").doesNotContain("walk_surface");
    }

    @Test
    void massChangesTheResourceNumbers() throws Exception {
        JsonNode a = assessment("ast_nova");
        String body = mvc.perform(post("/api/plans/generate?massKg=60").contentType(MediaType.APPLICATION_JSON)
                .content(a.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode light = mapper.readTree(body).get("resources");
        JsonNode heavy = generate(a).get("resources");
        assertThat(light.get("assumptions").get("mass_kg").asDouble()).isEqualTo(60);
        assertThat(light.get("totals").get("extra_kcal").asInt())
                .isLessThan(heavy.get("totals").get("extra_kcal").asInt());
    }

    @Test
    void rejectedDraftIsRetriedOnceWithTheReasons() throws Exception {
        llm.configured = true;
        ObjectNode bad = draft("zero_t2_ergometer", 30, "SPINE_BONE");
        bad.put("explanation", "You will burn 9999 kcal today.");
        llm.answers.add(bad);
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));

        JsonNode plan = generate(assessment("ast_nova"));

        assertThat(plan.get("llm").get("model").asText()).isEqualTo("stub-model");
        assertThat(llm.prompts).hasSize(2);
        assertThat(llm.prompts.get(1)).contains("rejected").contains("9999");
    }

    @Test
    void twoRejectedDraftsFallBackToTheOfflinePlan() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("bench_press", 30, "SPINE_BONE"));
        ObjectNode dosing = draft("zero_t2_ergometer", 30, "SPINE_BONE");
        dosing.put("explanation", "Take one tablet before training.");
        llm.answers.add(dosing);

        JsonNode plan = generate(assessment("ast_nova"));

        assertThat(llm.prompts).hasSize(2);
        assertThat(plan.get("llm").get("model").asText()).isEqualTo(PlannerService.OFFLINE_MODEL);
        assertThat(plan.get("draft").get("sessions")).hasSize(3);
    }

    @Test
    void llmFailureFallsBackWithoutRetrying() throws Exception {
        llm.configured = true;
        llm.answers.add(new IllegalStateException("connection refused"));

        JsonNode plan = generate(assessment("ast_kai"));

        assertThat(llm.prompts).hasSize(1);
        assertThat(plan.get("llm").get("model").asText()).isEqualTo(PlannerService.OFFLINE_MODEL);
    }

    @Test
    void exerciseFromTheLlmIsRefusedDuringAnEmergency() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("cycle_ergometer", 30, "CHEST_CARDIO"));
        llm.answers.add(draft("cycle_ergometer", 20, "CHEST_CARDIO"));

        JsonNode plan = generate(assessment("ast_lin"));

        assertThat(llm.prompts.get(1)).contains("exercise is on hold");
        assertThat(plan.get("llm").get("model").asText()).isEqualTo(PlannerService.OFFLINE_MODEL);
        assertThat(plan.get("draft").get("sessions")).isEmpty();
    }

    @Test
    void flightSurgeonProtocolForcesEscalation() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("thigh_cuff_bcomplex", 20, "HEAD_NEURO"));

        JsonNode d = generate(assessment("ast_nova")).get("draft");

        assertThat(d.get("escalate_to_flight_surgeon").asBoolean()).isTrue();
        assertThat(d.get("escalation_reason").asText()).contains("thigh cuff");
    }

    @Test
    void theLlmNeverSeesResourceNumbersOrRawSeries() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));
        generate(assessment("ast_kai"));

        JsonNode input = mapper.readTree(llm.prompts.get(0));
        assertThat(input.get("triggers")).hasSize(8);
        assertThat(input.get("allowed_countermeasures").findValuesAsText("exercise_id")).doesNotContain("walk_surface");
        assertThat(llm.prompts.get(0)).doesNotContain("\"met\"").doesNotContain("trend").doesNotContain("mass");
    }

    // HTTP

    @Test
    void badRequests() throws Exception {
        mvc.perform(post("/api/plans/generate").contentType(MediaType.APPLICATION_JSON).content("{\"overall\":\"RED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/plans/generate?massKg=5").contentType(MediaType.APPLICATION_JSON)
                .content(assessment("ast_nova").toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/plans/ast_nobody")).andExpect(status().isNotFound());
    }

    @Test
    void latestPlanIsTheFallbackFileUntilOneIsGenerated() throws Exception {
        JsonNode before = mapper.readTree(mvc.perform(get("/api/plans/ast_kai")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(before.get("plan_id").asText()).isEqualTo("plan_fallback_ast_kai_d14");

        JsonNode generated = generate(assessment("ast_kai"));
        JsonNode after = mapper.readTree(mvc.perform(get("/api/plans/ast_kai")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("plan_id").asText()).isEqualTo(generated.get("plan_id").asText());
    }

    // The files in src/main/resources/fallback-plans are what the offline planner gives for day 14.
    // Rewrite them with: ./mvnw test -Dtest=PlannerApiTest -Dfallback.write=true
    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin"})
    void fallbackFilesAreUpToDate(String astronautId) throws Exception {
        AssessmentResponse a = assessments.assess(astronautId, 14, "DEEP_SPACECRAFT", null);
        CountermeasurePlan plan = planner.buildPlan(a, 80, "plan_fallback_" + astronautId + "_d14", false);
        JsonNode actual = mapper.valueToTree(plan);

        if (Boolean.getBoolean("fallback.write")) {
            Path dir = Path.of("src/main/resources/fallback-plans");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(astronautId + ".json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/fallback-plans/" + astronautId + ".json")) {
            assertThat(in).as("fallback file for %s", astronautId).isNotNull();
            assertThat(JsonDiff.diff(mapper.readTree(in), actual)).isEmpty();
        }
    }
}
