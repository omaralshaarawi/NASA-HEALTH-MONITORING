import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { UltravoxSession } from 'ultravox-client';

import { environment } from '../../environments/environment';

// n8n workflow that opens the triage incident and creates the Ultravox call (see voice-n8n/README.md)
export const JARVIS_START_URL = environment.jarvisStartUrl;

export type VoiceStatus = 'off' | 'connecting' | 'listening' | 'thinking' | 'speaking' | 'ended' | 'failed';

export interface VoiceCallRequest {
  astronaut_id: string;
  environment_id: string;
  distance_km: number;
}

interface JarvisStartReply {
  joinUrl: string;
  callId: string;
  incident_id: string;
}

// The JARVIS voice call. One call at a time; the page shows status and the last spoken line.
@Injectable({ providedIn: 'root' })
export class VoiceOperatorService {
  private readonly http = inject(HttpClient);
  private session: UltravoxSession | null = null;

  readonly status = signal<VoiceStatus>('off');
  readonly muted = signal(false);
  readonly lastLine = signal<{ speaker: 'JARVIS' | 'Crew'; text: string } | null>(null);

  // Resolves with the incident id as soon as the call is created; audio connects in the background.
  async start(request: VoiceCallRequest): Promise<string> {
    await this.hangUp();
    this.status.set('connecting');
    this.lastLine.set(null);
    this.muted.set(false);
    try {
      const reply = await firstValueFrom(
        this.http.post<JarvisStartReply>(JARVIS_START_URL, { ...request, patient_id: request.astronaut_id }),
      );
      if (!reply?.joinUrl || !reply.incident_id) throw new Error('no join link in the reply');
      this.join(reply.joinUrl);
      return reply.incident_id;
    } catch (error) {
      this.status.set('failed');
      throw error;
    }
  }

  toggleMute(): void {
    if (!this.session) return;
    this.session.toggleMicMute();
    this.muted.set(this.session.isMicMuted);
  }

  async hangUp(): Promise<void> {
    const session = this.session;
    this.session = null;
    if (!session) return;
    try {
      await session.leaveCall();
    } catch {
      // already gone
    }
    this.status.set('ended');
  }

  private join(joinUrl: string): void {
    const session = new UltravoxSession();
    this.session = session;

    session.addEventListener('status', () => {
      if (this.session !== session) return;
      const status = session.status as string;
      if (status === 'disconnected') {
        this.session = null;
        this.status.set('ended');
      } else if (status === 'listening' || status === 'thinking' || status === 'speaking') {
        this.status.set(status);
      } else if (status === 'idle') {
        this.status.set('listening');
      }
    });

    session.addEventListener('transcripts', () => {
      const last = session.transcripts.at(-1);
      if (last?.text) {
        this.lastLine.set({ speaker: last.speaker === 'agent' ? 'JARVIS' : 'Crew', text: last.text });
      }
    });

    session.joinCall(joinUrl);
  }
}
