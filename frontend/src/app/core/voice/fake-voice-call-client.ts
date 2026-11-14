import { CallLine, ClientStatus, VoiceCallClient, VoiceCallListener } from './voice-call-client';

type Step = { at: number; status?: ClientStatus; line?: CallLine };

// A short scripted call. Each step fires this many milliseconds after joining.
const SCRIPT: Step[] = [
  { at: 800, status: 'speaking', line: { speaker: 'JARVIS', text: 'JARVIS here.', final: false } },
  { at: 1600, line: { speaker: 'JARVIS', text: 'JARVIS here. I am with you.', final: false } },
  { at: 2400, status: 'listening', line: { speaker: 'JARVIS', text: 'JARVIS here. I am with you. This is a simulated call.', final: true } },
  { at: 4000, line: { speaker: 'YOU', text: 'Yes', final: false } },
  { at: 4600, status: 'thinking', line: { speaker: 'YOU', text: 'Yes.', final: true } },
  { at: 5600, status: 'speaking', line: { speaker: 'JARVIS', text: 'Understood. Continue with the checklist on screen.', final: true } },
  { at: 7000, status: 'listening' },
];

// Simulated voice call: no network, no microphone. For unit tests, and for demos when
// environment.useFakeVoice is on.
export class FakeVoiceCallClient implements VoiceCallClient {
  // switches a test can flip before starting a call
  microphoneAllowed = true;
  joinFails = false;

  joined = 0;
  left = 0;
  muted = false;

  private listener: VoiceCallListener | null = null;
  private timers: Array<ReturnType<typeof setTimeout>> = [];
  private lines: CallLine[] = [];

  get listening(): boolean {
    return this.listener !== null;
  }

  checkMicrophone(): Promise<void> {
    return this.microphoneAllowed ? Promise.resolve() : Promise.reject(new Error('microphone not allowed'));
  }

  join(_joinUrl: string, listener: VoiceCallListener): void {
    if (this.joinFails) throw new Error('could not join');
    this.joined++;
    this.listener = listener;
    this.lines = [];
    listener.status('connecting');
    this.timers = SCRIPT.map((step) => setTimeout(() => this.play(step), step.at));
  }

  setMuted(muted: boolean): void {
    this.muted = muted;
  }

  leave(): Promise<void> {
    if (this.listener) this.left++;
    this.stop();
    return Promise.resolve();
  }

  // the far end going away: what a dropped connection or the agent hanging up looks like
  drop(): void {
    const listener = this.listener;
    this.stop();
    listener?.status('disconnected');
  }

  private play(step: Step): void {
    if (step.line) {
      const last = this.lines.at(-1);
      // an interim line is replaced by its next version; a final one stays
      if (last && !last.final && last.speaker === step.line.speaker) this.lines = [...this.lines.slice(0, -1), step.line];
      else this.lines = [...this.lines, step.line];
      this.listener?.transcript(this.lines);
    }
    if (step.status) this.listener?.status(step.status);
  }

  private stop(): void {
    this.timers.forEach(clearTimeout);
    this.timers = [];
    this.listener = null;
  }
}
