import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MissionStore } from '../../../core/mission-store.service';
import { TrainingService } from '../../../core/training.service';
import { MOCK_ASSESSMENTS, MOCK_PLANS } from '../../../mocks/mock-data';
import { SessionTimerComponent } from './session-timer.component';

const session = MOCK_PLANS['ast_kai'].draft.sessions[0];

function render(needsDirection: boolean, triage = false) {
  const store = {
    crewId: signal('ast_kai'),
    assessment: signal({ ...MOCK_ASSESSMENTS['ast_kai'], triage_recommended: triage }),
    plan: signal(MOCK_PLANS['ast_kai']),
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: MissionStore, useValue: store }] });
  const fixture = TestBed.createComponent(SessionTimerComponent);
  fixture.componentRef.setInput('exerciseId', session.exercise_id);
  fixture.componentRef.setInput('minutes', session.minutes);
  fixture.componentRef.setInput('needsDirection', needsDirection);
  fixture.detectChanges();

  const page: HTMLElement = fixture.nativeElement;
  const button = (text: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) ?? null;
  const press = (text: string) => {
    button(text)?.click();
    fixture.detectChanges();
  };
  return { page, button, press, training: TestBed.inject(TrainingService) };
}

describe('SessionTimerComponent', () => {
  beforeEach(() => localStorage.clear());

  it('starts straight away for an ordinary session', () => {
    const { press, training, page } = render(false);
    press('Start');

    expect(training.stateOf(session.exercise_id)).toBe('running');
    expect(page.textContent).toContain('Running');
    training.reset(session.exercise_id);
  });

  it('asks for confirmation when the flight surgeon directs the session', () => {
    const { press, button, training, page } = render(true);
    press('Start');

    expect(training.stateOf(session.exercise_id)).toBe('idle');
    expect(page.textContent).toContain('Start only if you have been directed to');

    press('Cancel');
    expect(button('Confirm and start')).toBeNull();

    press('Start');
    press('Confirm and start');
    expect(training.stateOf(session.exercise_id)).toBe('running');
    training.reset(session.exercise_id);
  });

  it('disables Start and says why while triage is recommended', () => {
    const { button, page } = render(false, true);

    expect(button('Start')?.disabled).toBe(true);
    expect(page.textContent).toContain('Emergency active: follow the triage');
  });
});
