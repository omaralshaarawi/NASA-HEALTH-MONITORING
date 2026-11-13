package com.nasa.spacehealth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nasa.spacehealth.JsonDiff;
import com.nasa.spacehealth.config.ContractFiles;
import com.nasa.spacehealth.repository.MeasurementRepository;
import com.nasa.spacehealth.service.SchemaValidator;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @TestConfiguration
    static class FixedClock {
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
    ContractFiles contract;
    @Autowired
    SchemaValidator schemas;
    @Autowired
    MeasurementRepository store;

    @BeforeEach
    void freshSeed() {
        store.reset();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url) throws Exception {
        return json(mvc.perform(get(url)).andExpect(status().isOk()).andReturn());
    }

    private MvcResult postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode postOk(String url, Object body) throws Exception {
        MvcResult r = postJson(url, body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json(r);
    }

    private ObjectNode obj() {
        return mapper.createObjectNode();
    }

    // core API

    @Test
    void astronautsListsTheThreeSeededStories() throws Exception {
        JsonNode list = getJson("/api/astronauts");
        assertThat(list).hasSize(3);
        assertThat(list.get(0).get("astronaut_id").asText()).isEqualTo("ast_nova");
        assertThat(list.get(0).get("display_name").asText()).isEqualTo("Nova");
        assertThat(list.get(0).get("overall").asText()).isEqualTo("GREEN");
        assertThat(list.get(1).get("overall").asText()).isEqualTo("YELLOW");
        assertThat(list.get(2).get("overall").asText()).isEqualTo("RED");
        assertThat(list.get(2).get("triage_recommended").asBoolean()).isTrue();
        assertThat(list.get(2).get("latest_mission_day").asInt()).isEqualTo(14);
        assertThat(list.get(2).get("environment_id").asText()).isEqualTo("DEEP_SPACECRAFT");
    }

    @Test
    void environmentsReturnsSixPresetsWithComputedDelay() throws Exception {
        JsonNode list = getJson("/api/environments");
        assertThat(list).hasSize(6);
        JsonNode station = list.get(0);
        assertThat(station.get("id").asText()).isEqualTo("SPACE_STATION");
        assertThat(station.get("one_way_delay_s").asDouble()).isEqualTo(0.001);
        assertThat(station.get("comm_mode").asText()).isEqualTo("LIVE");
        assertThat(station.has("distance_range_km")).isFalse();
        JsonNode transit = list.get(1);
        assertThat(transit.get("one_way_delay_s").asDouble()).isEqualTo(750.519);
        assertThat(transit.get("comm_mode").asText()).isEqualTo("AUTONOMOUS");
        assertThat(transit.get("radiation_expected_mSv_day").asDouble()).isEqualTo(1.4);
        assertThat(transit.get("radiation_mSv_day").asDouble()).isEqualTo(1.4);
        assertThat(transit.get("cabin_co2_mmHg").asDouble()).isEqualTo(2.8);
        assertThat(transit.get("distance_range_km").get("max").asLong()).isEqualTo(401000000L);
        JsonNode moon = list.get(2);
        assertThat(moon.get("one_way_delay_s").asDouble()).isEqualTo(1.282);
        assertThat(moon.get("comm_mode").asText()).isEqualTo("LIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin"})
    void assessmentOverHttpEqualsTheContractExample(String id) throws Exception {
        JsonNode actual = getJson("/api/assessments/" + id + "?day=14&envId=DEEP_SPACECRAFT");
        assertThat(JsonDiff.diff(contract.tree("example_assessment_" + id + ".json"), actual)).isEmpty();
        // and the defaults (latest day, recorded habitat) give the same answer
        assertThat(JsonDiff.diff(actual, getJson("/api/assessments/" + id))).isEmpty();
    }

    @Test
    void switchingHabitatRescalesRadiationButKeepsTheStory() throws Exception {
        JsonNode nova = getJson("/api/assessments/ast_nova?day=14&envId=SPACE_STATION");
        assertThat(nova.get("overall").asText()).isEqualTo("GREEN");
        assertThat(nova.get("environment").get("comm_mode").asText()).isEqualTo("LIVE");
        assertThat(nova.get("comms").get("emergency_action").asText()).isEqualTo("CALL_FLIGHT_SURGEON");
        assertThat(schemas.validate("AssessmentResponse", nova)).isEmpty();

        JsonNode kaiDeep = getJson("/api/assessments/ast_kai?day=14&envId=DEEP_SPACECRAFT");
        JsonNode kaiStation = getJson("/api/assessments/ast_kai?day=14&envId=SPACE_STATION");
        assertThat(kaiStation.get("overall").asText()).isEqualTo("YELLOW");
        assertThat(ratio(kaiStation)).isCloseTo(ratio(kaiDeep), org.assertj.core.data.Offset.offset(0.01));
        assertThat(kaiStation.get("flags").toString()).contains("SYSTEMIC_IMMUNE");

        // partial gravity halves the resistive target, so Kai's 10-25 min days are no longer "low"
        JsonNode kaiMoon = getJson("/api/assessments/ast_kai?day=14&envId=PLANETARY_BASE_MOON");
        assertThat(kaiMoon.get("environment").get("resistive_target_min").asInt()).isEqualTo(30);
        assertThat(kaiMoon.get("regions").get("SPINE_BONE").get("status").asText()).isEqualTo("GREEN");

        assertThat(getJson("/api/assessments/ast_lin?envId=SURFACE_ROVER_MARS").get("overall").asText()).isEqualTo("RED");
    }

    private static double ratio(JsonNode assessment) {
        for (JsonNode i : assessment.get("indicators")) {
            if (i.get("id").asText().equals("radiation")) {
                return i.get("ratio").asDouble();
            }
        }
        throw new AssertionError("no radiation indicator");
    }

    @Test
    void marsDistanceSliderDrivesTheDelay() throws Exception {
        JsonNode near = getJson("/api/assessments/ast_nova?envId=PLANETARY_BASE_MARS&distanceKm=54600000");
        assertThat(near.get("environment").get("distance_km").asLong()).isEqualTo(54600000L);
        assertThat(near.get("environment").get("one_way_delay_s").asDouble()).isEqualTo(182.126);
        assertThat(near.get("comms").get("one_way_delay_s").asDouble()).isEqualTo(182.126);
        // clamped to the rules.json range
        JsonNode far = getJson("/api/assessments/ast_nova?envId=PLANETARY_BASE_MARS&distanceKm=999999999999");
        assertThat(far.get("environment").get("distance_km").asLong()).isEqualTo(401000000L);
        // ignored where the distance is fixed
        JsonNode moon = getJson("/api/assessments/ast_nova?envId=PLANETARY_BASE_MOON&distanceKm=54600000");
        assertThat(moon.get("environment").get("distance_km").asLong()).isEqualTo(384400L);
    }

    @Test
    void earlierDayUsesOnlyRecordsUpToThatDay() throws Exception {
        JsonNode day5 = getJson("/api/assessments/ast_kai?day=5");
        assertThat(day5.get("mission_day").asInt()).isEqualTo(5);
        assertThat(day5.get("indicators").get(0).get("trend")).hasSize(5);
        assertThat(day5.get("indicators").get(0).get("anchor").isNull()).isTrue();
    }

    @Test
    void badRequestsGetClearErrors() throws Exception {
        mvc.perform(get("/api/assessments/ast_nobody")).andExpect(status().isNotFound());
        mvc.perform(get("/api/assessments/ast_nova?day=15")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assessments/ast_nova?day=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/assessments/ast_nova?day=abc")).andExpect(status().isBadRequest());
        MvcResult r = mvc.perform(get("/api/assessments/ast_nova?envId=PLUTO")).andExpect(status().isBadRequest()).andReturn();
        assertThat(json(r).get("message").asText()).contains("PLUTO").contains("SPACE_STATION");
    }

    @Test
    void corsAllowsTheAngularDevServer() throws Exception {
        mvc.perform(options("/api/measurements")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(get("/api/astronauts").header("Origin", "http://evil.example"))
                .andExpect(status().isForbidden());
    }

    // POST /api/measurements

    private ObjectNode novaDay(int day) {
        ObjectNode r = (ObjectNode) contract.tree("seed.json").get(13).deepCopy();   // ast_nova day 14
        assertThat(r.get("astronaut_id").asText()).isEqualTo("ast_nova");
        r.put("mission_day", day);
        r.put("source", "MANUAL");
        return r;
    }

    @Test
    void postingTheNextDayUpdatesStateAndReturnsItsAssessment() throws Exception {
        ObjectNode day15 = novaDay(15);
        ((ObjectNode) day15.get("vitals")).put("hr_rest_bpm", 104.0);
        JsonNode a = postOk("/api/measurements", day15);
        assertThat(a.get("mission_day").asInt()).isEqualTo(15);
        assertThat(a.get("overall").asText()).isEqualTo("RED");
        assertThat(a.get("triage_recommended").asBoolean()).isTrue();
        assertThat(a.get("indicators").get(0).get("reasons").get(0).asText()).isEqualTo("104 bpm above red limit 100");
        assertThat(a.get("indicators").get(0).get("trend")).hasSize(14);
        assertThat(schemas.validate("AssessmentResponse", a)).isEmpty();

        // state was updated: the list and a plain GET now see day 15
        assertThat(getJson("/api/astronauts").get(0).get("latest_mission_day").asInt()).isEqualTo(15);
        assertThat(getJson("/api/assessments/ast_nova").get("mission_day").asInt()).isEqualTo(15);

        // posting the same day again replaces it
        ((ObjectNode) day15.get("vitals")).put("hr_rest_bpm", 61.0);
        assertThat(postOk("/api/measurements", day15).get("overall").asText()).isEqualTo("GREEN");

        // reset goes back to the seed
        postOk("/api/reset", obj());
        assertThat(getJson("/api/assessments/ast_nova").get("mission_day").asInt()).isEqualTo(14);
    }

    @Test
    void missingVitalsAreNotMeasuredNeverZero() throws Exception {
        ObjectNode day15 = novaDay(15);
        ((ObjectNode) day15.get("vitals")).removeAll();
        day15.putNull("pvt");
        JsonNode a = postOk("/api/measurements", day15);
        assertThat(a.get("overall").asText()).isEqualTo("GREEN");
        JsonNode hr = a.get("indicators").get(0);
        assertThat(hr.get("value").isNull()).isTrue();
        assertThat(hr.get("reasons").get(0).asText()).isEqualTo("no reading today");
    }

    @Test
    void invalidMeasurementsAreRejected() throws Exception {
        ObjectNode gap = novaDay(17);
        assertThat(postJson("/api/measurements", gap).getResponse().getStatus()).isEqualTo(409);

        ObjectNode noHabitat = novaDay(15);
        noHabitat.remove("habitat");
        MvcResult r = postJson("/api/measurements", noHabitat);
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(r).get("message").asText()).contains("habitat");

        ObjectNode badEnv = novaDay(15);
        badEnv.put("environment_id", "PLUTO");
        assertThat(postJson("/api/measurements", badEnv).getResponse().getStatus()).isEqualTo(400);

        ObjectNode textVital = novaDay(15);
        ((ObjectNode) textVital.get("vitals")).put("hr_rest_bpm", "fast");
        assertThat(postJson("/api/measurements", textVital).getResponse().getStatus()).isEqualTo(400);

        mvc.perform(post("/api/measurements").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
        assertThat(getJson("/api/assessments/ast_nova").get("mission_day").asInt()).isEqualTo(14);
    }

    // triage

    // The generated schema types the two attachment lists as allOf[array, object], which nothing
    // can pass, so those fields are checked by hand and removed before validating the rest.
    private void assertValidPayload(JsonNode payload) {
        assertThat(payload.get("patient_snapshot").get("recent_attachments").isArray()).isTrue();
        ObjectNode copy = payload.deepCopy();
        ((ObjectNode) copy.get("patient_snapshot")).remove("recent_attachments");
        if (copy.has("store_and_forward")) {
            assertThat(copy.get("store_and_forward").get("attachments").isArray()).isTrue();
            ((ObjectNode) copy.get("store_and_forward")).remove("attachments");
        }
        assertThat(schemas.validate("EmergencyTriagePayload", copy)).as("EmergencyTriagePayload schema violations").isEmpty();
    }

    private JsonNode start(String patient) throws Exception {
        JsonNode p = postOk("/api/triage/start", obj().put("patient_id", patient).put("reporter_id", "ast_nova"));
        assertValidPayload(p);
        return p;
    }

    private JsonNode answer(String incident, String answer) throws Exception {
        JsonNode p = postOk("/api/triage/step", obj().put("incident_id", incident).put("answer", answer));
        assertValidPayload(p);
        return p;
    }

    private static String at(JsonNode payload) {
        return payload.get("state").get("current_step_id").asText();
    }

    @Test
    void startBuildsThePayloadFromStoreAndEngine() throws Exception {
        JsonNode p = start("ast_lin");
        assertThat(p.get("schema_version").asText()).isEqualTo("1.0.0");
        assertThat(p.get("incident_id").asText()).startsWith("inc_");
        assertThat(p.get("started_at_iso").asText()).isEqualTo("2026-11-14T08:00:00Z");
        assertThat(p.get("scenario").asText()).isEqualTo("CHEST_PAIN_COLLAPSE");
        assertThat(p.get("patient_id").asText()).isEqualTo("ast_lin");
        assertThat(p.get("reporter_id").asText()).isEqualTo("ast_nova");
        assertThat(p.get("environment").get("id").asText()).isEqualTo("DEEP_SPACECRAFT");
        assertThat(p.get("environment").get("one_way_delay_s").asDouble()).isEqualTo(750.519);
        assertThat(p.get("environment").get("comm_mode").asText()).isEqualTo("AUTONOMOUS");
        JsonNode snap = p.get("patient_snapshot");
        assertThat(snap.get("mission_day").asInt()).isEqualTo(14);
        assertThat(snap.get("overall").asText()).isEqualTo("RED");
        List<String> red = new ArrayList<>();
        snap.get("red_indicators").forEach(i -> red.add(i.get("id").asText()));
        assertThat(red).containsExactly("hr_rest", "sbp", "dbp");
        assertThat(snap.get("red_indicators").get(0).get("reasons").get(0).asText()).isEqualTo("118 bpm above red limit 100");
        assertThat(snap.get("latest_vitals").get("sbp_mmHg").asDouble()).isEqualTo(168.0);
        assertThat(snap.get("latest_vitals").get("mood_1_10").asDouble()).isEqualTo(5.0);
        assertThat(p.get("protocol").get("protocol_id").asText()).isEqualTo("chest_pain_collapse");
        assertThat(at(p)).isEqualTo("S0_RESPONSE");
        assertThat(p.get("state").get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(p.get("state").get("step_log")).isEmpty();
        assertThat(p.get("agent_rules").size()).isGreaterThan(3);
        assertThat(p.has("store_and_forward")).isFalse();
        assertThat(p.get("disclaimer").asText()).startsWith("Prototype decision-support tool");

        assertThat(JsonDiff.diff(p, getJson("/api/triage/" + p.get("incident_id").asText()))).isEmpty();
    }

    @Test
    void cprLoopCanBeLeftManuallyAndQueuesThePacket() throws Exception {
        String id = start("ast_lin").get("incident_id").asText();
        assertThat(at(answer(id, "no"))).isEqualTo("U1_CALL_HELP");
        assertThat(at(answer(id, "done"))).isEqualTo("U2_BREATHING_PULSE");
        assertThat(at(answer(id, "no"))).isEqualTo("A1_CPR");
        assertThat(at(answer(id, "done"))).isEqualTo("A2_AED_ATTACH");
        assertThat(at(answer(id, "done"))).isEqualTo("A3_SHOCK_ADVISED");
        for (int round = 0; round < 3; round++) {                       // the loop never ends by itself
            assertThat(at(answer(id, "yes"))).isEqualTo("A4_SHOCK");
            assertThat(at(answer(id, "done"))).isEqualTo("A6_REASSESS");
            assertThat(at(answer(id, "no"))).isEqualTo("A3_SHOCK_ADVISED");
        }
        assertThat(at(answer(id, "no"))).isEqualTo("A5_RESUME");

        JsonNode ended = postOk("/api/triage/end", obj().put("incident_id", id).put("reason", "second crew member took over"));
        assertValidPayload(ended);
        assertThat(ended.get("state").get("status").asText()).isEqualTo("ENDED");
        assertThat(at(ended)).isEqualTo("A5_RESUME");
        JsonNode log = ended.get("state").get("step_log");
        assertThat(log).hasSize(16);
        assertThat(log.get(0).get("step_id").asText()).isEqualTo("S0_RESPONSE");
        assertThat(log.get(0).get("answer").asText()).isEqualTo("no");
        assertThat(log.get(15).get("step_id").asText()).isEqualTo("A5_RESUME");
        assertThat(log.get(15).has("answer")).isFalse();

        JsonNode packet = ended.get("store_and_forward");
        assertThat(packet.get("packet_id").asText()).startsWith("pkt_");
        assertThat(packet.get("delivery").asText()).isEqualTo("QUEUED_FOR_UPLINK");
        assertThat(packet.get("created_at_iso").asText()).isEqualTo("2026-11-14T08:00:00Z");
        // created_at + one_way_delay_s = 08:00:00 + 750.519 s
        assertThat(packet.get("estimated_earth_receipt_iso").asText()).isEqualTo("2026-11-14T08:12:30.519Z");
        assertThat(packet.get("summary").asText())
                .contains("ast_lin").contains("hr_rest 118 bpm").contains("S0_RESPONSE=no > U1_CALL_HELP=done")
                .contains("ended manually by the crew at step A5_RESUME").contains("second crew member took over")
                .contains("packet queued for uplink");

        // ending twice is harmless, stepping after the end is refused
        assertThat(JsonDiff.diff(ended, postOk("/api/triage/end", obj().put("incident_id", id)))).isEmpty();
        assertThat(postJson("/api/triage/step", obj().put("incident_id", id).put("answer", "done")).getResponse().getStatus())
                .isEqualTo(409);
    }

    @Test
    void consciousPathRunsToEndAndSendsLiveNearEarth() throws Exception {
        JsonNode p = postOk("/api/triage/start", obj().put("patient_id", "ast_lin").put("environment_id", "PLANETARY_BASE_MOON"));
        assertThat(p.get("reporter_id").asText()).isEqualTo("ast_lin");
        assertThat(p.get("environment").get("comm_mode").asText()).isEqualTo("LIVE");
        String id = p.get("incident_id").asText();
        String[][] walk = {{"yes", "C1_CONSCIOUS"}, {"done", "C2_WARNING_SIGNS"}, {"yes", "C3_ECG"},
                {"done", "C4_MEDS_CHECKLIST"}, {"done", "C5_REASSESS"}, {"yes", "P1_PACKET"}, {"done", "P2_SEND"}};
        for (String[] w : walk) {
            JsonNode s = answer(id, w[0]);
            assertThat(at(s)).isEqualTo(w[1]);
            assertThat(s.has("store_and_forward")).isFalse();
        }
        JsonNode done = answer(id, "done");
        assertThat(at(done)).isEqualTo("END");
        assertThat(done.get("state").get("status").asText()).isEqualTo("ENDED");
        JsonNode packet = done.get("store_and_forward");
        assertThat(packet.get("delivery").asText()).isEqualTo("SENT_LIVE");
        assertThat(packet.get("estimated_earth_receipt_iso").asText()).isEqualTo("2026-11-14T08:00:01.282Z");
        assertThat(packet.get("summary").asText()).contains("Outcome: protocol completed").contains("packet sent live");
    }

    @Test
    void wrongAnswersAndStaleStepsAreRefused() throws Exception {
        String id = start("ast_lin").get("incident_id").asText();
        MvcResult bad = postJson("/api/triage/step", obj().put("incident_id", id).put("answer", "done"));
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(bad).get("message").asText()).contains("S0_RESPONSE").contains("yes or no");
        assertThat(postJson("/api/triage/step", obj().put("incident_id", id).put("answer", "maybe")).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(postJson("/api/triage/step", obj().put("incident_id", id).put("answer", "yes").put("step_id", "A1_CPR"))
                .getResponse().getStatus()).isEqualTo(409);
        assertThat(postJson("/api/triage/step", obj().put("incident_id", "inc_missing").put("answer", "yes"))
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(postJson("/api/triage/start", obj()).getResponse().getStatus()).isEqualTo(400);
        assertThat(postJson("/api/triage/start", obj().put("patient_id", "ast_nobody")).getResponse().getStatus())
                .isEqualTo(404);
        // nothing above moved the incident; a matching step_id is accepted
        assertThat(at(postOk("/api/triage/step", obj().put("incident_id", id).put("answer", "YES").put("step_id", "S0_RESPONSE"))))
                .isEqualTo("C1_CONSCIOUS");
    }

    @Test
    void protocolIsServedAsLoaded() throws Exception {
        JsonNode served = getJson("/api/triage/protocol");
        assertThat(JsonDiff.diff(contract.tree("triage_protocol.json"), served)).isEmpty();
        assertThat(schemas.validate("TriageProtocol", served)).isEmpty();
    }
}
