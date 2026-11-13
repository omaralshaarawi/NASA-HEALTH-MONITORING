package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Plans we already have, looked up by llm.input_hash (a hash of exactly what the LLM was sent).
// Starts with the recorded plans in resources/fallback-plans and also keeps every live LLM plan
// made since start-up, so the same assessment does not cost another API call.
@Component
public class SavedPlans {

    static final String FOLDER = "fallback-plans";

    private static final Logger log = LoggerFactory.getLogger(SavedPlans.class);

    private final ObjectMapper mapper;
    private final Map<String, CountermeasurePlan> byHash = new ConcurrentHashMap<>();
    private final Map<String, CountermeasurePlan> recordedByAstronaut = new ConcurrentHashMap<>();

    public SavedPlans(ObjectMapper mapper) {
        this.mapper = mapper;
        reset();
    }

    public CountermeasurePlan find(String inputHash) {
        return byHash.get(inputHash);
    }

    public CountermeasurePlan recordedFor(String astronautId) {
        return recordedByAstronaut.get(astronautId);
    }

    public void remember(CountermeasurePlan plan) {
        byHash.put(plan.llm().inputHash(), plan);
    }

    // back to only the recorded files
    public synchronized void reset() {
        byHash.clear();
        recordedByAstronaut.clear();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:" + FOLDER + "/*.json");
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    CountermeasurePlan plan = mapper.readValue(in, CountermeasurePlan.class);
                    byHash.put(plan.llm().inputHash(), plan);
                    recordedByAstronaut.put(plan.astronautId(), plan);
                }
            }
        } catch (IOException e) {
            log.warn("Cannot read the recorded plans: {}", e.getMessage());
        }
    }
}
