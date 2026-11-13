package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.DotEnv;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import com.nasa.spacehealth.service.AssessmentService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Not a normal test: records live LLM plans for Nova, Kai and Lin (day 14) into resources/fallback-plans.
// Needs GEMINI_API_KEY (backend/.env is fine). Read the printed plans before committing them.
//   ./mvnw test -Dtest=RecordPlans -Dplans.record=true
@SpringBootTest
@EnabledIfSystemProperty(named = "plans.record", matches = "true")
class RecordPlans {

    // free tier allows 5 requests a minute, and one plan can take two
    private static final long PAUSE_MS = 30_000;
    private static final int TRIES = 3;

    @BeforeAll
    static void loadKey() {
        DotEnv.load();
    }

    @Autowired
    PlannerService planner;
    @Autowired
    AssessmentService assessments;
    @Autowired
    DraftGenerator llm;
    @Autowired
    ObjectMapper mapper;

    @Test
    void recordDay14Plans() throws Exception {
        assertThat(llm.isConfigured()).as("GEMINI_API_KEY is not set").isTrue();
        Path dir = Path.of("src/main/resources", SavedPlans.FOLDER);
        Files.createDirectories(dir);

        for (String astronautId : List.of("ast_nova", "ast_kai", "ast_lin")) {
            AssessmentResponse a = assessments.assess(astronautId, 14, "DEEP_SPACECRAFT", null);
            CountermeasurePlan plan = null;
            for (int attempt = 1; plan == null && attempt <= TRIES; attempt++) {
                try {
                    plan = planner.livePlan(a, 80, "plan_" + llm.model().replace('.', '_') + "_" + astronautId + "_d14");
                } catch (IllegalStateException e) {
                    System.out.println(astronautId + " attempt " + attempt + " failed: " + e.getMessage());
                }
                Thread.sleep(PAUSE_MS);
            }
            assertThat(plan).as("no usable live plan for %s", astronautId).isNotNull();

            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan);
            Files.writeString(dir.resolve(astronautId + ".json"), json + "\n");
            System.out.println("\n===== " + astronautId + "\n"
                    + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(plan.draft()));
        }
    }
}
