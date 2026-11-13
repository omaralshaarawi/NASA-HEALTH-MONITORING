package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** contract.ts section 5. Every value comes from the store or the engine, never from an LLM. */
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
        /** Built when the protocol ends (reached END, or ended manually). */
        @JsonInclude(JsonInclude.Include.NON_NULL) StoreAndForward storeAndForward,
        String disclaimer) {

    /** Pick<EnvironmentProfile, "id" | "label" | "gravity_g" | "one_way_delay_s" | "comm_mode"> */
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
            /** created_at + one_way_delay_s */
            String estimatedEarthReceiptIso,
            String summary,
            List<DailyMeasurementAndCompliance.Attachment> attachments) {
    }
}
