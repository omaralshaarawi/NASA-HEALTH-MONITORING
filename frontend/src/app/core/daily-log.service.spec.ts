import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { MOCK_ASSESSMENTS, MOCK_PLANS } from '../mocks/mock-data';
import { AssessmentResponse } from '../models/contract';
import { API_BASE } from './api.service';
import { DailyLogService } from './daily-log.service';
import { MissionStore } from './mission-store.service';

const KAI = MOCK_ASSESSMENTS['ast_kai'];
const KAI_PLAN = MOCK_PLANS['ast_kai'];

describe('DailyLogService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('saves the day exactly as it was, newest first, and survives a reload', () => {
    const log = TestBed.inject(DailyLogService);
    log.close(KAI, KAI_PLAN);
    log.close({ ...KAI, mission_day: 15 }, null);

    expect(log.of('ast_kai').map((entry) => entry.day)).toEqual([15, 14]);
    expect(log.of('ast_kai')[1].assessment).toEqual(KAI);
    expect(log.of('ast_kai')[1].plan).toEqual(KAI_PLAN);
    expect(log.nextDay('ast_kai')).toBe(16);
    expect(log.nextDay('ast_nova')).toBeNull();

    TestBed.resetTestingModule();
    const reopened = TestBed.inject(DailyLogService);
    expect(reopened.of('ast_kai')[1].assessment.overall).toBe('YELLOW');
  });

  it('forgets everything on clear', () => {
    const log = TestBed.inject(DailyLogService);
    log.close(KAI, KAI_PLAN);
    log.clear();

    expect(log.of('ast_kai')).toEqual([]);
    expect(localStorage.getItem('crew-health.log')).toBe('{}');
  });
});

describe('MissionStore, closing a day', () => {
  let http: HttpTestingController;

  // answers what the store asks for on start-up: crew list, habitats, Kai's assessment and plan
  function setUp(): MissionStore {
    localStorage.setItem('crew-health.crew', 'ast_kai');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const store = TestBed.inject(MissionStore);
    http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_BASE}/api/astronauts`).flush([]);
    http.expectOne(`${API_BASE}/api/environments`).flush([]);
    serve(KAI);
    return store;
  }

  function serve(assessment: AssessmentResponse): void {
    http.expectOne((r) => r.url === `${API_BASE}/api/assessments/ast_kai`).flush(assessment);
    http.expectOne(`${API_BASE}/api/plans/generate`).flush({ ...KAI_PLAN, generated_for_mission_day: assessment.mission_day });
  }

  beforeEach(() => localStorage.clear());
  afterEach(() => http.verify());

  it('leaves the next day empty until the backend has an assessment for it', () => {
    const store = setUp();
    expect(store.assessment()?.mission_day).toBe(14);

    store.startNextDay();

    expect(store.assessment()).toBeNull();
    expect(store.plan()).toBeNull();
    expect(store.pendingDay()).toBe(15);
    expect(store.lastClosed()?.assessment).toEqual(KAI);

    // a reload still has only day 14 on the backend: still waiting
    store.refresh();
    http.expectOne(`${API_BASE}/api/astronauts`).flush([]);
    http.expectOne(`${API_BASE}/api/environments`).flush([]);
    serve(KAI);
    expect(store.assessment()).toBeNull();
  });

  it('shows the new day once its readings have been assessed', () => {
    const store = setUp();
    store.startNextDay();

    const day15 = { ...KAI, mission_day: 15 };
    store
      .logReadings({
        astronaut_id: 'ast_kai',
        mission_day: 15,
        environment_id: 'DEEP_SPACECRAFT',
        source: 'MANUAL',
        vitals: { hr_rest_bpm: 62 },
        habitat: { radiation_mSv_day: 1.4, cabin_co2_mmHg: 2.6 },
        compliance: { morning_vitals_logged: true, ared_resistive_minutes: 60, ration_intake_pct: 100 },
      })
      .subscribe();
    http.expectOne(`${API_BASE}/api/measurements`).flush(day15);

    expect(store.pendingDay()).toBeNull();
    expect(store.assessment()?.mission_day).toBe(15);

    http.expectOne(`${API_BASE}/api/astronauts`).flush([]);
    http.expectOne(`${API_BASE}/api/environments`).flush([]);
    serve(day15);
    expect(store.plan()?.generated_for_mission_day).toBe(15);
  });

  it('resets the demo: log cleared and the backend reloaded', () => {
    const store = setUp();
    store.startNextDay();

    store.resetDemo().subscribe();
    http.expectOne(`${API_BASE}/api/reset`).flush({ seed_records: 42 });
    http.expectOne(`${API_BASE}/api/astronauts`).flush([]);
    http.expectOne(`${API_BASE}/api/environments`).flush([]);
    serve(KAI);

    expect(store.lastClosed()).toBeNull();
    expect(store.pendingDay()).toBeNull();
    expect(store.assessment()?.mission_day).toBe(14);
  });
});
