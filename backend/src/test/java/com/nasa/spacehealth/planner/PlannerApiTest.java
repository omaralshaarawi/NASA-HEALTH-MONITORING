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
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
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
    @Autowired
    SavedPlans savedPlans;
    @Autowired
    DraftPolicy policy;

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

    private MvcResult call(String query, JsonNode assessment) throws Exception {
        return mvc.perform(post("/api/plans/generate" + query).contentType(MediaType.APPLICATION_JSON)
                .content(assessment.toString())).andExpect(status().isOk()).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private String source(MvcResult r) {
        return r.getResponse().getHeader(PlannerController.SOURCE_HEADER);
    }

    private JsonNode generate(JsonNode assessment) throws Exception {
        return body(call("", assessment));
    }

    // skips the saved plans, so the (stub) LLM is asked
    private JsonNode generateFresh(JsonNode assessment) throws Exception {
        return body(call("?fresh=true", assessment));
    }

    private JsonNode assessment(String astronautId, int day) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/assessments/" + astronautId + "?day=" + day))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private CountermeasurePlan offline(String astronautId, int day, String envId) {
        return planner.offlinePlan(assessments.assess(astronautId, day, envId, null), 80, "plan_test");
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

    // plans as the API returns them

    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin", "ast_kai@9", "ast_lin@3"})
    void planIsCompleteAndValid(String which) throws Exception {
        String astronautId = which.split("@")[0];
        int day = which.contains("@") ? Integer.parseInt(which.split("@")[1]) : 14;
        JsonNode a = assessment(astronautId, day);
        JsonNode plan = generate(a);

        assertThat(plan.get("schema_version").asText()).isEqualTo("1.0.0");
        assertThat(plan.get("plan_id").asText()).startsWith("plan_");
        assertThat(plan.get("astronaut_id").asText()).isEqualTo(astronautId);
        assertThat(plan.get("generated_for_mission_day").asInt()).isEqualTo(day);
        assertThat(plan.get("generated_at_iso").asText()).isEqualTo("2026-11-14T08:00:00Z");
        assertThat(plan.get("environment_id").asText()).isEqualTo("DEEP_SPACECRAFT");
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

    // offline planner

    @Test
    void offlinePlansFitTheThreeStories() {
        JsonNode nova = mapper.valueToTree(offline("ast_nova", 14, "DEEP_SPACECRAFT").draft());
        assertThat(nova.get("sessions")).hasSize(3);
        assertThat(nova.get("sessions").get(0).get("exercise_id").asText()).isEqualTo("zero_t2_ergometer");
        assertThat(nova.get("escalate_to_flight_surgeon").asBoolean()).isFalse();
        assertThat(nova.get("nutrition").get("kcal_adjustment").asInt()).isZero();

        JsonNode kai = mapper.valueToTree(offline("ast_kai", 14, "DEEP_SPACECRAFT").draft());
        assertThat(kai.get("sessions").findValuesAsText("exercise_id"))
                .containsExactly("zero_t2_ergometer", "circadian_led_journaling", "microbial_swab_air_check");
        assertThat(kai.get("sessions").get(0).get("minutes").asInt()).isEqualTo(45);
        assertThat(kai.get("nutrition").get("kcal_adjustment").asInt()).isEqualTo(200);
        assertThat(kai.get("escalate_to_flight_surgeon").asBoolean()).isTrue();

        JsonNode lin = mapper.valueToTree(offline("ast_lin", 14, "DEEP_SPACECRAFT"));
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
                    // offlinePlan throws if the draft breaks any rule
                    CountermeasurePlan plan = planner.offlinePlan(a, 80, "plan_test");
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
        CountermeasurePlan moon = offline("ast_nova", 14, "PLANETARY_BASE_MOON");
        assertThat(moon.draft().sessions()).extracting("exerciseId").contains("walk_surface", "resistive_moderate");
        CountermeasurePlan transit = offline("ast_nova", 14, "DEEP_SPACECRAFT");
        assertThat(transit.draft().sessions()).extracting("exerciseId").doesNotContain("walk_surface");
    }

    // LLM path

    @Test
    void llmDraftIsUsedAndItsNumbersAreClamped() throws Exception {
        llm.configured = true;
        ObjectNode d = draft("zero_t2_ergometer", 200, "SPINE_BONE");
        ((ObjectNode) d.get("nutrition")).put("kcal_adjustment", 900.4);
        llm.answers.add(d);

        JsonNode plan = generateFresh(assessment("ast_nova"));

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

        JsonNode plan = generateFresh(assessment("ast_nova"));

        assertThat(plan.get("llm").get("model").asText()).isEqualTo("stub-model");
        assertThat(llm.prompts).hasSize(2);
        assertThat(llm.prompts.get(1)).contains("rejected").contains("9999");
    }

    private ObjectNode dosingDraft() {
        ObjectNode dosing = draft("zero_t2_ergometer", 30, "SPINE_BONE");
        dosing.put("explanation", "Take one tablet before training.");
        return dosing;
    }

    @Test
    void twoRejectedDraftsFallBackToTheRecordedPlan() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("bench_press", 30, "SPINE_BONE"));
        llm.answers.add(dosingDraft());

        MvcResult r = call("?fresh=true", assessment("ast_nova"));

        assertThat(llm.prompts).hasSize(2);
        assertThat(source(r)).isEqualTo("SAVED");
        assertThat(body(r).get("llm").get("model").asText())
                .isEqualTo(savedPlans.recordedFor("ast_nova").llm().model());
    }

    @Test
    void twoRejectedDraftsWithNothingSavedFallBackToTheOfflinePlan() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("bench_press", 30, "SPINE_BONE"));
        llm.answers.add(dosingDraft());

        MvcResult r = call("", assessment("ast_nova", 10));

        assertThat(llm.prompts).hasSize(2);
        assertThat(source(r)).isEqualTo("OFFLINE");
        assertThat(body(r).get("llm").get("model").asText()).isEqualTo(PlannerService.OFFLINE_MODEL);
        assertThat(body(r).get("draft").get("sessions")).hasSize(3);
    }

    @Test
    void llmFailureFallsBackWithoutRetrying() throws Exception {
        llm.configured = true;
        llm.answers.add(new IllegalStateException("connection refused"));

        MvcResult r = call("?fresh=true", assessment("ast_kai"));

        assertThat(llm.prompts).hasSize(1);
        assertThat(source(r)).isEqualTo("SAVED");
    }

    @Test
    void exerciseFromTheLlmIsRefusedDuringAnEmergency() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("cycle_ergometer", 30, "CHEST_CARDIO"));
        llm.answers.add(draft("cycle_ergometer", 20, "CHEST_CARDIO"));

        JsonNode plan = generateFresh(assessment("ast_lin"));

        assertThat(llm.prompts.get(1)).contains("exercise is on hold");
        assertThat(plan.get("draft").get("sessions")).isEmpty();
        assertThat(plan.get("draft").get("escalate_to_flight_surgeon").asBoolean()).isTrue();
    }

    @Test
    void threeRegionsOffBaselineForceEscalation() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));   // says escalate = false

        JsonNode d = generateFresh(assessment("ast_kai")).get("draft");

        assertThat(d.get("escalate_to_flight_surgeon").asBoolean()).isTrue();
        assertThat(d.get("escalation_reason").asText()).contains("Several body systems");
    }

    // saved plans

    @Test
    void recordedPlanIsServedWithoutCallingTheLlm() throws Exception {
        llm.configured = true;

        MvcResult r = call("", assessment("ast_kai"));

        assertThat(llm.prompts).isEmpty();
        assertThat(source(r)).isEqualTo("SAVED");
        assertThat(body(r).get("plan_id").asText()).isNotEqualTo(savedPlans.recordedFor("ast_kai").planId());
        assertThat(body(r).get("draft").get("explanation").asText())
                .isEqualTo(savedPlans.recordedFor("ast_kai").draft().explanation());
    }

    @Test
    void liveAnswerIsReusedForTheSameAssessment() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));
        JsonNode a = assessment("ast_nova", 10);

        MvcResult first = call("", a);
        MvcResult second = call("", a);

        assertThat(llm.prompts).hasSize(1);
        assertThat(source(first)).isEqualTo("LIVE");
        assertThat(source(second)).isEqualTo("SAVED");
        assertThat(body(second).get("llm").get("model").asText()).isEqualTo("stub-model");
    }

    @Test
    void otherAssessmentsAreNotServedASavedPlan() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));

        MvcResult r = call("", assessment("ast_kai", 13));

        assertThat(source(r)).isEqualTo("LIVE");
        assertThat(llm.prompts).hasSize(1);
    }

    @Test
    void flightSurgeonProtocolForcesEscalation() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("thigh_cuff_bcomplex", 20, "HEAD_NEURO"));

        JsonNode d = generateFresh(assessment("ast_nova")).get("draft");

        assertThat(d.get("escalate_to_flight_surgeon").asBoolean()).isTrue();
        assertThat(d.get("escalation_reason").asText()).contains("thigh cuff");
    }

    @Test
    void theLlmNeverSeesResourceNumbersOrRawSeries() throws Exception {
        llm.configured = true;
        llm.answers.add(draft("zero_t2_ergometer", 30, "SPINE_BONE"));
        generateFresh(assessment("ast_kai"));

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
        assertThat(before.get("plan_id").asText()).isEqualTo(savedPlans.recordedFor("ast_kai").planId());

        JsonNode generated = generate(assessment("ast_kai"));
        JsonNode after = mapper.readTree(mvc.perform(get("/api/plans/ast_kai")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("plan_id").asText()).isEqualTo(generated.get("plan_id").asText());
    }

    // The recorded plans in src/main/resources/fallback-plans must still match today's day-14 assessment
    // and pass every check unchanged. Re-record with: ./mvnw test -Dtest=RecordPlans -Dplans.record=true
    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin"})
    void recordedPlansAreCurrentAndClean(String astronautId) throws Exception {
        JsonNode file;
        try (InputStream in = getClass().getResourceAsStream("/fallback-plans/" + astronautId + ".json")) {
            assertThat(in).as("recorded plan for %s", astronautId).isNotNull();
            file = mapper.readTree(in);
        }
        CountermeasurePlan plan = mapper.treeToValue(file, CountermeasurePlan.class);
        AssessmentResponse a = assessments.assess(astronautId, 14, "DEEP_SPACECRAFT", null);

        assertThat(plan.llm().inputHash()).as("prompt or assessment changed since recording: re-record the plans")
                .isEqualTo(planner.inputHash(a));
        JsonNode draft = file.get("draft");
        JsonNode clamped = policy.clamp(draft, a);
        assertThat(JsonDiff.diff(draft, clamped)).as("the server would change this draft").isEmpty();
        assertThat(policy.check(clamped, a, 0.0)).isEmpty();
        ResourceBlock expected = resources.buildResourceBlock(plan.draft().sessions(), 80);
        assertThat(JsonDiff.diff(mapper.valueToTree(expected), file.get("resources"))).isEmpty();
        assertThat(JsonDiff.diff(mapper.valueToTree(offline(astronautId, 14, "DEEP_SPACECRAFT").triggers()),
                file.get("triggers"))).isEmpty();
    }
}
