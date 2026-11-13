package com.nasa.spacehealth.controller;

import com.nasa.spacehealth.dto.EndTriageRequest;
import com.nasa.spacehealth.dto.StartTriageRequest;
import com.nasa.spacehealth.dto.TriageStepRequest;
import com.nasa.spacehealth.model.EmergencyTriagePayload;
import com.nasa.spacehealth.model.TriageProtocol;
import com.nasa.spacehealth.service.TriageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @GetMapping("/protocol")
    public TriageProtocol getProtocol() {
        return triageService.getProtocol();
    }

    @PostMapping("/start")
    public EmergencyTriagePayload start(@RequestBody StartTriageRequest request) {
        return triageService.start(request);
    }

    @PostMapping("/step")
    public EmergencyTriagePayload step(@RequestBody TriageStepRequest request) {
        return triageService.answer(request);
    }

    @PostMapping("/end")
    public EmergencyTriagePayload end(@RequestBody EndTriageRequest request) {
        return triageService.end(request);
    }

    @GetMapping("/{incidentId}")
    public EmergencyTriagePayload getIncident(@PathVariable String incidentId) {
        return triageService.get(incidentId);
    }
}
