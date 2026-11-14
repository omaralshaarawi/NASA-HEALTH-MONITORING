import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Subscription, catchError, interval, map, of, switchMap } from 'rxjs';

import { TopBarComponent } from '../../components/dashboard/top-bar.component';
import { CREW } from '../../core/crew';
import { ApiService, TriageAnswer, TriageIncident, TriageProtocol } from '../../core/api.service';
import { VoiceOperatorService } from '../../core/voice-operator.service';
import { MOCK_ASSESSMENTS } from '../../mocks/mock-data';
import { AssessmentResponse, EnvironmentProfile } from '../../models/contract';
import protocolJson from '../../models/triage_protocol.json';

const BUNDLED_PROTOCOL = protocolJson as TriageProtocol;

@Component({
  selector: 'app-triage-page',
  imports: [DatePipe, DecimalPipe, RouterLink, TopBarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './triage-page.component.html',
})
export class TriagePageComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly voice = inject(VoiceOperatorService);
  private incidentWatch: Subscription | null = null;

  protected readonly crew = CREW;
  protected readonly protocol = signal<TriageProtocol>(BUNDLED_PROTOCOL);
  protected readonly environments = signal<EnvironmentProfile[]>([]);
  protected readonly assessment = signal<AssessmentResponse | null>(null);
  protected readonly incident = signal<TriageIncident | null>(null);
  protected readonly patientId = signal('ast_lin');
  protected readonly environmentId = signal('SPACE_STATION');
  protected readonly distanceKm = signal(400);
  protected readonly missionDay = signal(14);
  protected readonly maxDay = computed(() => Math.max(14, this.missionDay(), this.assessment()?.mission_day ?? 14));
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly offline = signal(false);
  protected readonly error = signal('');
  protected readonly notice = signal('');
  protected readonly currentStep = computed(() => {
    const incident = this.incident();
    return incident ? this.protocol().steps.find((step) => step.id === incident.state.current_step_id) ?? null : null;
  });
  protected readonly ended = computed(() => this.incident()?.state.status === 'ENDED');
  protected readonly callLive = computed(() => ['connecting', 'listening', 'thinking', 'speaking'].includes(this.voice.status()));
  protected readonly callLabel = computed(() => {
    switch (this.voice.status()) {
      case 'connecting': return 'Connecting…';
      case 'listening': return this.voice.muted() ? 'Microphone muted' : 'Listening';
      case 'thinking': return 'Checking the protocol';
      case 'speaking': return 'Speaking';
      case 'failed': return 'Not connected';
      default: return 'Call ended';
    }
  });
  protected readonly environment = computed(
    () => this.environments().find((environment) => environment.id === this.environmentId()) ?? this.assessment()?.environment ?? null,
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.incidentWatch?.unsubscribe();
      void this.voice.hangUp();
    });

    const query = this.route.snapshot.queryParamMap;
    const patient = query.get('patient');
    if (patient && this.crew.some((member) => member.id === patient)) this.patientId.set(patient);
    const envId = query.get('env');
    if (envId) this.environmentId.set(envId);
    const distance = Number(query.get('distanceKm'));
    if (Number.isFinite(distance) && distance > 0) this.distanceKm.set(distance);
    const day = Number(query.get('day'));
    if (Number.isFinite(day) && day > 0) this.missionDay.set(day);

    this.api.triageProtocol().pipe(catchError(() => {
      this.offline.set(true);
      this.notice.set('Protocol service unavailable · using the bundled locked checklist.');
      return of(BUNDLED_PROTOCOL);
    })).subscribe((protocol) => {
      this.protocol.set(protocol);
      this.loadContext(query.get('incident'));
    });
  }

  protected patientChanged(event: Event): void {
    this.patientId.set((event.target as HTMLSelectElement).value);
    this.loadAssessment();
  }

  protected environmentChanged(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.environmentId.set(id);
    const selected = this.environments().find((environment) => environment.id === id);
    if (selected) this.distanceKm.set(selected.distance_km);
    this.loadAssessment();
  }

  protected distanceChanged(event: Event): void {
    this.distanceKm.set(Number((event.target as HTMLInputElement).value));
    this.loadAssessment();
  }

  protected dayChanged(event: Event): void {
    this.missionDay.set(Number((event.target as HTMLInputElement).value));
    this.loadAssessment();
  }

  protected start(): void {
    if (this.busy() || !this.assessment()) return;
    this.busy.set(true);
    this.error.set('');
    if (this.offline()) {
      this.useLocalIncident();
      return;
    }
    // JARVIS opens the incident and joins by voice; if that fails the checklist still starts on screen
    this.voice.start({
      astronaut_id: this.patientId(),
      environment_id: this.environmentId(),
      distance_km: this.distanceKm(),
    }).then(
      (incidentId) => this.followVoiceIncident(incidentId),
      () => {
        this.notice.set('Voice operator could not be reached · continue with the on-screen checklist.');
        this.startOnScreen();
      },
    );
  }

  protected hangUp(): void {
    void this.voice.hangUp();
  }

  private startOnScreen(): void {
    this.api.startTriage({
      patient_id: this.patientId(),
      reporter_id: this.patientId(),
      environment_id: this.environmentId(),
      distance_km: this.distanceKm(),
      mission_day: this.assessment()!.mission_day,
    }).subscribe({
      next: (incident) => {
        this.setIncident(incident);
        this.busy.set(false);
        this.writeIncidentToUrl(incident.incident_id);
      },
      error: (error: unknown) => {
        if (this.isNetworkError(error)) {
          this.offline.set(true);
          this.notice.set('Connection lost · continuing with the bundled protocol locally; no packet can be queued.');
          this.useLocalIncident();
          return;
        }
        this.error.set(this.errorMessage(error, 'Could not start the triage incident.'));
        this.busy.set(false);
      },
    });
  }

  protected answer(answer: TriageAnswer): void {
    const incident = this.incident();
    const step = this.currentStep();
    if (!incident || !step || this.busy() || this.ended()) return;
    this.error.set('');
    if (this.offline()) {
      this.applyLocalAnswer(answer);
      return;
    }
    this.busy.set(true);
    this.api.triageStep({ incident_id: incident.incident_id, answer, step_id: step.id }).subscribe({
      next: (updated) => {
        this.setIncident(updated);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        if (this.statusCode(error) === 409) {
          this.refreshIncident(incident.incident_id, 'The incident was refreshed after an out-of-sync response. Review the current step before continuing.');
          return;
        }
        if (this.isNetworkError(error)) {
          this.offline.set(true);
          this.notice.set('Connection lost · continuing locally; actions are not reaching Earth and no packet can be queued.');
          this.busy.set(false);
          this.applyLocalAnswer(answer);
          return;
        }
        this.error.set(this.errorMessage(error, 'Could not submit this step.'));
        this.busy.set(false);
      },
    });
  }

  protected endProtocol(): void {
    const incident = this.incident();
    if (!incident || this.busy() || this.ended()) return;
    this.error.set('');
    if (this.offline()) {
      this.finishLocally('ended manually by the crew');
      return;
    }
    this.busy.set(true);
    this.api.endTriage({ incident_id: incident.incident_id, reason: 'ended manually by the crew' }).subscribe({
      next: (updated) => {
        this.setIncident(updated);
        this.busy.set(false);
      },
      error: (error: unknown) => {
        if (this.statusCode(error) === 409) {
          this.refreshIncident(incident.incident_id, 'The incident state was refreshed.');
          return;
        }
        if (this.isNetworkError(error)) {
          this.offline.set(true);
          this.notice.set('Connection lost · ended locally; the incident packet cannot be queued from this device.');
          this.finishLocally('ended manually by the crew');
          return;
        }
        this.error.set(this.errorMessage(error, 'Could not end the incident.'));
        this.busy.set(false);
      },
    });
  }

  protected stepLabel(stepId: string): string {
    return this.protocol().steps.find((step) => step.id === stepId)?.checklist_label ?? stepId;
  }

  protected stepAnswer(stepId: string, answer?: string | null): string {
    return answer ? `${this.stepLabel(stepId)} · ${answer.toUpperCase()}` : this.stepLabel(stepId);
  }

  protected redIndicatorLabel(id: string): string {
    return id.replaceAll('_', ' ');
  }

  private loadContext(incidentId: string | null): void {
    this.api.environments().pipe(
      catchError(() => {
        this.offline.set(true);
        return of([] as EnvironmentProfile[]);
      }),
      switchMap((environments) => {
        this.environments.set(environments);
        const selected = environments.find((environment) => environment.id === this.environmentId());
        if (selected && !this.route.snapshot.queryParamMap.has('distanceKm')) {
          this.distanceKm.set(selected.distance_km);
        }
        return this.api.assessment(this.patientId(), {
          day: this.missionDay(),
          envId: this.environmentId(),
          distanceKm: this.distanceKm(),
        }).pipe(
          catchError(() => {
            this.offline.set(true);
            return of(MOCK_ASSESSMENTS[this.patientId()]);
          }),
          map((assessment) => ({ environments, assessment })),
        );
      }),
    ).subscribe(({ environments, assessment }) => {
      this.environments.set(environments);
      this.assessment.set(assessment);
      if (!environments.some((environment) => environment.id === this.environmentId())) {
        this.environmentId.set(assessment.environment.id);
      }
      this.distanceKm.set(assessment.environment.distance_km);
      this.missionDay.set(assessment.mission_day);
      this.loading.set(false);
      if (incidentId) this.loadExistingIncident(incidentId);
      else if (this.route.snapshot.queryParamMap.has('autostart')) this.start();
    });
  }

  // The call's incident lives on the backend; show it and keep the screen in step with the voice.
  private followVoiceIncident(incidentId: string): void {
    this.api.triageIncident(incidentId).subscribe({
      next: (incident) => {
        this.setIncident(incident);
        this.busy.set(false);
        this.writeIncidentToUrl(incident.incident_id);
        this.watchIncident(incident.incident_id);
      },
      error: () => {
        this.notice.set('JARVIS is on the call, but its incident is on another backend, so this screen runs its own checklist.');
        this.startOnScreen();
      },
    });
  }

  private watchIncident(incidentId: string): void {
    this.incidentWatch?.unsubscribe();
    this.incidentWatch = interval(2000).pipe(
      switchMap(() => this.api.triageIncident(incidentId).pipe(catchError(() => EMPTY))),
    ).subscribe((latest) => {
      const shown = this.incident();
      if (!shown || shown.incident_id !== incidentId || this.offline()) {
        this.incidentWatch?.unsubscribe();
        return;
      }
      const moved = latest.state.step_log.length !== shown.state.step_log.length || latest.state.status !== shown.state.status;
      if (moved && !this.busy()) this.setIncident(latest);
      if (latest.state.status === 'ENDED') this.incidentWatch?.unsubscribe();
    });
  }

  private loadAssessment(): void {
    this.loading.set(true);
    this.api.assessment(this.patientId(), {
      day: this.missionDay(),
      envId: this.environmentId(),
      distanceKm: this.distanceKm(),
    }).pipe(catchError(() => {
      this.offline.set(true);
      return of(MOCK_ASSESSMENTS[this.patientId()]);
    })).subscribe((assessment) => {
      this.assessment.set(assessment);
      this.environmentId.set(assessment.environment.id);
      this.loading.set(false);
    });
  }

  private loadExistingIncident(incidentId: string): void {
    if (incidentId.startsWith('local_')) {
      const saved = this.readStoredIncident(incidentId);
      this.offline.set(true);
      if (saved) {
        this.setIncident(saved);
      } else {
        this.incident.set(this.createLocalIncident(incidentId));
        this.persistIncident(this.incident()!);
      }
      this.notice.set('Local incident restored from this browser. The packet cannot be queued.');
      return;
    }
    this.api.triageIncident(incidentId).subscribe({
      next: (incident) => {
        const saved = this.readStoredIncident(incidentId);
        if (saved && saved.state.step_log.length > incident.state.step_log.length) {
          this.offline.set(true);
          this.setIncident(saved);
          this.notice.set('Restored local progress that was not synced to the server. No packet can be queued.');
        } else {
          this.setIncident(incident);
        }
      },
      error: () => {
        const saved = this.readStoredIncident(incidentId);
        this.offline.set(true);
        if (saved) {
          this.setIncident(saved);
        } else {
          this.incident.set(this.createLocalIncident(`local_${incidentId}`));
          this.persistIncident(this.incident()!);
          this.writeIncidentToUrl(this.incident()!.incident_id);
        }
        this.notice.set('Server incident unavailable · using the last saved browser state; no packet can be queued.');
      },
    });
  }

  private useLocalIncident(): void {
    const incident = this.createLocalIncident();
    this.setIncident(incident);
    this.busy.set(false);
    this.writeIncidentToUrl(incident.incident_id);
    this.notice.set('Local protocol mode · follow the checklist; this device cannot queue a packet.');
  }

  private createLocalIncident(id?: string): TriageIncident {
    const assessment = this.assessment() ?? MOCK_ASSESSMENTS[this.patientId()];
    const environment = this.environment() ?? assessment.environment;
    const incidentId = id?.startsWith('local_') ? id : `local_${Math.random().toString(36).slice(2, 10)}`;
    return {
      schema_version: assessment.schema_version,
      incident_id: incidentId,
      started_at_iso: new Date().toISOString(),
      scenario: this.protocol().scenario,
      patient_id: this.patientId(),
      reporter_id: this.patientId(),
      environment: {
        id: environment.id,
        label: environment.label,
        gravity_g: environment.gravity_g,
        one_way_delay_s: assessment.comms.one_way_delay_s,
        comm_mode: assessment.comms.mode,
      },
      patient_snapshot: {
        mission_day: assessment.mission_day,
        overall: assessment.overall,
        red_indicators: assessment.indicators.filter((indicator) => indicator.status === 'RED').map((indicator) => ({
          id: indicator.id,
          value: indicator.value,
          unit: indicator.unit,
          reasons: indicator.reasons,
        })),
        latest_vitals: null,
        recent_attachments: [],
      },
      protocol: { protocol_id: this.protocol().protocol_id, version: this.protocol().version },
      state: { current_step_id: this.protocol().start, step_log: [], status: 'IN_PROGRESS' },
      agent_rules: [],
      disclaimer: assessment.disclaimer,
    };
  }

  private applyLocalAnswer(answer: TriageAnswer): void {
    const incident = this.incident();
    const step = this.currentStep();
    const nextId = step?.next?.[answer];
    if (!incident || !step || !nextId) return;
    const atIso = new Date().toISOString();
    const log = [...incident.state.step_log, { step_id: step.id, at_iso: atIso, answer }];
    const nextStep = this.protocol().steps.find((item) => item.id === nextId);
    if (!nextStep) {
      this.error.set(`Bundled protocol step ${nextId} is missing.`);
      return;
    }
    let updated: TriageIncident = {
      ...incident,
      state: { current_step_id: nextStep.id, step_log: log, status: incident.state.status },
    };
    if (nextStep.terminal) {
      updated = {
        ...updated,
        state: { current_step_id: nextStep.id, step_log: [...log, { step_id: nextStep.id, at_iso: atIso }], status: 'ENDED' },
      };
      this.notice.set('Protocol completed locally. No incident packet was sent or queued.');
    }
    this.setIncident(updated);
  }

  private finishLocally(reason: string): void {
    const incident = this.incident();
    if (!incident) return;
    const step = this.currentStep();
    const entry = step ? [{ step_id: step.id, at_iso: new Date().toISOString() }] : [];
    this.setIncident({
      ...incident,
      state: { ...incident.state, status: 'ENDED', step_log: [...incident.state.step_log, ...entry] },
    });
    this.notice.set(`${reason}. This browser cannot create or queue the store-and-forward packet.`);
  }

  private refreshIncident(id: string, message: string): void {
    this.api.triageIncident(id).subscribe({
      next: (incident) => {
        this.setIncident(incident);
        this.error.set(message);
        this.busy.set(false);
      },
      error: () => {
        this.error.set(`${message} The server could not be reached to refresh it.`);
        this.busy.set(false);
      },
    });
  }

  private setIncident(incident: TriageIncident): void {
    this.incident.set(incident);
    this.patientId.set(incident.patient_id);
    this.environmentId.set(incident.environment.id);
    this.missionDay.set(incident.patient_snapshot.mission_day);
    this.persistIncident(incident);
  }

  private writeIncidentToUrl(id: string): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { incident: id, autostart: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private persistIncident(incident: TriageIncident): void {
    try {
      sessionStorage.setItem(`triage_${incident.incident_id}`, JSON.stringify(incident));
    } catch {
      this.notice.set('Browser storage is unavailable; this incident cannot be resumed after reload.');
    }
  }

  private readStoredIncident(id: string): TriageIncident | null {
    try {
      const value = sessionStorage.getItem(`triage_${id}`);
      return value ? JSON.parse(value) as TriageIncident : null;
    } catch {
      return null;
    }
  }

  private statusCode(error: unknown): number {
    return typeof error === 'object' && error !== null && 'status' in error ? Number((error as { status: number }).status) : 0;
  }

  private isNetworkError(error: unknown): boolean {
    const status = this.statusCode(error);
    return status === 0 || status >= 500;
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (typeof error === 'object' && error !== null && 'error' in error) {
      const body = (error as { error?: { message?: string; error?: string } }).error;
      return body?.message ?? body?.error ?? fallback;
    }
    return fallback;
  }
}