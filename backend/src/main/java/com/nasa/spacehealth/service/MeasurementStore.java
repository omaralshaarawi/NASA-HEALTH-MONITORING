package com.nasa.spacehealth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nasa.spacehealth.config.ContractFiles;
import com.nasa.spacehealth.controller.ApiException;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * In-memory measurement state, seeded from contract/seed.json. Nothing is persisted: a restart (or reset()) goes back
 * to the 42 seeded records.
 *
 * Invariant: each astronaut's days are contiguous 1..N, which is what the engine expects.
 */
@Component
public class MeasurementStore {

    private final ContractFiles files;
    private final Map<String, TreeMap<Integer, DailyMeasurementAndCompliance>> byAstronaut = new LinkedHashMap<>();

    public MeasurementStore(ContractFiles files) {
        this.files = files;
        reset();
    }

    /** Drop everything posted since start-up and reload seed.json. */
    public synchronized int reset() {
        byAstronaut.clear();
        List<DailyMeasurementAndCompliance> seed = files.read("seed.json", new TypeReference<>() {
        });
        for (DailyMeasurementAndCompliance r : seed) {
            byAstronaut.computeIfAbsent(r.astronautId(), k -> new TreeMap<>()).put(r.missionDay(), r);
        }
        return seed.size();
    }

    public synchronized List<String> astronautIds() {
        return new ArrayList<>(byAstronaut.keySet());
    }

    public synchronized boolean exists(String astronautId) {
        return byAstronaut.containsKey(astronautId);
    }

    public synchronized int latestDay(String astronautId) {
        return days(astronautId).lastKey();
    }

    /** Mission days 1..day, oldest first. */
    public synchronized List<DailyMeasurementAndCompliance> upTo(String astronautId, int day) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = days(astronautId);
        if (day < 1 || day > days.lastKey()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "day must be between 1 and " + days.lastKey()
                    + " for " + astronautId + " (got " + day + ")");
        }
        return new ArrayList<>(days.headMap(day, true).values());
    }

    /**
     * Replace an existing day or append the next one. A day that would leave a gap is refused, because the anchor
     * window and the drift slope both index by mission day.
     */
    public synchronized void upsert(DailyMeasurementAndCompliance record) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = byAstronaut.get(record.astronautId());
        int next = days == null ? 1 : days.lastKey() + 1;
        if (record.missionDay() > next) {
            throw new ApiException(HttpStatus.CONFLICT, "mission_day " + record.missionDay() + " would leave a gap for "
                    + record.astronautId() + ": the next day to log is " + next);
        }
        byAstronaut.computeIfAbsent(record.astronautId(), k -> new TreeMap<>()).put(record.missionDay(), record);
    }

    private TreeMap<Integer, DailyMeasurementAndCompliance> days(String astronautId) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = byAstronaut.get(astronautId);
        if (days == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown astronaut '" + astronautId + "'. Known: "
                    + byAstronaut.keySet());
        }
        return days;
    }
}
