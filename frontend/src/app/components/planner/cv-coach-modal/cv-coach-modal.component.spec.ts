import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { MissionStore } from '../../../core/mission-store.service';
import { environment } from '../../../../environments/environment';
import { MOCK_ASSESSMENTS } from '../../../mocks/mock-data';
import { CvTelemetry } from '../../../models-local/cv-coach';
import { CvCoachModalComponent } from './cv-coach-modal.component';

const CV = environment.cvServiceUrl;
const KAI = MOCK_ASSESSMENTS['ast_kai'];

function telemetry(changes: Partial<CvTelemetry> = {}): CvTelemetry {
  return {
    exercise_id: 'zero_t2_ergometer',
    requested_id: 'zero_t2_ergometer',
    profile_fallback: false,
    name: 'Zero T2 compact ergometer',
    target_system: 'SPINE_BONE',
    cv_tracking_mode: 'REP_COUNTER',
    setup_instruction_0g: 'Strap in.',
    reps: 0,
    target_reps: 12,
    hold_seconds: 0,
    target_hold_seconds: 0,
    cadence_rpm: 0,
    stage: 'UP',
    tracked_side: 'LEFT',
    primary_angle: 180,
    visibility_ok: true,
    camera_active: true,
    severity: 'GREEN',
    hud_warning: 'FORM NOMINAL',
    active_warnings: [],
    ...changes,
  };
}

async function render() {
  const refresh = vi.fn();
  const store = { assessment: signal(KAI), crewId: signal('ast_kai'), refresh };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: MissionStore, useValue: store }],
  });
  const http = TestBed.inject(HttpTestingController);
  const fixture = TestBed.createComponent(CvCoachModalComponent);
  fixture.componentRef.setInput('exerciseId', 'zero_t2_ergometer');
  fixture.detectChanges();
  const page: HTMLElement = fixture.nativeElement;
  const update = async (ms = 0) => {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  };
  const button = (text: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) ?? null;
  return { fixture, page, http, refresh, update, button };
}

// answers the opening calls the way a running cv-service would
function goOnline(http: HttpTestingController): void {
  http.expectOne(`${CV}/exercises`).flush([{ exercise_id: 'zero_t2_ergometer', name: 'Zero T2 compact ergometer' }]);
  const select = http.expectOne(`${CV}/select`);
  expect(select.request.body).toEqual({ exercise_id: 'zero_t2_ergometer' });
  select.flush(telemetry());
}

describe('CvCoachModalComponent', () => {
  // jsdom has no <dialog> behaviour; the real browser opens and closes it natively
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.setAttribute('open', '');
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.removeAttribute('open');
    };
  });
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('shows a plain message and no camera when cv-service is not running', async () => {
    const { page, http, update } = await render();
    http.expectOne(`${CV}/exercises`).error(new ProgressEvent('error'), { status: 0 });
    http.match(`${CV}/select`);
    await update();

    expect(page.querySelector('img')).toBeNull();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('camera coach is offline');
  });

  it('shows the feed and live counters while open', async () => {
    const { page, http, update } = await render();
    goOnline(http);
    await update();

    expect(page.querySelector('img')?.getAttribute('src')).toMatch(new RegExp(`^${CV}/video_feed\\?stream=\\w+`));

    await update(400);
    http.expectOne(`${CV}/telemetry`).flush(telemetry({ reps: 3, severity: 'YELLOW', hud_warning: 'HEAD UP - EYES FORWARD' }));
    await update();

    expect(page.querySelector('.stats')?.textContent).toContain('3/ 12');
    expect(page.querySelector('.banner')?.textContent).toContain('HEAD UP - EYES FORWARD');
    expect(page.querySelector('.banner .status')?.textContent?.trim()).toBe('Form YELLOW');
  });

  it('drops the feed and stops polling when closed', async () => {
    const { fixture, page, http, update } = await render();
    goOnline(http);
    await update();
    const img = page.querySelector('img')!;

    const stream = new URL(img.getAttribute('src')!).searchParams.get('stream');
    fixture.destroy();
    expect(img.hasAttribute('src')).toBe(false);

    // the webcam is released right away, for this stream only
    const stop = http.expectOne((r) => r.url === `${CV}/stop`);
    expect(stop.request.method).toBe('POST');
    expect(stop.request.params.get('stream')).toBe(stream);
    stop.flush({ released: true, camera_active: false });

    await vi.advanceTimersByTimeAsync(2000);
    http.expectNone(`${CV}/telemetry`);
  });

  it('syncs the workout for the current crew member and day, then reloads the assessment', async () => {
    const { http, update, button, refresh } = await render();
    goOnline(http);
    await update(400);
    http.expectOne(`${CV}/telemetry`).flush(telemetry({ reps: 4 }));
    await update();

    button('Sync workout to mission log')!.click();
    const sync = http.expectOne(`${CV}/sync-to-backend`);
    expect(sync.request.body).toEqual({ astronaut_id: 'ast_kai', day: KAI.mission_day, minutes_override: null });
    sync.flush({ synced: true, exercise_id: 'zero_t2_ergometer', reps_completed: 4, credited_minutes: 20, previous_ared_minutes: 11, new_ared_minutes: 31, updated_assessment: KAI });
    await update();

    expect(refresh).toHaveBeenCalledTimes(1);
    http.match(`${CV}/telemetry`);
  });

  it('goes offline when cv-service stops answering', async () => {
    const { page, http, update } = await render();
    goOnline(http);
    await update();

    for (let i = 0; i < 5; i++) {
      await update(400);
      http.expectOne(`${CV}/telemetry`).error(new ProgressEvent('error'), { status: 0 });
    }
    await update();

    expect(page.querySelector('img')).toBeNull();
    expect(page.querySelector('[role=alert]')?.textContent).toContain('stopped answering');
  });
});
