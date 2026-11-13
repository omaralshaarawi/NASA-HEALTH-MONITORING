package com.nasa.spacehealth.service;

import com.nasa.spacehealth.controller.ApiException;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.CommMode;
import com.nasa.spacehealth.model.Contract;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance.Attachment;
import com.nasa.spacehealth.model.EmergencyTriagePayload;
import com.nasa.spacehealth.model.EmergencyTriagePayload.PatientSnapshot;
import com.nasa.spacehealth.model.EmergencyTriagePayload.RedIndicator;
import com.nasa.spacehealth.model.EmergencyTriagePayload.StepLogEntry;
import com.nasa.spacehealth.model.EmergencyTriagePayload.StoreAndForward;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.model.TriageProtocol;
import com.nasa.spacehealth.model.TriageRequests;
import com.nasa.spacehealth.model.TriageStep;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Deterministic state machine over contract/triage_protocol.json.
 *
 * The server owns the position in the protocol: a client can only answer the current step, and the only way forward
 * is that step's `next` map. Nothing here is generated: scripts come from the protocol file, vitals from the store,
 * statuses and the light-time delay from the engine.
 */
@Service
public class TriageService {

    /** How many days back attachments count as "recent" for the snapshot and the packet. */
    private static final int ATTACHMENT_WINDOW_DAYS = 7;

    /** EmergencyTriagePayload.agent_rules: hard rules for anything (voice agent or screen) that presents the steps. */
    static final List<String> AGENT_RULES = List.of(
            "Speak each step's script verbatim. Do not paraphrase, shorten, add to or skip a script.",
            "Move only along the current step's 'next' map: yes or no for ASK_YES_NO, done for every other step.",
            "Never give a diagnosis and never estimate how serious the condition is.",
            "Never name, choose or dose a medication. Refer the crew to the onboard checklist.",
            "Never state a number that is not in patient_snapshot or in a script.",
            "If an answer is unclear, repeat the current script. Do not guess the answer.",
            "The crew can end the protocol at any time. Stop as soon as they ask.",
            "This is decision support on simulated data. It does not replace onboard medical procedures.");

    private static final class Incident {
        String id;
        String startedAtIso;
        String patientId;
        String reporterId;
        EmergencyTriagePayload.Environment environment;
        PatientSnapshot snapshot;
        String currentStepId;
        final List<StepLogEntry> log = new ArrayList<>();
        boolean ended;
        StoreAndForward packet;
    }

    private final TriageProtocol protocol;
    private final Map<String, TriageStep> steps = new LinkedHashMap<>();
    private final AssessmentService assessments;
    private final MeasurementStore store;
    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();

    public TriageService(TriageProtocol protocol, AssessmentService assessments, MeasurementStore store) {
        this.protocol = protocol;
        this.assessments = assessments;
        this.store = store;
        protocol.steps().forEach(s -> steps.put(s.id(), s));
        if (!steps.containsKey(protocol.start())) {
            throw new IllegalStateException("triage_protocol.json: start step '" + protocol.start() + "' does not exist");
        }
    }

    public TriageProtocol protocol() {
        return protocol;
    }

    public EmergencyTriagePayload start(TriageRequests.Start req) {
        if (req == null || req.patientId() == null || req.patientId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        AssessmentResponse a = assessments.assess(req.patientId(), req.missionDay(), req.environmentId(), req.distanceKm());
        List<DailyMeasurementAndCompliance> records = store.upTo(req.patientId(), a.missionDay());
        DailyMeasurementAndCompliance latest = records.get(records.size() - 1);

        Incident inc = new Incident();
        inc.id = "inc_" + UUID.randomUUID().toString().substring(0, 8);
        inc.startedAtIso = assessments.nowIso();
        inc.patientId = req.patientId();
        inc.reporterId = req.reporterId() == null || req.reporterId().isBlank() ? req.patientId() : req.reporterId();
        inc.environment = new EmergencyTriagePayload.Environment(a.environment().id(), a.environment().label(),
                a.environment().gravityG(), a.environment().oneWayDelayS(), a.environment().commMode());
        List<RedIndicator> red = a.indicators().stream()
                .filter(i -> i.status() == Status.RED)
                .map(i -> new RedIndicator(i.id(), i.value(), i.unit(), i.reasons()))
                .toList();
        List<Attachment> attachments = records.stream()
                .skip(Math.max(0, records.size() - ATTACHMENT_WINDOW_DAYS))
                .filter(r -> r.attachments() != null)
                .flatMap(r -> r.attachments().stream())
                .toList();
        inc.snapshot = new PatientSnapshot(a.missionDay(), a.overall(), red, latest.vitals(), attachments);
        inc.currentStepId = protocol.start();
        incidents.put(inc.id, inc);
        return payload(inc);
    }

    public EmergencyTriagePayload get(String incidentId) {
        Incident inc = incident(incidentId);
        synchronized (inc) {
            return payload(inc);
        }
    }

    /** Answer the current step and move to the step its `next` map names. Reaching a terminal step ends the incident. */
    public EmergencyTriagePayload step(TriageRequests.Step req) {
        Incident inc = incident(req == null ? null : req.incidentId());
        synchronized (inc) {
            if (inc.ended) {
                throw new ApiException(HttpStatus.CONFLICT, "Incident " + inc.id + " has already ended");
            }
            TriageStep current = steps.get(inc.currentStepId);
            if (req.stepId() != null && !req.stepId().equals(current.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "Out of sync: the incident is at step " + current.id()
                        + ", not " + req.stepId());
            }
            Map<String, String> next = current.next() == null ? Map.of() : current.next();
            String answer = req.answer() == null ? "" : req.answer().trim().toLowerCase();
            String nextId = next.get(answer);
            if (nextId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Step " + current.id() + " (" + current.type()
                        + ") accepts answer " + String.join(" or ", next.keySet()) + ", got '" + req.answer() + "'");
            }
            TriageStep target = steps.get(nextId);
            if (target == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "triage_protocol.json: step " + current.id()
                        + " points to unknown step " + nextId);
            }
            String now = assessments.nowIso();
            inc.log.add(new StepLogEntry(current.id(), now, answer));
            inc.currentStepId = target.id();
            if (target.endsProtocol()) {
                inc.log.add(new StepLogEntry(target.id(), now, null));
                finish(inc, now, "protocol completed");
            }
            return payload(inc);
        }
    }

