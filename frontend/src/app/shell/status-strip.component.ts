import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { MissionStore } from '../core/mission-store.service';
import { delayText } from '../ui/format';
import { StatusChipComponent } from '../ui/status-chip.component';

// One line of mission facts under the top bar: who, how they are, where, and how far Earth is.
@Component({
  selector: 'app-status-strip',
  imports: [StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  template: `
    <div class="mb-6 flex flex-wrap items-center gap-2" aria-label="Mission status">
      @if (store.assessment(); as a) {
        <app-status-chip [status]="a.overall" />
        <span class="chip num">Day {{ a.mission_day }}</span>
        <span class="chip">{{ a.environment.label }}</span>
        <span class="chip num">{{ a.environment.gravity_g }} g</span>
        <span class="chip num">
          @if (delay(); as d) {
            {{ d.value }}@if (d.unit) { {{ d.unit }} one way }
          }
        </span>
        <span class="chip" [class.text-signal]="a.comms.mode === 'AUTONOMOUS'" [class.border-signal]="a.comms.mode === 'AUTONOMOUS'">
          {{ a.comms.mode }}
        </span>
        @if (store.offline()) {
          <span class="chip">Offline · bundled day {{ a.mission_day }} data</span>
        }
      } @else {
        <span class="chip" role="status">{{ store.loading() ? 'Loading crew data…' : "Awaiting today's readings" }}</span>
      }
    </div>
  `,
})
export class StatusStripComponent {
  protected readonly store = inject(MissionStore);

  protected readonly delay = computed(() => {
    const a = this.store.assessment();
    return a ? delayText(a.comms.one_way_delay_s) : null;
  });
}
