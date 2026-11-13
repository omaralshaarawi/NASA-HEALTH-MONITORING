package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class PlannerController {

    private final PlannerService plannerService;

    public PlannerController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    // body: the AssessmentResponse from GET /api/assessments/{id}
    @PostMapping("/generate")
    public CountermeasurePlan generate(@RequestBody JsonNode assessment,
                                       @RequestParam(required = false) Double massKg) {
        return plannerService.generate(assessment, massKg);
    }

    @GetMapping("/{astronautId}")
    public CountermeasurePlan getLatest(@PathVariable String astronautId) {
        return plannerService.latest(astronautId);
    }
}
