import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// One number with its unit and a caption underneath.
@Component({
  selector: 'app-metric-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <div class="num text-[1.75rem] leading-none text-ink">
      {{ value() }}@if (unit()) {<span class="unit ml-1">{{ unit() }}</span>}
    </div>
    <div class="eyebrow mt-2">{{ label() }}</div>
    @if (note()) {
      <div class="mt-0.5 text-[0.6875rem] leading-4 text-ink-mute">{{ note() }}</div>
    }
  `,
})
export class MetricTileComponent {
  readonly value = input.required<string | number>();
  readonly unit = input('');
  readonly label = input.required<string>();
  readonly note = input('');
}
