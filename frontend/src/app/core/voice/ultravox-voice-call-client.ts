import { UltravoxSession } from 'ultravox-client';

import { CallLine, ClientStatus, VoiceCallClient, VoiceCallListener } from './voice-call-client';

// 'disconnecting' is skipped: 'disconnected' follows right after
const STATUS: Record<string, ClientStatus | undefined> = {
  connecting: 'connecting',
  idle: 'listening',
  listening: 'listening',
  thinking: 'thinking',
  speaking: 'speaking',
  disconnected: 'disconnected',
};

export class UltravoxVoiceCallClient implements VoiceCallClient {
  private session: UltravoxSession | null = null;
  private stopListening: (() => void) | null = null;

  async checkMicrophone(): Promise<void> {
    // ask up front so a refusal shows before the call is created
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => track.stop());
  }

  join(joinUrl: string, listener: VoiceCallListener): void {
    const session = new UltravoxSession();

    const onStatus = () => {
      const status = STATUS[session.status];
      if (status) listener.status(status);
    };
    const onTranscripts = () => {
      listener.transcript(
        session.transcripts.map((t): CallLine => ({
          speaker: t.speaker === 'agent' ? 'JARVIS' : 'YOU',
          text: t.text,
          final: t.isFinal,
        })),
      );
    };
    session.addEventListener('status', onStatus);
    session.addEventListener('transcripts', onTranscripts);

    this.session = session;
    this.stopListening = () => {
      session.removeEventListener('status', onStatus);
      session.removeEventListener('transcripts', onTranscripts);
    };
    session.joinCall(joinUrl);
  }

  setMuted(muted: boolean): void {
    if (muted) this.session?.muteMic();
    else this.session?.unmuteMic();
  }

  async leave(): Promise<void> {
    const session = this.session;
    this.stopListening?.();
    this.stopListening = null;
    this.session = null;
    await session?.leaveCall();
  }
}
