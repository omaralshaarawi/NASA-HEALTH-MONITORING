package com.nasa.spacehealth.service;

import com.nasa.spacehealth.dto.EndTriageRequest;
import com.nasa.spacehealth.dto.StartTriageRequest;
import com.nasa.spacehealth.dto.TriageStepRequest;
import com.nasa.spacehealth.engine.PyFormat;
import com.nasa.spacehealth.exception.ApiException;
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
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.model.TriageProtocol;
import com.nasa.spacehealth.model.TriageStep;
import com.nasa.spacehealth.repository.MeasurementRepository;
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

// Walks an incident through triage_protocol.json. The server keeps track of the current step;
// the client can only answer it, and the step's "next" map decides where to go.
@Service
public class TriageService {

    private static final int ATTACHMENT_DAYS = 7;

    static final List<String> AGENT_RULES = List.of(
            "Speak each step's script verbatim. Do not paraphrase, shorten, add to or skip a script.",
            "Move only along the current step's 'next' map: yes or no for ASK_YES_NO, done for every other step.",
            "Never give a diagnosis and never estimate how serious the condition is.",
            "Never name, choose or dose a medication. Refer the crew to the onboard checklist.",
            "Never state a number that is not in patient_snapshot or in a script.",
            "If an answer is unclear, repeat the current script. Do not guess the answer.",
            "The crew can end the protocol at any time. Stop as soon as they ask.",
            "This is decision support on simulated data. It does not replace onboard medical procedures.");

    private static class Incident {
        String id;
        String startedAt;
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
    private final AssessmentService assessmentService;
    private final MeasurementRepository repository;
    private final Map<String, Incident> incidents = new ConcurrentHashMap<>();

    public TriageService(TriageProtocol protocol, AssessmentService assessmentService,
                         MeasurementRepository repository) {
        this.protocol = protocol;
        this.assessmentService = assessmentService;
        this.repository = repository;
        protocol.steps().forEach(s -> steps.put(s.id(), s));
        if (!steps.containsKey(protocol.start())) {
            throw new IllegalStateException("triage_protocol.json: start step '" + protocol.start() + "' not found");
        }
    }

    public TriageProtocol getProtocol() {
        return protocol;
    }

    public EmergencyTriagePayload start(StartTriageRequest req) {
        if (req == null || isBlank(req.patientId())) {
            throw ApiException.badRequest("patient_id is required");
        }
        AssessmentResponse assessment = assessmentService.assess(req.patientId(), req.missionDay(),
                req.environmentId(), req.distanceKm());
        List<DailyMeasurementAndCompliance> records = repository.findUpTo(req.patientId(), assessment.missionDay());

        Incident inc = new Incident();
        inc.id = "inc_" + UUID.randomUUID().toString().substring(0, 8);
        inc.startedAt = assessmentService.nowIso();
        inc.patientId = req.patientId();
        inc.reporterId = isBlank(req.reporterId()) ? req.patientId() : req.reporterId();
        EnvironmentProfile env = assessment.environment();
        inc.environment = new EmergencyTriagePayload.Environment(env.id(), env.label(), env.gravityG(),
                env.oneWayDelayS(), env.commMode());
        inc.snapshot = snapshot(assessment, records);
        inc.currentStepId = protocol.start();

        incidents.put(inc.id, inc);
        return toPayload(inc);
    }

    public EmergencyTriagePayload get(String incidentId) {
        Incident inc = findIncident(incidentId);
        synchronized (inc) {
            return toPayload(inc);
        }
    }

    public EmergencyTriagePayload answer(TriageStepRequest req) {
        Incident inc = findIncident(req == null ? null : req.incidentId());
        synchronized (inc) {
            if (inc.ended) {
                throw ApiException.conflict("Incident " + inc.id + " has already ended");
            }
            TriageStep current = steps.get(inc.currentStepId);
            if (req.stepId() != null && !req.stepId().equals(current.id())) {
                throw ApiException.conflict("Out of sync: the incident is at step " + current.id()
                        + ", not " + req.stepId());
            }

            Map<String, String> next = current.next() == null ? Map.of() : current.next();
            String answer = req.answer() == null ? "" : req.answer().trim().toLowerCase();
            String nextId = next.get(answer);
            if (nextId == null) {
                throw ApiException.badRequest("Step " + current.id() + " (" + current.type() + ") accepts answer "
                        + String.join(" or ", next.keySet()) + ", got '" + req.answer() + "'");
            }
            TriageStep target = steps.get(nextId);
            if (target == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "triage_protocol.json: step " + current.id() + " points to unknown step " + nextId);
            }

            String now = assessmentService.nowIso();
            inc.log.add(new StepLogEntry(current.id(), now, answer));
            inc.currentStepId = target.id();
            if (target.endsProtocol()) {
                inc.log.add(new StepLogEntry(target.id(), now, null));
                close(inc, now, "protocol completed");
            }
            return toPayload(inc);
        }
    }