    /**
     * Manual exit, allowed at any step (the CPR loop A3 -> A6 -> A3 has no other way out while the patient does not
     * recover). Builds the store-and-forward packet from whatever has been done so far. Idempotent.
     */
    public EmergencyTriagePayload end(TriageRequests.End req) {
        Incident inc = incident(req == null ? null : req.incidentId());
        synchronized (inc) {
            if (!inc.ended) {
                TriageStep current = steps.get(inc.currentStepId);
                String now = assessments.nowIso();
                inc.log.add(new StepLogEntry(current.id(), now, null));
                String reason = req.reason() == null || req.reason().isBlank() ? "" : ": " + req.reason().trim();
                finish(inc, now, "ended manually by the crew at step " + current.id() + " (" + current.checklistLabel()
                        + ")" + reason);
            }
            return payload(inc);
        }
    }

    // ---------------------------------------------------------------- internals

    private Incident incident(String incidentId) {
        if (incidentId == null || incidentId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "incident_id is required");
        }
        Incident inc = incidents.get(incidentId);
        if (inc == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Unknown incident '" + incidentId + "'");
        }
        return inc;
    }

    /** estimated_earth_receipt_iso = created_at + one_way_delay_s (light time, to the millisecond). */
    private void finish(Incident inc, String nowIso, String outcome) {
        inc.ended = true;
        boolean autonomous = inc.environment.commMode() == CommMode.AUTONOMOUS;
        Instant created = Instant.parse(nowIso);
        Instant receipt = created.plusMillis(Math.round(inc.environment.oneWayDelayS() * 1000.0));
        inc.packet = new StoreAndForward(
                "pkt_" + inc.id.substring(inc.id.indexOf('_') + 1),
                nowIso,
                autonomous ? "QUEUED_FOR_UPLINK" : "SENT_LIVE",
                receipt.toString(),
                summary(inc, outcome, autonomous),
                inc.snapshot.recentAttachments());
    }

    private String summary(Incident inc, String outcome, boolean autonomous) {
        String red = inc.snapshot.redIndicators().isEmpty() ? "none"
                : inc.snapshot.redIndicators().stream()
                .map(r -> r.id() + " " + (r.value() == null ? "no reading" : PyFormat.general(r.value()) + " " + r.unit())
                        + " [" + String.join("; ", r.reasons()) + "]")
                .collect(Collectors.joining(", "));
        String path = inc.log.stream()
                .map(e -> e.answer() == null ? e.stepId() : e.stepId() + "=" + e.answer())
                .collect(Collectors.joining(" > "));
        return protocol.scenario() + " incident " + inc.id + ". Patient " + inc.patientId + ", reported by "
                + inc.reporterId + ", mission day " + inc.snapshot.missionDay() + ", overall status "
                + inc.snapshot.overall() + ". RED indicators: " + red + ". Steps: " + path + ". Outcome: " + outcome
                + ". Comms: " + inc.environment.commMode() + ", one-way delay "
                + PyFormat.general(inc.environment.oneWayDelayS()) + " s, "
                + (autonomous ? "packet queued for uplink" : "packet sent live")
                + ". Simulated data; decision support only.";
    }

    private EmergencyTriagePayload payload(Incident inc) {
        return new EmergencyTriagePayload(
                Contract.SCHEMA_VERSION,
                inc.id,
                inc.startedAtIso,
                protocol.scenario(),
                inc.patientId,
                inc.reporterId,
                inc.environment,
                inc.snapshot,
                new EmergencyTriagePayload.ProtocolRef(protocol.protocolId(), protocol.version()),
                new EmergencyTriagePayload.State(inc.currentStepId, List.copyOf(inc.log),
                        inc.ended ? "ENDED" : "IN_PROGRESS"),
                AGENT_RULES,
                inc.packet,
                Contract.DISCLAIMER);
    }
}
