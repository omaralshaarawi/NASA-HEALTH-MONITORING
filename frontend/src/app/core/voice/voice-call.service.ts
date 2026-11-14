import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, TimeoutError, firstValueFrom, map, timeout } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ApiService, TriageStartRequest } from '../api.service';
import { CallLine, ClientStatus, VOICE_CALL_CLIENT, VoiceCallClient } from './voice-call-client';

export type CallPhase = 'idle' | 'connecting' | 'listening' | 'thinking' | 'speaking' | 'ended' | 'error';
export type CallError = 'microphone' | 'webhook' | 'timeout' | 'join' | 'lost';

// what the voice workflow needs to open the incident and create the call
export type CallRequest = Required<Pick<TriageStartRequest, 'patient_id' | 'environment_id' | 'distance_km'>>;

interface StartReply {
  joinUrl: string;
  incident_id: string;
}

const WEBHOOK_TIMEOUT_MS = 15_000;
const LIVE: CallPhase[] = ['connecting', 'listening', 'thinking', 'speaking'];

// The emergency voice call with JARVIS. It belongs to the app, not to a page, so it carries on
// while the crew member moves between pages. There is never more than one call.
//
// The conversation is kept in memory only: it is not stored and not logged.
@Injectable({ providedIn: 'root' })
export class VoiceCallService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly clientFactory = inject(VOICE_CALL_CLIENT);
  private client: VoiceCallClient | null = null;
  // goes up on every start and end, so answers that arrive for an older attempt are dropped
  private attempt = 0;
  private starting: Promise<string | null> | null = null;

  readonly phase = signal<CallPhase>('idle');
  readonly error = signal<CallError | null>(null);
  readonly muted = signal(false);
  readonly lines = signal<CallLine[]>([]);
  // the triage incident the call is working through, and who it is for
  readonly incidentId = signal<string | null>(null);
  readonly patientId = signal<string | null>(null);

  readonly active = computed(() => LIVE.includes(this.phase()));

  constructor() {
    inject(DestroyRef).onDestroy(() => void this.client?.leave());
  }

  // Opens the incident and joins the call. Resolves with the incident id as soon as there is one,
  // or null when the call could not be set up (the reason is in error()).
  // Asking again while a call is connecting or running changes nothing: there is never a second call.
  start(request: CallRequest): Promise<string | null> {
    if (this.starting) return this.starting;
    if (this.active()) return Promise.resolve(this.incidentId());
    this.starting = this.connect(request).finally(() => (this.starting = null));
    return this.starting;
  }

  private async connect(request: CallRequest): Promise<string | null> {
    const attempt = ++this.attempt;
    this.phase.set('connecting');
    this.error.set(null);
    this.muted.set(false);
    this.lines.set([]);
    this.incidentId.set(null);
    this.patientId.set(request.patient_id);

    let client: VoiceCallClient;
    try {
      client = await this.clientFactory();
    } catch {
      return this.fail(attempt, 'join');
    }
    try {
      await client.checkMicrophone();
    } catch {
      return this.fail(attempt, 'microphone');
    }
    if (attempt !== this.attempt) return null;

    let reply: StartReply;
    try {
      reply = await firstValueFrom(this.requestCall(request).pipe(timeout(WEBHOOK_TIMEOUT_MS)));
      if (!reply?.joinUrl || !reply.incident_id) throw new Error('no join link in the reply');
    } catch (error) {
      return this.fail(attempt, error instanceof TimeoutError ? 'timeout' : 'webhook');
    }
    if (attempt !== this.attempt) return null;

    this.incidentId.set(reply.incident_id);
    try {
      this.client = client;
      client.join(reply.joinUrl, {
        status: (status) => this.onStatus(attempt, status),
        transcript: (lines) => attempt === this.attempt && this.lines.set(lines),
      });
    } catch {
      this.fail(attempt, 'join');
    }
    // the incident exists either way, so the checklist can follow it
    return reply.incident_id;
  }

  // The n8n workflow opens the incident and creates the Ultravox call. A simulated call stays on this
  // machine: it opens the incident on the local backend and never reaches n8n or Ultravox.
  private requestCall(request: CallRequest): Observable<StartReply> {
    const body = { ...request, reporter_id: request.patient_id };
    if (environment.useFakeVoice) {
      return this.api.startTriage(body).pipe(map((incident) => ({ joinUrl: 'simulated', incident_id: incident.incident_id })));
    }
    return this.http.post<StartReply>(environment.jarvisStartUrl, body);
  }

  toggleMute(): void {
    if (!this.client || !this.active()) return;
    this.muted.update((muted) => !muted);
    this.client.setMuted(this.muted());
  }

  async end(): Promise<void> {
    if (!this.active()) return;
    this.attempt++;
    this.phase.set('ended');
    await this.release();
  }

  private onStatus(attempt: number, status: ClientStatus): void {
    if (attempt !== this.attempt) return;
    if (status !== 'disconnected') {
      this.phase.set(status);
      return;
    }
    // the far end went away: never connected, the network dropped, or JARVIS hung up
    if (this.phase() === 'connecting') this.fail(attempt, 'join');
    else if (!navigator.onLine) this.fail(attempt, 'lost');
    else {
      this.phase.set('ended');
      void this.release();
    }
  }

  private fail(attempt: number, error: CallError): null {
    if (attempt === this.attempt) {
      this.error.set(error);
      this.phase.set('error');
      void this.release();
    }
    return null;
  }

  private async release(): Promise<void> {
    const client = this.client;
    this.client = null;
    this.muted.set(false);
    try {
      await client?.leave();
    } catch {
      // already gone
    }
  }
}
