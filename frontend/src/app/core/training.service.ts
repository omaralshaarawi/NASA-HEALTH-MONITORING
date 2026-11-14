import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';

import { MissionStore } from './mission-store.service';

export type TrainingState = 'idle' | 'running' | 'paused' | 'done';

interface Entry {
  state: TrainingState;
  elapsedMs: number;
}

const SAVED_TRAINING = 'crew-health.training';
const IDLE: Entry = { state: 'idle', elapsedMs: 0 };

// Countdown timers for today's training sessions. This is screen state only: it never touches
// the assessment, the compliance fields or the plan's resource numbers.
//
// Time is always worked out from timestamps, so a tab left in the background stays accurate.
// A session that was running when the page closed comes back paused at its last saved time.
@Injectable({ providedIn: 'root' })
export class TrainingService {
  private readonly store = inject(MissionStore);

  // every saved session, keyed by crew member, mission day and exercise
  private readonly entries = signal<Record<string, Entry>>(readSaved());
  private readonly now = signal(Date.now());
  // the one session whose clock is running
  private clock: { key: string; exerciseId: string; since: number; base: number; totalMs: number } | null = null;
  private ticker: ReturnType<typeof setInterval> | null = null;

  // exercise id of the session that last ran down to zero, for the screen-reader announcement
  readonly lastCompleted = signal<string | null>(null);

  // no training while an emergency is open
  readonly locked = computed(() => this.store.assessment()?.triage_recommended === true);

  private readonly sessions = computed(() => this.store.plan()?.draft.sessions ?? []);

  // the session that is running or paused today, if any
  readonly activeId = computed(() => {
    const active = this.sessions().find((s) => ['running', 'paused'].includes(this.stateOf(s.exercise_id)));
    return active?.exercise_id ?? null;
  });

  readonly summary = computed(() => {
    const sessions = this.sessions();
    const elapsedMs = sessions.reduce((sum, s) => sum + this.elapsedMs(s.exercise_id), 0);
    return {
      completed: sessions.filter((s) => this.stateOf(s.exercise_id) === 'done').length,
      total: sessions.length,
      minutesDone: Math.floor(elapsedMs / 60_000),
      minutesPlanned: sessions.reduce((sum, s) => sum + s.minutes, 0),
    };
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTicker());
  }

  stateOf(exerciseId: string): TrainingState {
    return this.entry(this.key(exerciseId)).state;
  }

  elapsedMs(exerciseId: string): number {
    const key = this.key(exerciseId);
    if (this.clock?.key === key && this.entry(key).state === 'running') {
      return Math.min(this.clock.totalMs, this.clock.base + (this.now() - this.clock.since));
    }
    return this.entry(key).elapsedMs;
  }

  remainingMs(exerciseId: string): number {
    return Math.max(0, this.totalMs(exerciseId) - this.elapsedMs(exerciseId));
  }

  // why Start is not available right now, or '' when it is
  blockedReason(exerciseId: string): string {
    if (this.locked()) return 'Emergency active: follow the triage';
    const active = this.activeId();
    if (active && active !== exerciseId) return 'Finish or reset the session in progress first';
    return '';
  }

  start(exerciseId: string): void {
    if (this.stateOf(exerciseId) !== 'idle' || this.blockedReason(exerciseId)) return;
    this.run(exerciseId, 0);
  }

  pause(exerciseId: string): void {
    if (this.stateOf(exerciseId) !== 'running') return;
    this.put(this.key(exerciseId), { state: 'paused', elapsedMs: this.elapsedMs(exerciseId) });
    this.stopClock(exerciseId);
  }

  resume(exerciseId: string): void {
    if (this.stateOf(exerciseId) !== 'paused' || this.locked()) return;
    this.run(exerciseId, this.entry(this.key(exerciseId)).elapsedMs);
  }

  // finish before the countdown ends; the time actually trained is kept
  complete(exerciseId: string): void {
    const state = this.stateOf(exerciseId);
    if (state !== 'running' && state !== 'paused') return;
    this.put(this.key(exerciseId), { state: 'done', elapsedMs: this.elapsedMs(exerciseId) });
    this.stopClock(exerciseId);
  }

  reset(exerciseId: string): void {
    this.stopClock(exerciseId);
    this.entries.update((all) => {
      const { [this.key(exerciseId)]: _removed, ...rest } = all;
      return rest;
    });
    this.save();
  }

  // demo reset: forget every timer
  clearAll(): void {
    this.clock = null;
    this.stopTicker();
    this.entries.set({});
    this.save();
  }

  private run(exerciseId: string, base: number): void {
    // one clock at a time, whoever it belonged to
    if (this.clock) {
      const other = this.clock;
      this.put(other.key, { state: 'paused', elapsedMs: Math.min(other.totalMs, other.base + (Date.now() - other.since)) });
    }
    const key = this.key(exerciseId);
    this.clock = { key, exerciseId, since: Date.now(), base, totalMs: this.totalMs(exerciseId) };
    this.now.set(Date.now());
    this.put(key, { state: 'running', elapsedMs: base });
    this.ticker ??= setInterval(() => this.tick(), 500);
  }

  private tick(): void {
    const clock = this.clock;
    if (!clock) return;
    this.now.set(Date.now());
    const elapsed = Math.min(clock.totalMs, clock.base + (Date.now() - clock.since));
    if (elapsed >= clock.totalMs) {
      this.put(clock.key, { state: 'done', elapsedMs: clock.totalMs });
      this.lastCompleted.set(clock.exerciseId);
      this.clock = null;
      this.stopTicker();
      return;
    }
    // keep the saved time fresh, so a reload loses at most half a second
    this.put(clock.key, { state: 'running', elapsedMs: elapsed });
  }

  private stopClock(exerciseId: string): void {
    if (this.clock?.key !== this.key(exerciseId)) return;
    this.clock = null;
    this.stopTicker();
  }

  private stopTicker(): void {
    if (this.ticker) clearInterval(this.ticker);
    this.ticker = null;
  }

  private totalMs(exerciseId: string): number {
    const session = this.sessions().find((s) => s.exercise_id === exerciseId);
    return (session?.minutes ?? 0) * 60_000;
  }

  private key(exerciseId: string): string {
    const day = this.store.assessment()?.mission_day ?? 0;
    return [this.store.crewId(), day, exerciseId].join('|');
  }

  private entry(key: string): Entry {
    return this.entries()[key] ?? IDLE;
  }

  private put(key: string, entry: Entry): void {
    this.entries.update((all) => ({ ...all, [key]: entry }));
    this.save();
  }

  private save(): void {
    try {
      localStorage.setItem(SAVED_TRAINING, JSON.stringify(this.entries()));
    } catch {
      // no storage: the timers still work until the page is closed
    }
  }
}

function readSaved(): Record<string, Entry> {
  try {
    const saved = JSON.parse(localStorage.getItem(SAVED_TRAINING) ?? '{}') as Record<string, Entry>;
    // nobody was timing while the page was closed
    return Object.fromEntries(
      Object.entries(saved).map(([key, entry]) => [key, entry.state === 'running' ? { ...entry, state: 'paused' as const } : entry]),
    );
  } catch {
    return {};
  }
}
