import { InjectionToken } from '@angular/core';

import { environment } from '../../../environments/environment';

export type ClientStatus = 'connecting' | 'listening' | 'thinking' | 'speaking' | 'disconnected';

// One utterance. While someone is still talking the same line arrives again with more text,
// and finally with final = true.
export interface CallLine {
  speaker: 'JARVIS' | 'YOU';
  text: string;
  final: boolean;
}

export interface VoiceCallListener {
  status(status: ClientStatus): void;
  // the whole conversation so far, oldest first
  transcript(lines: CallLine[]): void;
}

// What the call service needs from a voice provider. The real one talks to Ultravox;
// the fake one plays a short scripted call for tests and offline demos.
export interface VoiceCallClient {
  // rejects when the microphone cannot be used
  checkMicrophone(): Promise<void>;
  // throws when the call cannot be joined
  join(joinUrl: string, listener: VoiceCallListener): void;
  setMuted(muted: boolean): void;
  // stops reporting to the listener and leaves the call; safe to call twice
  leave(): Promise<void>;
}

export type VoiceCallClientFactory = () => Promise<VoiceCallClient>;

// The Ultravox SDK is large, so it is only downloaded when a call is actually started.
export const VOICE_CALL_CLIENT = new InjectionToken<VoiceCallClientFactory>('voice call client', {
  providedIn: 'root',
  factory: () => async () =>
    environment.useFakeVoice
      ? new (await import('./fake-voice-call-client')).FakeVoiceCallClient()
      : new (await import('./ultravox-voice-call-client')).UltravoxVoiceCallClient(),
});