    // The crew can stop at any step (this is also the only way out of the CPR loop). Calling it twice is fine.
    public EmergencyTriagePayload end(EndTriageRequest req) {
        Incident inc = findIncident(req == null ? null : req.incidentId());
        synchronized (inc) {
            if (!inc.ended) {
                TriageStep current = steps.get(inc.currentStepId);
                String now = assessmentService.nowIso();
                inc.log.add(new StepLogEntry(current.id(), now, null));
                String reason = isBlank(req.reason()) ? "" : ": " + req.reason().trim();
                close(inc, now, "ended manually by the crew at step " + current.id()
                        + " (" + current.checklistLabel() + ")" + reason);
            }
            return toPayload(inc);
        }
    }

    private PatientSnapshot snapshot(AssessmentResponse assessment, List<DailyMeasurementAndCompliance> records) {
        List<RedIndicator> red = assessment.indicators().stream()
                .filter(i -> i.status() == Status.RED)
                .map(i -> new RedIndicator(i.id(), i.value(), i.unit(), i.reasons()))
                .toList();
        List<Attachment> attachments = records.stream()
                .skip(Math.max(0, records.size() - ATTACHMENT_DAYS))
                .filter(r -> r.attachments() != null)
                .flatMap(r -> r.attachments().stream())
                .toList();
        DailyMeasurementAndCompliance latest = records.get(records.size() - 1);
        return new PatientSnapshot(assessment.missionDay(), assessment.overall(), red, latest.vitals(), attachments);
    }

    private Incident findIncident(String incidentId) {
        if (isBlank(incidentId)) {
            throw ApiException.badRequest("incident_id is required");
        }
        Incident inc = incidents.get(incidentId);
        if (inc == null) {
            throw ApiException.notFound("Unknown incident '" + incidentId + "'");
        }
        return inc;
    }

    // Builds the store-and-forward packet. Earth gets it one light-delay after it is created.
    private void close(Incident inc, String now, String outcome) {
        inc.ended = true;
        boolean autonomous = inc.environment.commMode() == CommMode.AUTONOMOUS;
        Instant receipt = Instant.parse(now).plusMillis(Math.round(inc.environment.oneWayDelayS() * 1000.0));
        inc.packet = new StoreAndForward(
                "pkt_" + inc.id.substring("inc_".length()),
                now,
                autonomous ? "QUEUED_FOR_UPLINK" : "SENT_LIVE",
                receipt.toString(),
                summary(inc, outcome, autonomous),
                inc.snapshot.recentAttachments());
    }

    private String summary(Incident inc, String outcome, boolean autonomous) {
        String red = inc.snapshot.redIndicators().isEmpty() ? "none" : inc.snapshot.redIndicators().stream()
                .map(r -> r.id() + " " + (r.value() == null ? "no reading" : PyFormat.general(r.value()) + " " + r.unit())
                        + " [" + String.join("; ", r.reasons()) + "]")
                .collect(Collectors.joining(", "));
        String path = inc.log.stream()
                .map(e -> e.answer() == null ? e.stepId() : e.stepId() + "=" + e.answer())
                .collect(Collectors.joining(" > "));

        return protocol.scenario() + " incident " + inc.id + ". Patient " + inc.patientId
                + ", reported by " + inc.reporterId
                + ", mission day " + inc.snapshot.missionDay()
                + ", overall status " + inc.snapshot.overall()
                + ". RED indicators: " + red
                + ". Steps: " + path
                + ". Outcome: " + outcome
                + ". Comms: " + inc.environment.commMode()
                + ", one-way delay " + PyFormat.general(inc.environment.oneWayDelayS()) + " s, "
                + (autonomous ? "packet queued for uplink" : "packet sent live")
                + ". Simulated data; decision support only.";
    }

    private EmergencyTriagePayload toPayload(Incident inc) {
        return new EmergencyTriagePayload(
                Contract.SCHEMA_VERSION,
                inc.id,
                inc.startedAt,
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
