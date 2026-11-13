import { ChangeDetectionStrategy, Component, OnDestroy, output, signal } from '@angular/core';

export interface PvtResult {
  median_rt_ms: number;
  lapses_gt500ms: number;
  n_trials: number;
  source: 'BROWSER_30S';
}

@Component({
  selector: 'app-pvt-test',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rounded border border-line bg-hull p-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div class="eyebrow">Optional · browser reaction test</div>
          <p class="m-0 mt-1 text-sm leading-6 text-ink-dim">Tap the target as soon as it appears. The test lasts 30 seconds.</p>
        </div>
        @if (running()) { <span class="chip num">{{ remaining() }} s</span> }
        @else if (result(); as done) { <span class="chip num">{{ done.n_trials }} trials · median {{ done.median_rt_ms }} ms</span> }
      </div>
      <div class="mt-3 flex min-h-28 items-center justify-center rounded border border-line bg-panel">
        @if (running() && targetVisible()) {
          <button type="button" class="h-16 w-16 rounded-full border-4 border-signal bg-signal text-void" aria-label="Tap now" (click)="respond()">
            <span class="sr-only">Tap now</span>
          </button>
        } @else if (running()) {
          <span class="eyebrow">Wait for the target</span>
        } @else if (result(); as done) {
          <span class="text-sm text-ink-dim">{{ done.lapses_gt500ms }} responses over 500 ms</span>
        } @else {
          <span class="eyebrow">Ready when you are</span>
        }
      </div>
      <div class="mt-3 flex items-center justify-between gap-3">
        <p class="m-0 text-xs text-ink-mute" aria-live="polite">{{ message() }}</p>
        @if (!running()) {
          <button type="button" class="btn" (click)="start()">{{ result() ? 'Run again' : 'Start 30 s test' }}</button>
        } @else {
          <button type="button" class="btn" (click)="stop()">Stop test</button>
        }
      </div>
    </div>
  `,
})
export class PvtTestComponent implements OnDestroy {
  readonly completed = output<PvtResult | null>();
  protected readonly running = signal(false);
  protected readonly targetVisible = signal(false);
  protected readonly remaining = signal(30);
  protected readonly result = signal<PvtResult | null>(null);
  protected readonly message = signal('');

  private readonly responses: number[] = [];
  private stimulusAt = 0;
  private stimulusTimer: ReturnType<typeof setTimeout> | null = null;
  private clockTimer: ReturnType<typeof setInterval> | null = null;
  private testEndsAt = 0;

  protected start(): void {
    this.clearTimers();
    this.responses.length = 0;
    this.result.set(null);
    this.completed.emit(null);
    this.message.set('Test in progress.');
    this.remaining.set(30);
    this.running.set(true);
    this.testEndsAt = Date.now() + 30_000;
    this.scheduleStimulus();
    this.clockTimer = setInterval(() => {
      const secondsLeft = Math.max(0, Math.ceil((this.testEndsAt - Date.now()) / 1000));
      this.remaining.set(secondsLeft);
      if (secondsLeft === 0) this.finish();
    }, 100);
  }

  protected respond(): void {
    if (!this.targetVisible()) return;
    this.responses.push(Math.round(performance.now() - this.stimulusAt));
    this.targetVisible.set(false);
    if (Date.now() < this.testEndsAt) this.scheduleStimulus();
  }

  protected stop(): void {
    this.finish();
  }

  ngOnDestroy(): void {
    this.clearTimers();
  }

  private scheduleStimulus(): void {
    if (this.stimulusTimer) clearTimeout(this.stimulusTimer);
    const delay = 800 + Math.floor(Math.random() * 1700);
    this.stimulusTimer = setTimeout(() => {
      if (this.running() && Date.now() < this.testEndsAt) {
        this.stimulusAt = performance.now();
        this.targetVisible.set(true);
      }
    }, delay);
  }

  private finish(): void {
    if (!this.running()) return;
    this.running.set(false);
    this.targetVisible.set(false);
    this.remaining.set(0);
    this.clearTimers();
    if (this.responses.length === 0) {
      this.message.set('No valid responses recorded. Run the test again to add a PVT measurement.');
      return;
    }
    const sorted = [...this.responses].sort((a, b) => a - b);
    const middle = Math.floor(sorted.length / 2);
    const median = sorted.length % 2 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2);
    const result: PvtResult = {
      median_rt_ms: median,
      lapses_gt500ms: this.responses.filter((response) => response > 500).length,
      n_trials: this.responses.length,
      source: 'BROWSER_30S',
    };
    this.result.set(result);
    this.message.set('PVT measurement ready to include.');
    this.completed.emit(result);
  }

  private clearTimers(): void {
    if (this.stimulusTimer) clearTimeout(this.stimulusTimer);
    if (this.clockTimer) clearInterval(this.clockTimer);
    this.stimulusTimer = null;
    this.clockTimer = null;
  }
}