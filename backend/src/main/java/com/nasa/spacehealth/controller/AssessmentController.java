package com.nasa.spacehealth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.dto.AstronautSummary;
import com.nasa.spacehealth.model.AssessmentResponse;
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

    private final AssessmentService assessmentService;

    public AssessmentController(AssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @GetMapping("/astronauts")
    public List<AstronautSummary> getAstronauts() {
        return assessmentService.getAstronauts();
    }

    @GetMapping("/environments")
    public List<EnvironmentProfile> getEnvironments() {
        return assessmentService.getEnvironments();
    }

    @GetMapping("/assessments/{astronautId}")
    public AssessmentResponse getAssessment(@PathVariable String astronautId,
                                            @RequestParam(required = false) Integer day,
                                            @RequestParam(required = false) String envId,
                                            @RequestParam(required = false) Double distanceKm) {
        return assessmentService.assess(astronautId, day, envId, distanceKm);
    }

    @PostMapping("/measurements")
    public AssessmentResponse addMeasurement(@RequestBody JsonNode body) {
        return assessmentService.addMeasurement(body);
    }

    // demo helper: drop posted data and reload seed.json
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return Map.of("seed_records", assessmentService.reset());
    }
}
