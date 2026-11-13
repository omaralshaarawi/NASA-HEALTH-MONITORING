import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { RegionId } from '../../../models/contract';
import { REGION_LABELS } from '../planner-labels';

// Body-region tag. Regions are told apart by icon and name, not by colour:
// green / amber / red stay reserved for health status.
@Component({
  selector: 'app-region-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="chip">
      <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.5"
           stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        @switch (region()) {
          @case ('HEAD_NEURO') {
            <circle cx="8" cy="7" r="4.5" /><path d="M6 13h4M8 11.5V13" />
          }
          @case ('CHEST_CARDIO') {
            <path d="M1.5 8.5h3l1.5-4 2.5 7 1.5-3h4.5" />
          }
          @case ('SPINE_BONE') {
            <path d="M8 1.5v13M5.5 4h5M5 8h6M5.5 12h5" />
          }
          @default {
            <path d="M8 1.8l5.4 3.1v6.2L8 14.2 2.6 11.1V4.9z" /><circle cx="8" cy="8" r="1.6" />
          }
        }
      </svg>
      {{ label() }}
    </span>
  `,
})
export class RegionBadgeComponent {
  readonly region = input.required<RegionId | string>();
  protected readonly label = computed(() => REGION_LABELS[this.region() as RegionId] ?? this.region());
}
