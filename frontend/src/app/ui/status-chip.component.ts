import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Status } from '../models/contract';

@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="classes()">{{ status() }}@if (note()) { · {{ note() }} }</span>`,
})
export class StatusChipComponent {
  readonly status = input.required<Status>();
  readonly note = input('');

  protected readonly classes = computed(
    () => 'status status-pill status-' + this.status().toLowerCase(),
  );
}
