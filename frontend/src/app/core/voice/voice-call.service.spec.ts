import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { FakeVoiceCallClient } from './fake-voice-call-client';
import { VOICE_CALL_CLIENT } from './voice-call-client';
import { CallRequest, VoiceCallService } from './voice-call.service';

const REQUEST: CallRequest = { patient_id: 'ast_lin', environment_id: 'DEEP_SPACECRAFT', distance_km: 225_000_000 };
const REPLY = { joinUrl: 'wss://example.test/join', incident_id: 'inc_test' };

// lets pending promises settle without moving the clock
const settle = () => vi.advanceTimersByTimeAsync(0);

function setUp() {
  const fake = new FakeVoiceCallClient();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: VOICE_CALL_CLIENT, useValue: () => Promise.resolve(fake) },
    ],
  });
  const call = TestBed.inject(VoiceCallService);
  const http = TestBed.inject(HttpTestingController);

  // start a call and answer the webhook, the way a working n8n would
  const connect = async () => {
    const started = call.start(REQUEST);
    await settle();
    http.expectOne(environment.jarvisStartUrl).flush(REPLY);
    return started;
  };
  return { fake, call, http, connect };
}

describe('VoiceCallService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('asks the webhook for a call and follows it through its states', async () => {
    const { call, http } = setUp();
    const started = call.start(REQUEST);
    expect(call.phase()).toBe('connecting');
    await settle();

    const request = http.expectOne(environment.jarvisStartUrl);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ ...REQUEST, reporter_id: 'ast_lin' });
    request.flush(REPLY);

    expect(await started).toBe('inc_test');
    expect(call.incidentId()).toBe('inc_test');
    expect(call.patientId()).toBe('ast_lin');

    await vi.advanceTimersByTimeAsync(800);
    expect(call.phase()).toBe('speaking');
    await vi.advanceTimersByTimeAsync(1600);
    expect(call.phase()).toBe('listening');
    await vi.advanceTimersByTimeAsync(2200);
    expect(call.phase()).toBe('thinking');
    await vi.advanceTimersByTimeAsync(1000);
    expect(call.phase()).toBe('speaking');
  });

  it('replaces an interim subtitle with the final one', async () => {
    const { call, connect } = setUp();
    await connect();

    await vi.advanceTimersByTimeAsync(800);
    expect(call.lines()).toEqual([{ speaker: 'JARVIS', text: 'JARVIS here.', final: false }]);

    await vi.advanceTimersByTimeAsync(1600);
    expect(call.lines()).toHaveLength(1);
    expect(call.lines()[0].final).toBe(true);

    await vi.advanceTimersByTimeAsync(2200);
    expect(call.lines().map((line) => line.speaker)).toEqual(['JARVIS', 'YOU']);
  });

  it('mutes and unmutes the microphone', async () => {
    const { call, fake, connect } = setUp();
    await connect();

    call.toggleMute();
    expect(call.muted()).toBe(true);
    expect(fake.muted).toBe(true);

    call.toggleMute();
    expect(call.muted()).toBe(false);
    expect(fake.muted).toBe(false);
  });

  it('leaves the call and stops listening when it is ended', async () => {
    const { call, fake, connect } = setUp();
    await connect();
    await vi.advanceTimersByTimeAsync(800);

    await call.end();
    expect(call.phase()).toBe('ended');
    expect(fake.left).toBe(1);
    expect(fake.listening).toBe(false);

    // nothing more arrives, and what was said stays on screen
    await vi.advanceTimersByTimeAsync(10_000);
    expect(call.phase()).toBe('ended');
    expect(call.lines()).toHaveLength(1);
  });

  it('never creates a second call when asked twice', async () => {
    const { call, fake, http } = setUp();
    const first = call.start(REQUEST);
    const second = call.start(REQUEST);
    await settle();

    http.expectOne(environment.jarvisStartUrl).flush(REPLY);
    expect(await first).toBe('inc_test');
    expect(await second).toBe('inc_test');

    // and again once the call is running
    expect(await call.start(REQUEST)).toBe('inc_test');
    http.expectNone(environment.jarvisStartUrl);
    expect(fake.joined).toBe(1);
  });

  it('reports a refused microphone without creating a call', async () => {
    const { call, fake, http } = setUp();
    fake.microphoneAllowed = false;

    expect(await call.start(REQUEST)).toBeNull();
    expect(call.phase()).toBe('error');
    expect(call.error()).toBe('microphone');
    http.expectNone(environment.jarvisStartUrl);
  });

  it('reports a webhook that fails', async () => {
    const { call, http } = setUp();
    const started = call.start(REQUEST);
    await settle();
    http.expectOne(environment.jarvisStartUrl).flush('down', { status: 502, statusText: 'Bad Gateway' });

    expect(await started).toBeNull();
    expect(call.error()).toBe('webhook');
  });

  it('gives up on the webhook after 15 seconds', async () => {
    const { call, http } = setUp();
    const started = call.start(REQUEST);
    await settle();
    http.expectOne(environment.jarvisStartUrl);
    await vi.advanceTimersByTimeAsync(15_000);

    expect(await started).toBeNull();
    expect(call.error()).toBe('timeout');
  });

  it('keeps the incident when the call cannot be joined', async () => {
    const { call, fake, connect } = setUp();
    fake.joinFails = true;

    expect(await connect()).toBe('inc_test');
    expect(call.phase()).toBe('error');
    expect(call.error()).toBe('join');
  });

  it('tells a dropped connection from JARVIS hanging up', async () => {
    const lost = setUp();
    await lost.connect();
    await vi.advanceTimersByTimeAsync(800);
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);
    lost.fake.drop();
    expect(lost.call.error()).toBe('lost');

    vi.restoreAllMocks();
    const hungUp = setUp();
    await hungUp.connect();
    await vi.advanceTimersByTimeAsync(800);
    hungUp.fake.drop();
    expect(hungUp.call.phase()).toBe('ended');
    expect(hungUp.call.error()).toBeNull();
  });

  it('can start a new call after one has ended', async () => {
    const { call, fake, connect } = setUp();
    await connect();
    await call.end();

    await connect();
    expect(call.active()).toBe(true);
    expect(fake.joined).toBe(2);
  });
});
