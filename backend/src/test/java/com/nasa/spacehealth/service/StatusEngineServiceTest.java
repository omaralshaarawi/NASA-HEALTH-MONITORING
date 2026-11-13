package com.nasa.spacehealth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.JsonDiff;
import com.nasa.spacehealth.config.ContractFiles;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import com.nasa.spacehealth.service.StatusEngineService.IndicatorResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Java engine is the same engine as contract/status_engine.py.
 *  - the three locked example files, field for field
 *  - 47 more outputs of the Python reference (other habitats, earlier days, synthetic edge cases)
 *  - the unit checks of contract/test_engine.py (tests 1 to 5)
 */
@SpringBootTest
class StatusEngineServiceTest {

    private static final String NOW = "2026-11-14T08:00:00Z";

    @Autowired
    StatusEngineService engine;
    @Autowired
    MeasurementStore store;
    @Autowired
    ContractFiles contract;
    @Autowired
    SchemaValidator schemas;
    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void freshSeed() {
        store.reset();
    }

    // ------------------------------------------------------------ contract examples

    @ParameterizedTest
    @ValueSource(strings = {"ast_nova", "ast_kai", "ast_lin"})
    void day14MatchesContractExampleFieldForField(String astronautId) {
        AssessmentResponse response = engine.assess(store.upTo(astronautId, 14), "DEEP_SPACECRAFT", null, NOW);
        JsonNode actual = mapper.valueToTree(response);
        JsonNode expected = contract.tree("example_assessment_" + astronautId + ".json");

        assertThat(JsonDiff.diff(expected, actual)).as("differences against example_assessment_%s.json", astronautId)
                .isEmpty();
        assertThat(schemas.validate("AssessmentResponse", actual)).as("AssessmentResponse schema violations").isEmpty();
    }

    @Test
    void seededStoriesHoldOnDay14() {
        assertThat(engine.assess(store.upTo("ast_nova", 14), "DEEP_SPACECRAFT", null, NOW).overall().name())
                .isEqualTo("GREEN");
        AssessmentResponse kai = engine.assess(store.upTo("ast_kai", 14), "DEEP_SPACECRAFT", null, NOW);
        assertThat(kai.overall().name()).isEqualTo("YELLOW");
        assertThat(kai.triageRecommended()).isFalse();
        assertThat(kai.flags()).contains("SYSTEMIC_IMMUNE", "SPINE_BONE", "INADEQUATE_NUTRITION");
        AssessmentResponse lin = engine.assess(store.upTo("ast_lin", 14), "DEEP_SPACECRAFT", null, NOW);
        assertThat(lin.overall().name()).isEqualTo("RED");
        assertThat(lin.triageRecommended()).isTrue();
        assertThat(lin.regions().get("CHEST_CARDIO").status().name()).isEqualTo("RED");
    }

    // ------------------------------------------------------------ wider Python reference

    @TestFactory
    Stream<DynamicTest> matchesPythonReference() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/reference/python_reference.json")) {
            cases = mapper.readTree(in);
        }
        assertThat(cases.size()).isGreaterThanOrEqualTo(47);
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                List<DailyMeasurementAndCompliance> records = c.has("records")
                        ? mapper.convertValue(c.get("records"), new TypeReference<>() {
                        })
                        : store.upTo(c.get("astronaut_id").asText(), c.get("day").asInt());
                JsonNode actual = mapper.valueToTree(engine.assess(records, c.get("env_id").asText(), null, NOW));
                assertThat(JsonDiff.diff(c.get("expected"), actual)).isEmpty();
                assertThat(schemas.validate("AssessmentResponse", actual)).isEmpty();
            }));
        }
        return tests.stream();
    }

    // ------------------------------------------------------------ contract/test_engine.py, tests 1-5

    private static final double[] ANCHOR_HR = {60, 61, 62, 61, 60, 62, 61};

    private static List<Double> series(double... values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static List<Double> concat(double[] head, double... tail) {
        List<Double> out = new ArrayList<>(series(head));
        out.addAll(series(tail));
        return out;
    }

    private IndicatorResult anchor(String indicatorId, List<Double> series) {
        return engine.assessAnchor(engine.indicator(indicatorId), series);
    }

    @Test
    void smallVarianceDoesNotBlowUp() {
        // raw SD of the anchor is 0.82, so a naive Z for HR 66 would be > 5; the floored SD keeps it GREEN
        IndicatorResult r = anchor("hr_rest", concat(ANCHOR_HR, 61, 62, 61, 62, 61, 62, 66));
        assertThat(r.status).isZero();
        assertThat(r.z).isEqualTo(1.67);
    }

    @Test
    void slowDriftIsCaughtAndStaysCaught() {
        List<Double> ramp = concat(ANCHOR_HR, IntStream.range(62, 76).asDoubleStream().toArray());
        int firstYellow = IntStream.rangeClosed(8, ramp.size())
                .filter(d -> anchor("hr_rest", ramp.subList(0, d)).status >= 1).findFirst().orElseThrow();
        assertThat(firstYellow - 7).isLessThanOrEqualTo(8);
        assertThat(anchor("hr_rest", ramp.subList(0, 21)).status).isGreaterThanOrEqualTo(1);
    }

    @Test
    void absoluteLimitsAreImmediate() {
        assertThat(anchor("hr_rest", series(61, 62, 102)).status).isEqualTo(2);
        double[][] glucose = {{110, 1}, {130, 1}, {142, 2}, {68, 1}, {62, 2}, {88, 0}};
        for (double[] g : glucose) {
            assertThat(anchor("glucose", series(90, 92, 91, 93, 90, 92, 91, 91, g[0])).status)
                    .as("glucose %s", g[0]).isEqualTo((int) g[1]);
        }
        double[] temp = new double[9];
        Arrays.fill(temp, 36.8);
        temp[8] = 38.0;
        assertThat(anchor("temp", series(temp)).status).isEqualTo(1);
        temp[8] = 38.4;
        assertThat(anchor("temp", series(temp)).status).isEqualTo(2);
        double[] sbp = new double[9];
        Arrays.fill(sbp, 118);
        sbp[8] = 88;
        assertThat(anchor("sbp", series(sbp)).status).isEqualTo(2);
    }

    @Test
    void twoShortNightsAreAtLeastYellow() {
        assertThat(anchor("sleep", series(7.0, 6.8, 7.2, 7.0, 6.9, 7.1, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 4.8, 4.8)).status)
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void singleOutlierNeedsConfirmation() {
        IndicatorResult single = anchor("pvt", series(250, 255, 248, 252, 249, 251, 250, 251, 250, 249, 252, 250, 251, 320));
        assertThat(single.status).isEqualTo(1);
        assertThat(single.remeasure).isTrue();

        IndicatorResult confirmed = anchor("pvt", series(250, 255, 248, 252, 249, 251, 250, 251, 250, 249, 252, 250, 320, 322));
        assertThat(confirmed.status).isEqualTo(2);
        assertThat(confirmed.remeasure).isFalse();
    }
}
