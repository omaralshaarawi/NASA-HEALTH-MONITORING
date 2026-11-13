package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Same drafts, same problems (and same wording) as validate_draft() in contract/resources.py.
@SpringBootTest
class DraftValidatorTest {

    @Autowired
    DraftValidator validator;
    @Autowired
    ObjectMapper mapper;

    @TestFactory
    Stream<DynamicTest> matchesPythonValidateDraft() throws Exception {
        JsonNode cases;
        try (InputStream in = getClass().getResourceAsStream("/reference/resources_reference.json")) {
            cases = mapper.readTree(in).get("validations");
        }
        assertThat(cases.size()).isGreaterThanOrEqualTo(28);
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : cases) {
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(), () -> {
                List<String> expected = new ArrayList<>();
                c.get("expected").forEach(e -> expected.add(e.asText()));
                assertThat(validator.validate(c.get("draft"), c.get("gravity_g").asDouble()))
                        .containsExactlyElementsOf(expected);
            }));
        }
        return tests.stream();
    }
}
