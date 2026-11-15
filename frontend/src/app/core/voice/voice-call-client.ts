import { InjectionToken } from '@angular/core';

import { environment } from '../../../environments/environment';

export type ClientStatus = 'connecting' | 'listening' | 'thinking' | 'speaking' | 'disconnected';

// interim lines are resent with more text until final is true
export interface CallLine {
  speaker: 'JARVIS' | 'YOU';
  text: string;
  final: boolean;
}

export interface VoiceCallListener {
  status(status: ClientStatus): void;
  transcript(lines: CallLine[]): void;
}

export interface VoiceCallClient {
  checkMicrophone(): Promise<void>;
  join(joinUrl: string, listener: VoiceCallListener): void;
  setMuted(muted: boolean): void;
  // safe to call twice
  leave(): Promise<void>;
}

export type VoiceCallClientFactory = () => Promise<VoiceCallClient>;

// lazy, so the Ultravox SDK only downloads when a call starts
export const VOICE_CALL_CLIENT = new InjectionToken<VoiceCallClientFactory>('voice call client', {
  providedIn: 'root',
  factory: () => async () =>
    environment.useFakeVoice
      ? new (await import('./fake-voice-call-client')).FakeVoiceCallClient()
      : new (await import('./ultravox-voice-call-client')).UltravoxVoiceCallClient(),
});
