import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Status } from '../models/contract';

// A status as the engine gave it: shape, colour and the word, in a pill.
@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="classes()">{{ status() }}@if (note()) { · {{ note() }} }</span>`,
})
export class StatusChipComponent {
  readonly status = input.required<Status>();
  // a short word after the status, e.g. "caution"
  readonly note = input('');

  protected readonly classes = computed(() => 'status status-pill status-' + this.status().toLowerCase());
}
