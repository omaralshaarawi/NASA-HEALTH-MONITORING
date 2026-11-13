package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.JsonDiff;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft;
import com.nasa.spacehealth.planner.model.ResourceBlock;
import com.nasa.spacehealth.planner.model.ResourceBlock.SessionImpact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Compares the Java resource math with contract/resources.py (outputs saved in resources_reference.json).
@SpringBootTest
class ResourcesServiceTest {

    @Autowired
    ResourcesService resources;
    @Autowired
    ObjectMapper mapper;

    JsonNode reference;

    @BeforeEach
    void loadReference() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/reference/resources_reference.json")) {
            reference = mapper.readTree(in);
        }
    }

    @Test
    void sameNumbersAsThePythonSelfTest() {
        SessionImpact s = resources.sessionImpact("resistive_vigorous", 30, 80);
        assertThat(s.met()).isEqualTo(6.0);
        assertThat(s.grossKcal()).isEqualTo(240);
        assertThat(s.extraKcal()).isEqualTo(200);
        assertThat(s.extraWaterMl()).isEqualTo(230);
        assertThat(s.extraCo2G()).isEqualTo(68);
    }

    @Test
    void sessionImpactsMatchPython() {
        JsonNode cases = reference.get("session_impacts");
        assertThat(cases.size()).isGreaterThan(500);
        List<String> differences = new ArrayList<>();
        for (JsonNode c : cases) {
            String id = c.get("exercise_id").asText();
            int minutes = c.get("minutes").asInt();
            double mass = c.get("mass_kg").asDouble();
            SessionImpact s = resources.sessionImpact(id, minutes, mass);
            JsonNode e = c.get("expected");
            if (s.met() != e.get("met").asDouble() || s.grossKcal() != e.get("gross_kcal").asLong()
                    || s.extraKcal() != e.get("extra_kcal").asLong()
                    || s.extraWaterMl() != e.get("extra_water_ml").asLong()
                    || s.extraCo2G() != e.get("extra_co2_g").asLong()) {
                differences.add(id + " " + minutes + " min " + mass + " kg: expected " + e + " got " + s);
            }
        }
        assertThat(differences).isEmpty();
    }

    @Test
    void resourceBlocksMatchPythonFieldForField() {
        for (JsonNode c : reference.get("resource_blocks")) {
            List<CountermeasurePlanDraft.Session> sessions = new ArrayList<>();
            for (JsonNode s : c.get("sessions")) {
                sessions.add(new CountermeasurePlanDraft.Session(s.get("exercise_id").asText(),
                        s.get("minutes").asInt(), null, List.of(), ""));
            }
            ResourceBlock block = resources.buildResourceBlock(sessions, c.get("mass_kg").asDouble());
            assertThat(JsonDiff.diff(c.get("expected"), mapper.valueToTree(block))).isEmpty();
        }
    }

    @Test
    void restingProtocolsCostNothingExtra() {
        SessionImpact s = resources.sessionImpact("thigh_cuff_bcomplex", 60, 80);
        assertThat(s.grossKcal()).isEqualTo(80);
        assertThat(s.extraKcal()).isZero();
        assertThat(s.extraWaterMl()).isZero();
        assertThat(s.extraCo2G()).isZero();
    }

    @Test
    void unknownExerciseIsRejected() {
        assertThatThrownBy(() -> resources.sessionImpact("bench_press", 30, 80))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
