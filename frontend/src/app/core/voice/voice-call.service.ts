import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, TimeoutError, firstValueFrom, map, timeout } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ApiService, TriageStartRequest } from '../api.service';
import { CallLine, ClientStatus, VOICE_CALL_CLIENT, VoiceCallClient } from './voice-call-client';

export type CallPhase =
  'idle' | 'connecting' | 'listening' | 'thinking' | 'speaking' | 'ended' | 'error';
export type CallError = 'microphone' | 'webhook' | 'timeout' | 'join' | 'lost';

export type CallRequest = Required<
  Pick<TriageStartRequest, 'patient_id' | 'environment_id' | 'distance_km'>
>;

interface StartReply {
  joinUrl: string;
  incident_id: string;
}

const WEBHOOK_TIMEOUT_MS = 15_000;
const LIVE: CallPhase[] = ['connecting', 'listening', 'thinking', 'speaking'];

// App-wide so the call survives page changes. Transcripts stay in memory only.
@Injectable({ providedIn: 'root' })
export class VoiceCallService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private readonly clientFactory = inject(VOICE_CALL_CLIENT);
  private client: VoiceCallClient | null = null;
  // bumped on every start/end so late answers from an old attempt are ignored
  private attempt = 0;
  private starting: Promise<string | null> | null = null;

  readonly phase = signal<CallPhase>('idle');
  readonly error = signal<CallError | null>(null);
  readonly muted = signal(false);
  readonly lines = signal<CallLine[]>([]);
  readonly incidentId = signal<string | null>(null);
  readonly patientId = signal<string | null>(null);

  readonly active = computed(() => LIVE.includes(this.phase()));

  constructor() {
    inject(DestroyRef).onDestroy(() => void this.client?.leave());
  }

  // Resolves with the incident id, or null if setup failed (see error()).
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
    return reply.incident_id;
  }

  // simulated calls open the incident on the local backend and never touch n8n
  private requestCall(request: CallRequest): Observable<StartReply> {
    const body = { ...request, reporter_id: request.patient_id };
    if (environment.useFakeVoice) {
      return this.api
        .startTriage(body)
        .pipe(map((incident) => ({ joinUrl: 'simulated', incident_id: incident.incident_id })));
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
    // never connected, network dropped, or JARVIS hung up
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
