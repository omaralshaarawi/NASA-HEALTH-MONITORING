package com.nasa.spacehealth.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.planner.model.CountermeasurePlan;
import org.springframework.http.ResponseEntity;
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

    // tells the UI where the plan came from: SAVED, LIVE or OFFLINE
    public static final String SOURCE_HEADER = "X-Plan-Source";

    private final PlannerService plannerService;

    public PlannerController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    // body: the AssessmentResponse from GET /api/assessments/{id}. fresh=true skips saved plans and asks the LLM.
    @PostMapping("/generate")
    public ResponseEntity<CountermeasurePlan> generate(@RequestBody JsonNode assessment,
                                                       @RequestParam(required = false) Double massKg,
                                                       @RequestParam(defaultValue = "false") boolean fresh) {
        PlannerService.Result result = plannerService.generate(assessment, massKg, fresh);
        return ResponseEntity.ok().header(SOURCE_HEADER, result.source().name()).body(result.plan());
    }

    @GetMapping("/{astronautId}")
    public CountermeasurePlan getLatest(@PathVariable String astronautId) {
        return plannerService.latest(astronautId);
    }
}
