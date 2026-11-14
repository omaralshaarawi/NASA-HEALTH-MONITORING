import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { CREW } from '../core/crew';
import { EmergencyService } from '../core/emergency.service';
import { MissionStore } from '../core/mission-store.service';
import { VoiceCallService } from '../core/voice/voice-call.service';

// Red strip under the top bar on every page while triage is recommended. It cannot be dismissed;
// it goes away when the assessment no longer recommends triage. While the voice call is running
// it also carries the call's mute and end buttons, whichever page is open.
@Component({
  selector: 'app-alert-strip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  template: `
    <div role="alert" aria-live="assertive">
      @if (emergency.active() || call.active()) {
        <div class="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border border-crit/60 bg-crit/15 px-4 py-2.5 backdrop-blur-xl">
          <svg class="shrink-0 text-crit" viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path d="M10 2.5 18.5 17h-17Z" stroke-linejoin="round" />
            <path d="M10 8v4.25M10 14.5v.25" stroke-linecap="round" />
          </svg>
          <p class="m-0 min-w-0 flex-1 basis-56 text-sm leading-5 text-ink">
            <strong class="font-medium">Emergency.</strong>
            @if (call.active()) {
              Call active for Ast. {{ patientName() }}{{ call.muted() ? ' · mic muted' : '' }}.
            } @else {
              Triage is recommended for Ast. {{ store.member().name }}.
            }
          </p>
          @if (call.active()) {
            <div class="flex flex-wrap gap-2">
              <button type="button" class="btn" [attr.aria-pressed]="call.muted()" (click)="call.toggleMute()">
                {{ call.muted() ? 'Unmute' : 'Mute' }}
              </button>
              <button type="button" class="btn" (click)="end()">{{ confirmingEnd() ? 'Tap again to end' : 'End' }}</button>
              <button type="button" class="btn btn-danger" (click)="emergency.open()">Open triage</button>
            </div>
          } @else {
            <button type="button" class="btn btn-danger" (click)="emergency.open()">{{ emergency.label() }}</button>
          }
        </div>
      }
    </div>
  `,
})
export class AlertStripComponent {
  protected readonly emergency = inject(EmergencyService);
  protected readonly store = inject(MissionStore);
  protected readonly call = inject(VoiceCallService);

  protected readonly confirmingEnd = signal(false);
  protected readonly patientName = computed(() => CREW.find((m) => m.id === this.call.patientId())?.name ?? '');

  // one extra tap, so the call is not ended by accident
  protected end(): void {
    if (!this.confirmingEnd()) {
      this.confirmingEnd.set(true);
      return;
    }
    this.confirmingEnd.set(false);
    void this.call.end();
  }
}
