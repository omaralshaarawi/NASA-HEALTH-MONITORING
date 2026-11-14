import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';

// One number with its unit and a caption underneath. An icon can be passed as content.
@Component({
  selector: 'app-metric-tile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  template: `
    <ng-content />
    <div class="num leading-none text-ink" [class]="compact() ? 'text-[1.375rem]' : 'text-[1.75rem]'">
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
  readonly compact = input(false, { transform: booleanAttribute });
}
