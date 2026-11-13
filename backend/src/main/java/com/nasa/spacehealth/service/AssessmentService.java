package com.nasa.spacehealth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.dto.AstronautSummary;
import com.nasa.spacehealth.engine.EnvironmentResolver;
import com.nasa.spacehealth.engine.PyFormat;
import com.nasa.spacehealth.engine.StatusEngine;
import com.nasa.spacehealth.exception.ApiException;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance.Habitat;
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.repository.MeasurementRepository;
import com.nasa.spacehealth.rules.Rules;
import com.nasa.spacehealth.rules.Rules.EnvironmentPreset;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class AssessmentService {

    private record Crew(String name, String scenario) {
    }

    // The three demo astronauts from seed.json
    private static final Map<String, Crew> SEEDED_CREW = Map.of(
            "ast_nova", new Crew("Nova", "Nominal: all systems inside the personal baseline"),
            "ast_kai", new Crew("Kai", "Multi-system drift from day 8, solar particle event on days 13-14"),
            "ast_lin", new Crew("Lin", "Acute chest-pain emergency on day 14"));

    private final MeasurementRepository repository;
    private final StatusEngine engine;
    private final EnvironmentResolver environments;
    private final SchemaValidator schemaValidator;
    private final Rules rules;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AssessmentService(MeasurementRepository repository, StatusEngine engine, EnvironmentResolver environments,
                             SchemaValidator schemaValidator, Rules rules, ObjectMapper mapper, Clock clock) {
        this.repository = repository;
        this.engine = engine;
        this.environments = environments;
        this.schemaValidator = schemaValidator;
        this.rules = rules;
        this.mapper = mapper;
        this.clock = clock;
    }

    public String nowIso() {
        return Instant.now(clock).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public List<AstronautSummary> getAstronauts() {
        return repository.astronautIds().stream().map(id -> {
            AssessmentResponse latest = assess(id, null, null, null);
            Crew crew = SEEDED_CREW.getOrDefault(id, new Crew(nameFromId(id), "Live data"));
            return new AstronautSummary(id, crew.name(), crew.scenario(), latest.missionDay(),
                    latest.environment().id(), latest.overall(), latest.triageRecommended());
        }).toList();
    }

    public List<EnvironmentProfile> getEnvironments() {
        return rules.environments().entrySet().stream()
                .map(e -> environments.profile(e.getKey(), null, e.getValue().radiationExpectedMsvDay(),
                        e.getValue().cabinCo2DefaultMmHg()))
                .toList();
    }

    // day: null = latest day. envId: null = the habitat that day was recorded in.
    public AssessmentResponse assess(String astronautId, Integer day, String envId, Double distanceKm) {
        int missionDay = day != null ? day : repository.latestDay(astronautId);
        List<DailyMeasurementAndCompliance> records = repository.findUpTo(astronautId, missionDay);
        String env = envId == null || envId.isBlank() ? records.get(records.size() - 1).environmentId() : envId;
        if (!rules.environments().containsKey(env)) {
            throw ApiException.badRequest("Unknown environment '" + env + "'. Valid: " + rules.environments().keySet());
        }

        List<DailyMeasurementAndCompliance> viewed = records.stream().map(r -> rescaleRadiation(r, env)).toList();
        Long km = distanceKm == null ? null : Math.round(distanceKm);
        return engine.assess(viewed, env, km, nowIso());
    }

    public AssessmentResponse addMeasurement(JsonNode body) {
        List<String> errors = schemaValidator.validate("DailyMeasurementAndCompliance", body);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("Body is not a valid DailyMeasurementAndCompliance: "
                    + String.join("; ", errors));
        }
        JsonNode day = body.get("mission_day");
        if (!day.isIntegralNumber() || day.asInt() < 1) {
            throw ApiException.badRequest("mission_day must be a whole number >= 1");
        }

        DailyMeasurementAndCompliance record = mapper.convertValue(body, DailyMeasurementAndCompliance.class);
        repository.save(record);
        return assess(record.astronautId(), record.missionDay(), record.environmentId(), null);
    }

    public int reset() {
        return repository.reset();
    }

    // A dose rate only makes sense next to the habitat it was measured in (1.4 mSv/day is normal in deep
    // space but 3.5x the station's level). When looking at a day from another habitat, scale the dose so
    // the ratio to the expected rate stays the same. Days viewed in their own habitat are not touched.
    private DailyMeasurementAndCompliance rescaleRadiation(DailyMeasurementAndCompliance r, String envId) {
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

    private static String nameFromId(String astronautId) {
        String name = astronautId.startsWith("ast_") ? astronautId.substring(4) : astronautId;
        return name.isEmpty() ? astronautId : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
