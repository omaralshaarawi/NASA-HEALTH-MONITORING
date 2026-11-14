import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { FakeVoiceCallClient } from '../../core/voice/fake-voice-call-client';
import { VOICE_CALL_CLIENT } from '../../core/voice/voice-call-client';
import { VoiceCallService } from '../../core/voice/voice-call.service';
import { VoiceCallPanelComponent } from './voice-call-panel.component';

const REQUEST = { patient_id: 'ast_lin', environment_id: 'DEEP_SPACECRAFT', distance_km: 225_000_000 };

async function render(options: { microphoneAllowed?: boolean } = {}) {
  const fake = new FakeVoiceCallClient();
  fake.microphoneAllowed = options.microphoneAllowed ?? true;
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: VOICE_CALL_CLIENT, useValue: () => Promise.resolve(fake) },
    ],
  });
  const call = TestBed.inject(VoiceCallService);
  const fixture = TestBed.createComponent(VoiceCallPanelComponent);
  let asked = 0;
  fixture.componentInstance.callAgain.subscribe(() => asked++);

  const started = call.start(REQUEST);
  await vi.advanceTimersByTimeAsync(0);
  if (fake.microphoneAllowed) {
    TestBed.inject(HttpTestingController)
      .expectOne(environment.jarvisStartUrl)
      .flush({ joinUrl: 'wss://example.test/join', incident_id: 'inc_test' });
  }
  await started;
  fixture.detectChanges();

  const page: HTMLElement = fixture.nativeElement;
  const button = (text: string) =>
    Array.from(page.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) ?? null;
  return {
    fake,
    call,
    fixture,
    page,
    button,
    asked: () => asked,
    status: () => page.querySelector('[role=status]')?.textContent?.trim() ?? '',
    caption: () => page.querySelector('.caption')?.textContent?.trim() ?? '',
    press: (text: string) => {
      button(text)?.click();
      fixture.detectChanges();
    },
    after: async (ms: number) => {
      await vi.advanceTimersByTimeAsync(ms);
      fixture.detectChanges();
    },
  };
}

describe('VoiceCallPanelComponent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('names each state of the call in words', async () => {
    const panel = await render();
    expect(panel.status()).toBe('Connecting');

    await panel.after(800);
    expect(panel.status()).toBe('Jarvis is speaking');
    await panel.after(1600);
    expect(panel.status()).toBe('Listening');
    await panel.after(2200);
    expect(panel.status()).toBe('Jarvis is thinking');
  });

  it('shows the subtitle as it is spoken, then the final text', async () => {
    const panel = await render();

    await panel.after(800);
    expect(panel.caption()).toBe('JARVISJARVIS here.');
    expect(panel.page.querySelector('.caption .interim')).not.toBeNull();

    await panel.after(1600);
    expect(panel.caption()).toContain('This is a simulated call.');
    expect(panel.page.querySelector('.caption .interim')).toBeNull();

    await panel.after(2200);
    expect(panel.caption()).toBe('YOUYes.');
  });

  it('marks a muted microphone on the button and in the status', async () => {
    const panel = await render();
    await panel.after(2400);
    expect(panel.button('Mute mic')?.getAttribute('aria-pressed')).toBe('false');

    panel.press('Mute mic');
    expect(panel.button('Unmute mic')?.getAttribute('aria-pressed')).toBe('true');
    expect(panel.status()).toContain('Mic muted');
    expect(panel.fake.muted).toBe(true);
  });

  it('ends the call only on the second tap', async () => {
    const panel = await render();
    await panel.after(800);

    panel.press('End call');
    expect(panel.call.active()).toBe(true);

    panel.press('Tap again to end');
    await panel.after(0);
    expect(panel.status()).toBe('Call ended');
    expect(panel.fake.left).toBe(1);
    expect(panel.fake.listening).toBe(false);

    panel.press('Call again');
    expect(panel.asked()).toBe(1);
  });

  it('keeps the call running when the page that shows it is closed', async () => {
    const panel = await render();
    await panel.after(800);
    panel.fixture.destroy();
    await vi.advanceTimersByTimeAsync(1600);

    expect(panel.call.active()).toBe(true);
    expect(panel.call.phase()).toBe('listening');
    expect(panel.fake.left).toBe(0);
  });

  it('explains a refused microphone and offers another try', async () => {
    const panel = await render({ microphoneAllowed: false });

    expect(panel.status()).toBe('Connection error');
    expect(panel.page.querySelector('[role=alert]')?.textContent).toContain('microphone could not be used');
    expect(panel.page.querySelector('[role=alert]')?.textContent).toContain('checklist below still works');

    panel.press('Try again');
    expect(panel.asked()).toBe(1);
  });
});
