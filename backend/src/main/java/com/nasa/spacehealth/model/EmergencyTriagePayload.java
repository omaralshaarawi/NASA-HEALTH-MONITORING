package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record EmergencyTriagePayload(
        String schemaVersion,
        String incidentId,
        String startedAtIso,
        String scenario,
        String patientId,
        String reporterId,
        Environment environment,
        PatientSnapshot patientSnapshot,
        ProtocolRef protocol,
        State state,
        List<String> agentRules,
        // only set once the incident has ended
        @JsonInclude(JsonInclude.Include.NON_NULL) StoreAndForward storeAndForward,
        String disclaimer) {

    public record Environment(String id, String label, double gravityG, double oneWayDelayS, CommMode commMode) {
    }

    public record PatientSnapshot(
            int missionDay,
            Status overall,
            List<RedIndicator> redIndicators,
            DailyMeasurementAndCompliance.Vitals latestVitals,
            List<DailyMeasurementAndCompliance.Attachment> recentAttachments) {
    }

    public record RedIndicator(String id, Double value, String unit, List<String> reasons) {
    }

    public record ProtocolRef(String protocolId, String version) {
    }

    public record State(String currentStepId, List<StepLogEntry> stepLog, String status) {
    }

    public record StepLogEntry(String stepId, String atIso,
                               @JsonInclude(JsonInclude.Include.NON_NULL) String answer) {
    }

    public record StoreAndForward(
            String packetId,
            String createdAtIso,
            String delivery,
            String estimatedEarthReceiptIso,
            String summary,
            List<DailyMeasurementAndCompliance.Attachment> attachments) {
    }
}
