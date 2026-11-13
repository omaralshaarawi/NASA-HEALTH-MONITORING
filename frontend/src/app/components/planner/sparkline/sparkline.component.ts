import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

// Small trend line for up to 14 daily values (null = not measured, shown as a gap),
// with an optional dashed line for the personal baseline.
@Component({
  selector: 'app-sparkline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative block' },
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" preserveAspectRatio="none" class="block h-full w-full"
         role="img" [attr.aria-label]="label()">
      @if (geometry(); as g) {
        @if (g.baselineY !== null) {
          <line x1="0" [attr.x2]="width" [attr.y1]="g.baselineY" [attr.y2]="g.baselineY"
                stroke="currentColor" stroke-opacity="0.45" stroke-width="1" stroke-dasharray="3 3"
                vector-effect="non-scaling-stroke" />
        }
        @for (segment of g.segments; track $index) {
          <polyline [attr.points]="segment" fill="none" stroke="currentColor" stroke-width="1.75"
                    stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
        }
      }
    </svg>
    <!-- the last point is an HTML dot so it stays round when the chart is stretched -->
    @if (geometry()?.last; as last) {
      <span class="absolute h-[0.4rem] w-[0.4rem] -translate-x-1/2 -translate-y-1/2 rounded-full"
            [style.left.%]="(last[0] / width) * 100" [style.top.%]="(last[1] / height) * 100"
            [style.background]="dotColor()"></span>
    }
  `,
})
export class SparklineComponent {
  readonly values = input.required<Array<number | null>>();
  readonly baseline = input<number | null>(null);
  readonly dotColor = input('currentColor');
  readonly label = input('Trend over the last days');

  protected readonly width = 120;
  protected readonly height = 32;

  protected readonly geometry = computed(() => {
    const values = this.values();
    const numbers = values.filter((v): v is number => v !== null);
    if (numbers.length === 0) {
      return null;
    }
    const baseline = this.baseline();
    const all = baseline === null ? numbers : [...numbers, baseline];
    const min = Math.min(...all);
    const max = Math.max(...all);
    const span = max - min || 1;
    const pad = 4;

    const x = (i: number) => (values.length === 1 ? this.width / 2 : (i / (values.length - 1)) * (this.width - 2 * pad) + pad);
    const y = (v: number) => this.height - pad - ((v - min) / span) * (this.height - 2 * pad);

    // break the line wherever a day is missing
    const segments: string[] = [];
    let current: string[] = [];
    let last: [number, number] | null = null;
    values.forEach((v, i) => {
      if (v === null) {
        if (current.length) {
          segments.push(current.join(' '));
        }
        current = [];
        return;
      }
      last = [x(i), y(v)];
      current.push(last.join(','));
    });
    if (current.length) {
      segments.push(current.join(' '));
    }
    return { segments, last: last as [number, number] | null, baselineY: baseline === null ? null : y(baseline) };
  });
}
