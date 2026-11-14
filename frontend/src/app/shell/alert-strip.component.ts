import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { EmergencyService } from '../core/emergency.service';
import { MissionStore } from '../core/mission-store.service';

// Red strip under the top bar on every page while triage is recommended. It cannot be dismissed;
// it goes away when the assessment no longer recommends triage.
@Component({
  selector: 'app-alert-strip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  template: `
    <div role="alert" aria-live="assertive">
      @if (emergency.active()) {
        <div class="mb-4 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-2xl border border-crit/60 bg-crit/15 px-4 py-2.5 backdrop-blur-xl">
          <svg class="shrink-0 text-crit" viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
            <path d="M10 2.5 18.5 17h-17Z" stroke-linejoin="round" />
            <path d="M10 8v4.25M10 14.5v.25" stroke-linecap="round" />
          </svg>
          <p class="m-0 min-w-0 flex-1 basis-56 text-sm leading-5 text-ink">
            <strong class="font-medium">Emergency.</strong>
            Triage is recommended for Ast. {{ store.member().name }}.
          </p>
          <button type="button" class="btn btn-danger" (click)="emergency.open()">{{ emergency.label() }}</button>
        </div>
      }
    </div>
  `,
})
export class AlertStripComponent {
  protected readonly emergency = inject(EmergencyService);
  protected readonly store = inject(MissionStore);
}
