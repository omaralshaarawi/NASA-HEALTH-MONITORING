package com.nasa.spacehealth.controller;

import com.nasa.spacehealth.model.EmergencyTriagePayload;
import com.nasa.spacehealth.model.TriageProtocol;
import com.nasa.spacehealth.model.TriageRequests;
import com.nasa.spacehealth.service.TriageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Every route answers with the full EmergencyTriagePayload (contract.ts section 5). */
@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private final TriageService service;

    public TriageController(TriageService service) {
        this.service = service;
    }

    /** triage_protocol.json as loaded, so the checklist UI can render scripts and labels by step id. */
    @GetMapping("/protocol")
    public TriageProtocol protocol() {
        return service.protocol();
    }

    /** Body: {"patient_id": "ast_lin"} plus optional reporter_id, environment_id, distance_km, mission_day. */
    @PostMapping("/start")
    public EmergencyTriagePayload start(@RequestBody TriageRequests.Start body) {
        return service.start(body);
    }

    /** Body: {"incident_id": "...", "answer": "yes" | "no" | "done"} plus optional step_id. */
    @PostMapping("/step")
    public EmergencyTriagePayload step(@RequestBody TriageRequests.Step body) {
        return service.step(body);
    }

    /** Body: {"incident_id": "..."} plus optional reason. Manual exit at any step; builds store_and_forward. */
    @PostMapping("/end")
    public EmergencyTriagePayload end(@RequestBody TriageRequests.End body) {
        return service.end(body);
    }

    /** Current state of an incident (for a page reload). */
    @GetMapping("/{incidentId}")
    public EmergencyTriagePayload get(@PathVariable String incidentId) {
        return service.get(incidentId);
    }
}
