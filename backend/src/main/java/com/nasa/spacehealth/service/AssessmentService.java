package com.nasa.spacehealth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.controller.ApiException;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.AstronautSummary;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance.Habitat;
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.model.Rules;
import com.nasa.spacehealth.model.Rules.EnvironmentPreset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Glue between the in-memory store and the engine: picks the records, the habitat and "now". */
@Service
public class AssessmentService {

    /** Demo metadata for the three seeded astronauts (see contract/seed.py). Anyone else gets a derived name. */
    private static final Map<String, String[]> SEEDED = Map.of(
            "ast_nova", new String[]{"Nova", "Nominal: all systems inside the personal baseline"},
            "ast_kai", new String[]{"Kai", "Multi-system drift from day 8, solar particle event on days 13-14"},
            "ast_lin", new String[]{"Lin", "Acute chest-pain emergency on day 14"});

    private final MeasurementStore store;
    private final StatusEngineService engine;
    private final SchemaValidator schemas;
    private final Rules rules;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AssessmentService(MeasurementStore store, StatusEngineService engine, SchemaValidator schemas, Rules rules,
                             ObjectMapper mapper, Clock clock) {
        this.store = store;
        this.engine = engine;
        this.schemas = schemas;
        this.rules = rules;
        this.mapper = mapper;
        this.clock = clock;
    }

    public String nowIso() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public List<AstronautSummary> astronauts() {
        return store.astronautIds().stream().map(id -> {
            AssessmentResponse a = assess(id, null, null, null);
            String[] meta = SEEDED.get(id);
            String name = meta != null ? meta[0] : displayName(id);
            return new AstronautSummary(id, name, meta != null ? meta[1] : "Live data", a.missionDay(),
                    a.environment().id(), a.overall(), a.triageRecommended());
        }).toList();
    }

    /** The six presets of rules.json with computed delay and comm mode; telemetry is the preset default. */
    public List<EnvironmentProfile> environments() {
        return rules.environments().entrySet().stream()
                .map(e -> engine.environmentProfile(e.getKey(), null, e.getValue().radiationExpectedMsvDay(),
                        e.getValue().cabinCo2DefaultMmHg()))
                .toList();
    }

    /**
     * @param day        mission day to assess (null = latest logged day); days 1..day feed the engine
     * @param envId      habitat to judge against (null = the habitat that day was recorded in)
     * @param distanceKm Earth distance for the Mars habitats (null = rules.json default)
     */
    public AssessmentResponse assess(String astronautId, Integer day, String envId, Double distanceKm) {
        int d = day != null ? day : store.latestDay(astronautId);
        List<DailyMeasurementAndCompliance> records = store.upTo(astronautId, d);
        String env = envId != null && !envId.isBlank() ? envId : records.get(records.size() - 1).environmentId();
        if (!rules.environments().containsKey(env)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown environment '" + env + "'. Valid: "
                    + rules.environments().keySet());
        }
        List<DailyMeasurementAndCompliance> viewed = records.stream().map(r -> viewedIn(r, env)).toList();
        return engine.assess(viewed, env, distanceKm == null ? null : Math.round(distanceKm), nowIso());
    }

    /** Validate against the contract schema, store, and return the assessment for that day in the record's habitat. */
    public AssessmentResponse record(JsonNode body) {
        List<String> violations = schemas.validate("DailyMeasurementAndCompliance", body);
        if (!violations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Body is not a valid DailyMeasurementAndCompliance: "
                    + String.join("; ", violations));
        }
        JsonNode day = body.get("mission_day");
        if (!day.isIntegralNumber() || day.asInt() < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "mission_day must be a whole number >= 1");
        }
        DailyMeasurementAndCompliance record = mapper.convertValue(body, DailyMeasurementAndCompliance.class);
        store.upsert(record);
        return assess(record.astronautId(), record.missionDay(), record.environmentId(), null);
    }

    public int reset() {
        return store.reset();
    }

    /**
     * Habitat switching. Radiation telemetry only means something relative to the habitat it was recorded in
     * (1.4 mSv/day is normal in deep space and 3.5x the expected rate on the station). When a day recorded in one
     * habitat is viewed in another, its dose rate is rescaled so the ratio to the expected rate is preserved:
     * a quiet day stays quiet and a solar particle event stays an event. Everything else is left as recorded.
     * Days viewed in the habitat they were recorded in are untouched, so the reference outputs still match exactly.
     */
    private DailyMeasurementAndCompliance viewedIn(DailyMeasurementAndCompliance r, String envId) {
        if (envId.equals(r.environmentId()) || r.habitat() == null || r.habitat().radiationMsvDay() == null) {
            return r;
        }
        EnvironmentPreset from = rules.environments().get(r.environmentId());
        EnvironmentPreset to = rules.environments().get(envId);
        if (from == null || from.radiationExpectedMsvDay() <= 0) {
            return r;
        }
        double scaled = r.habitat().radiationMsvDay() * to.radiationExpectedMsvDay() / from.radiationExpectedMsvDay();
        return r.withHabitat(new Habitat(PyFormat.round(scaled, 3), r.habitat().cabinCo2MmHg()));
    }

    private static String displayName(String astronautId) {
        String s = astronautId.startsWith("ast_") ? astronautId.substring(4) : astronautId;
        return s.isEmpty() ? astronautId : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
