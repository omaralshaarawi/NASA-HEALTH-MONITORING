import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, output, signal, viewChild } from '@angular/core';

import { CallError, VoiceCallService } from '../../core/voice/voice-call.service';

type Look = 'connecting' | 'listening' | 'thinking' | 'speaking' | 'muted' | 'ended' | 'error';

const LABELS: Record<Look, string> = {
  connecting: 'Connecting',
  listening: 'Listening',
  thinking: 'Jarvis is thinking',
  speaking: 'Jarvis is speaking',
  muted: 'Mic muted',
  ended: 'Call ended',
  error: 'Connection error',
};

const PROBLEMS: Record<CallError, string> = {
  microphone:
    'The microphone could not be used. Allow it for this site in the browser (the padlock next to the address), then try again. The call only works on HTTPS or localhost.',
  webhook: 'The voice operator did not answer. Check the connection, then try again.',
  timeout: 'The voice operator took too long to answer. Try again.',
  join: 'The call was created but could not be joined. Try again.',
  lost: 'The connection was lost and the call dropped. Try again when the link is back.',
};

// The JARVIS voice call inside the triage page: what the call is doing, live subtitles,
// and the mute and end controls. The checklist below it works with or without the call.
@Component({
  selector: 'app-voice-call-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './voice-call-panel.component.html',
  styles: `
    .caption {
      min-height: 4.5rem;
      font-size: 1.3125rem;
      line-height: 1.45;
      color: var(--color-ink);
    }
    .who {
      margin-right: 0.625rem;
      font-size: 0.6875rem;
      letter-spacing: 0.12em;
      vertical-align: middle;
      color: var(--color-ink-mute);
    }
    .log {
      max-height: 11rem;
      overflow-y: auto;
      overscroll-behavior: contain;
    }
    .interim { color: var(--color-ink-mute); }
    @media (prefers-reduced-motion: no-preference) {
      .live { animation: live 1.4s ease-in-out infinite; }
      @keyframes live { 50% { opacity: 0.35; } }
    }
  `,
})
export class VoiceCallPanelComponent {
  protected readonly call = inject(VoiceCallService);

  // "Call again" and "Try again": the page starts a new call and follows its incident
  readonly callAgain = output<void>();

  protected readonly confirmingEnd = signal(false);
  private readonly log = viewChild<ElementRef<HTMLElement>>('log');
  // follow new lines until the reader scrolls up to look at older ones
  private following = true;

  protected readonly look = computed<Look>(() => {
    const phase = this.call.phase();
    if (phase === 'idle') return 'ended';
    return this.call.active() && this.call.muted() ? 'muted' : phase;
  });
  protected readonly label = computed(() => LABELS[this.look()]);
  protected readonly problem = computed(() => {
    const error = this.call.error();
    return error ? PROBLEMS[error] : '';
  });
  protected readonly current = computed(() => this.call.lines().at(-1) ?? null);

  constructor() {
    effect(() => {
      this.call.lines();
      const log = this.log()?.nativeElement;
      if (log && this.following) queueMicrotask(() => (log.scrollTop = log.scrollHeight));
    });
  }

  protected scrolled(log: HTMLElement): void {
    this.following = log.scrollHeight - log.scrollTop - log.clientHeight < 24;
  }

  // one extra tap, because ending the call in the middle of an emergency should not be an accident
  protected end(): void {
    if (!this.confirmingEnd()) {
      this.confirmingEnd.set(true);
      return;
    }
    this.confirmingEnd.set(false);
    void this.call.end();
  }
}
