package com.nasa.spacehealth.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nasa.spacehealth.config.ContractFiles;
import com.nasa.spacehealth.exception.ApiException;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// In-memory store, filled from seed.json on start-up. Nothing is saved to disk.
// Each astronaut's days are kept contiguous (1..N) because the engine depends on that.
@Repository
public class MeasurementRepository {

    private final ContractFiles files;
    private final Map<String, TreeMap<Integer, DailyMeasurementAndCompliance>> data = new LinkedHashMap<>();

    public MeasurementRepository(ContractFiles files) {
        this.files = files;
        reset();
    }

    public synchronized int reset() {
        data.clear();
        List<DailyMeasurementAndCompliance> seed = files.read("seed.json", new TypeReference<>() {
        });
        seed.forEach(this::put);
        return seed.size();
    }

    public synchronized List<String> astronautIds() {
        return new ArrayList<>(data.keySet());
    }

    public synchronized int latestDay(String astronautId) {
        return daysOf(astronautId).lastKey();
    }

    // Days 1..day, oldest first
    public synchronized List<DailyMeasurementAndCompliance> findUpTo(String astronautId, int day) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = daysOf(astronautId);
        int last = days.lastKey();
        if (day < 1 || day > last) {
            throw ApiException.badRequest("day must be between 1 and " + last + " for " + astronautId
                    + " (got " + day + ")");
        }
        return new ArrayList<>(days.headMap(day, true).values());
    }

    // Replaces an existing day or adds the next one. Skipping days is not allowed.
    public synchronized void save(DailyMeasurementAndCompliance record) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = data.get(record.astronautId());
        int nextDay = days == null ? 1 : days.lastKey() + 1;
        if (record.missionDay() > nextDay) {
            throw ApiException.conflict("mission_day " + record.missionDay() + " would leave a gap for "
                    + record.astronautId() + ": the next day to log is " + nextDay);
        }
        put(record);
    }

    private void put(DailyMeasurementAndCompliance record) {
        data.computeIfAbsent(record.astronautId(), k -> new TreeMap<>()).put(record.missionDay(), record);
    }

    private TreeMap<Integer, DailyMeasurementAndCompliance> daysOf(String astronautId) {
        TreeMap<Integer, DailyMeasurementAndCompliance> days = data.get(astronautId);
        if (days == null) {
            throw ApiException.notFound("Unknown astronaut '" + astronautId + "'. Known: " + data.keySet());
        }
        return days;
    }
}
