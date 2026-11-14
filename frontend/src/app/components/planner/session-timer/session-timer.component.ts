import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, inject, input, signal, viewChild } from '@angular/core';

import { TrainingService } from '../../../core/training.service';

const STATE_WORDS = { idle: 'Not started', running: 'Running', paused: 'Paused', done: 'Completed' };

// Countdown and controls for one training session. All state lives in TrainingService.
@Component({
  selector: 'app-session-timer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './session-timer.component.html',
  styles: `
    .track {
      height: 0.25rem;
      overflow: hidden;
      border-radius: 999px;
      background: rgb(255 255 255 / 0.1);
    }
    .fill {
      height: 100%;
      border-radius: 999px;
      background: var(--color-signal);
      transition: width 400ms linear;
    }
  `,
})
export class SessionTimerComponent {
  protected readonly training = inject(TrainingService);
  private readonly injector = inject(Injector);
  // the first button of whatever state the timer is in
  private readonly main = viewChild<ElementRef<HTMLButtonElement>>('main');

  readonly exerciseId = input.required<string>();
  readonly minutes = input.required<number>();
  // the flight surgeon sets and times this protocol: ask before starting
  readonly needsDirection = input(false);

  protected readonly confirming = signal(false);

  protected readonly state = computed(() => this.training.stateOf(this.exerciseId()));
  protected readonly stateWord = computed(() => STATE_WORDS[this.state()]);
  protected readonly blocked = computed(() => this.training.blockedReason(this.exerciseId()));

  protected readonly percent = computed(() => {
    const total = this.minutes() * 60_000;
    return total ? Math.round((this.training.elapsedMs(this.exerciseId()) / total) * 100) : 0;
  });

  // mm:ss left on the clock; once the session is done, the time that was trained
  protected readonly clock = computed(() => {
    const id = this.exerciseId();
    const ms = this.state() === 'done' ? this.training.elapsedMs(id) : this.training.remainingMs(id);
    const seconds = Math.ceil(ms / 1000);
    return String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0');
  });

  protected start(): void {
    if (this.needsDirection() && !this.confirming()) {
      this.confirming.set(true);
    } else {
      this.confirming.set(false);
      this.training.start(this.exerciseId());
    }
    this.keepFocus();
  }

  protected cancelStart(): void {
    this.confirming.set(false);
    this.keepFocus();
  }

  protected act(action: (exerciseId: string) => void): void {
    action.call(this.training, this.exerciseId());
    this.keepFocus();
  }

  // the pressed button is replaced by the next state's buttons; keep the keyboard on the timer
  private keepFocus(): void {
    afterNextRender(() => this.main()?.nativeElement.focus(), { injector: this.injector });
  }
}
