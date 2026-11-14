import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MOCK_ASSESSMENTS, MOCK_PLANS } from '../mocks/mock-data';
import { AssessmentResponse, CountermeasurePlan } from '../models/contract';
import { MissionStore } from './mission-store.service';
import { TrainingService } from './training.service';

const MINUTE = 60_000;

// Kai's bundled day-14 plan, with the first two sessions used as A and B.
const plan = MOCK_PLANS['ast_kai'];
const [A, B] = plan.draft.sessions.map((s) => s.exercise_id);
const minutesA = plan.draft.sessions[0].minutes;

// Stands in for the store: the timers only read these three signals.
function fakeStore(triage = false) {
  return {
    crewId: signal('ast_kai'),
    assessment: signal<AssessmentResponse | null>({ ...MOCK_ASSESSMENTS['ast_kai'], triage_recommended: triage }),
    plan: signal<CountermeasurePlan | null>(plan),
  };
}

function setUp(store = fakeStore()) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: MissionStore, useValue: store }] });
  return { store, training: TestBed.inject(TrainingService) };
}

describe('TrainingService', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-14T08:00:00Z'));
  });

  afterEach(() => vi.useRealTimers());

  it('starts, and counts down from the planned minutes', () => {
    const { training } = setUp();
    expect(training.stateOf(A)).toBe('idle');
    expect(training.remainingMs(A)).toBe(minutesA * MINUTE);

    training.start(A);
    vi.advanceTimersByTime(90_000);

    expect(training.stateOf(A)).toBe('running');
    expect(training.remainingMs(A)).toBe(minutesA * MINUTE - 90_000);
  });

  it('stops the clock while paused and carries on after resume', () => {
    const { training } = setUp();
    training.start(A);
    vi.advanceTimersByTime(2 * MINUTE);
    training.pause(A);
    vi.advanceTimersByTime(10 * MINUTE);

    expect(training.stateOf(A)).toBe('paused');
    expect(training.elapsedMs(A)).toBe(2 * MINUTE);

    training.resume(A);
    vi.advanceTimersByTime(MINUTE);

    expect(training.stateOf(A)).toBe('running');
    expect(training.elapsedMs(A)).toBe(3 * MINUTE);
  });

  it('completes by itself when the countdown reaches zero', () => {
    const { training } = setUp();
    training.start(A);
    vi.advanceTimersByTime(minutesA * MINUTE + 1000);

    expect(training.stateOf(A)).toBe('done');
    expect(training.remainingMs(A)).toBe(0);
    expect(training.lastCompleted()).toBe(A);
    expect(training.summary().completed).toBe(1);
    expect(training.summary().minutesDone).toBe(minutesA);
  });

  it('can be completed early, keeping the time actually trained', () => {
    const { training } = setUp();
    training.start(A);
    vi.advanceTimersByTime(5 * MINUTE);
    training.complete(A);
    vi.advanceTimersByTime(5 * MINUTE);

    expect(training.stateOf(A)).toBe('done');
    expect(training.elapsedMs(A)).toBe(5 * MINUTE);
    expect(training.summary().minutesDone).toBe(5);
  });

  it('allows only one session to be active at a time', () => {
    const { training } = setUp();
    training.start(A);
    training.start(B);

    expect(training.stateOf(B)).toBe('idle');
    expect(training.blockedReason(B)).toContain('in progress');

    // a paused session still counts as active
    training.pause(A);
    training.start(B);
    expect(training.stateOf(B)).toBe('idle');

    training.reset(A);
    training.start(B);
    expect(training.stateOf(B)).toBe('running');
  });

  it('comes back paused after a reload, at the last saved time', () => {
    const first = setUp();
    first.training.start(A);
    vi.advanceTimersByTime(4 * MINUTE);

    // the page is closed for an hour, then opened again
    TestBed.resetTestingModule();
    vi.advanceTimersByTime(60 * MINUTE);
    const { training } = setUp();

    expect(training.stateOf(A)).toBe('paused');
    expect(training.elapsedMs(A)).toBe(4 * MINUTE);
    vi.advanceTimersByTime(10 * MINUTE);
    expect(training.elapsedMs(A)).toBe(4 * MINUTE);
  });

  it('cannot start or resume while triage is recommended', () => {
    const { training, store } = setUp(fakeStore(true));
    training.start(A);

    expect(training.locked()).toBe(true);
    expect(training.stateOf(A)).toBe('idle');
    expect(training.blockedReason(A)).toBe('Emergency active: follow the triage');

    // a session paused before the emergency stays paused
    store.assessment.update((a) => a && { ...a, triage_recommended: false });
    training.start(A);
    training.pause(A);
    store.assessment.update((a) => a && { ...a, triage_recommended: true });
    training.resume(A);
    expect(training.stateOf(A)).toBe('paused');
  });

  it('keeps each crew member and mission day apart', () => {
    const { training, store } = setUp();
    training.start(A);
    vi.advanceTimersByTime(MINUTE);
    training.complete(A);

    store.crewId.set('ast_nova');
    expect(training.stateOf(A)).toBe('idle');

    store.crewId.set('ast_kai');
    store.assessment.update((a) => a && { ...a, mission_day: a.mission_day + 1 });
    expect(training.stateOf(A)).toBe('idle');
  });
});
