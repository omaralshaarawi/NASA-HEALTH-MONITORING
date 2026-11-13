package com.nasa.spacehealth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.AstronautSummary;
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.service.AssessmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AssessmentController {

    private final AssessmentService service;

    public AssessmentController(AssessmentService service) {
        this.service = service;
    }

    /** Nova, Kai, Lin (plus anyone added through POST /api/measurements) with their current status. */
    @GetMapping("/astronauts")
    public List<AstronautSummary> astronauts() {
        return service.astronauts();
    }

    /** The six habitat presets from rules.json with computed one_way_delay_s and comm_mode. */
    @GetMapping("/environments")
    public List<EnvironmentProfile> environments() {
        return service.environments();
    }

    /**
     * GET /api/assessments/ast_kai?day=14&envId=DEEP_SPACECRAFT&distanceKm=225000000
     * All three query parameters are optional: latest day, the habitat that day was recorded in, default distance.
     */
    @GetMapping("/assessments/{astronautId}")
    public AssessmentResponse assessment(@PathVariable String astronautId,
                                         @RequestParam(required = false) Integer day,
                                         @RequestParam(required = false) String envId,
                                         @RequestParam(required = false) Double distanceKm) {
        return service.assess(astronautId, day, envId, distanceKm);
    }

    /** Body: DailyMeasurementAndCompliance. Replaces that mission day or appends the next one. */
    @PostMapping("/measurements")
    public AssessmentResponse measurement(@RequestBody JsonNode body) {
        return service.record(body);
    }

    /** Demo helper: forget everything posted and reload seed.json. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return Map.of("seed_records", service.reset());
    }
}
