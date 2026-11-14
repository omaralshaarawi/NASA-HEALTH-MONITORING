import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { AssessmentResponse, IndicatorAssessment, IndicatorId, RegionId } from '../../models/contract';
import { REGION_LABELS } from '../planner/planner-labels';
import { SparklineComponent } from '../planner/sparkline/sparkline.component';

// One glass card per indicator: today's value, its status and reasons exactly as the engine
// gave them, the 14-day trend, and a way into the 0 g measurement guide.
@Component({
  selector: 'app-indicator-list',
  imports: [SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  styles: `
    .grid-cards {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(11.5rem, 1fr));
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .vital {
      display: flex;
      flex-direction: column;
      min-height: 15.5rem;
      padding: 1rem 1.125rem 0.875rem;
    }
    .label {
      font-size: 0.75rem;
      letter-spacing: 0.05em;
      line-height: 1.3;
      text-transform: uppercase;
      color: var(--color-ink);
    }
    .value {
      margin-top: 1rem;
      font-size: 2rem;
      line-height: 1;
      color: var(--color-ink);
    }
    .value .unit { margin-left: 0.3125rem; font-size: 0.8125rem; }
    .reasons {
      margin: 0.625rem 0 0;
      font-size: 0.75rem;
      line-height: 1.45;
      color: var(--color-ink-dim);
    }
    .again {
      margin-top: 0.375rem;
      font-size: 0.6875rem;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--color-warn);
    }
    .guide {
      align-self: flex-start;
      margin-top: 0.625rem;
      padding: 0;
      border: 0;
      background: none;
      color: var(--color-ink-mute);
      font: inherit;
      font-size: 0.6875rem;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      text-decoration: underline;
      text-decoration-color: var(--color-line-strong);
      text-underline-offset: 0.25rem;
      cursor: pointer;
    }
    .guide:hover { color: var(--color-signal); }
    .green { color: var(--color-ok); }
    .yellow { color: var(--color-warn); }
    .red { color: var(--color-crit); }
  `,
  template: `
    <ul class="grid-cards">
      @for (indicator of visible(); track indicator.id) {
        <li class="panel vital">
          <div class="eyebrow">{{ regionLabels[indicator.region] }}</div>
          <div class="label mt-1">{{ indicator.label }}</div>

          <div class="num value">
            {{ indicator.value ?? '—' }}@if (indicator.value !== null) {<span class="unit">{{ indicator.unit }}</span>}
          </div>
          <div class="mt-3">
            <span [class]="'status status-pill status-' + indicator.status.toLowerCase()">{{ indicator.status }}</span>
          </div>

          @if (indicator.reasons.length) {
            <p class="reasons">{{ indicator.reasons.join(' · ') }}</p>
          }
          @if (indicator.remeasure) {
            <div class="again">Re-measure to confirm</div>
          }

          <div class="mt-auto pt-3">
            @if (hasTrend(indicator)) {
              <app-sparkline
                [class]="'h-9 ' + indicator.status.toLowerCase()"
                [values]="indicator.trend"
                [baseline]="indicator.anchor?.mean ?? null"
                [label]="indicator.label + ', last ' + indicator.trend.length + ' days'"
              />
            }
          </div>
          <button type="button" class="guide" (click)="indicatorSelected.emit(indicator.id)">How to measure in 0 g</button>
        </li>
      }
    </ul>
  `,
})
export class IndicatorListComponent {
  readonly assessment = input.required<AssessmentResponse>();
  readonly onlyRegion = input<RegionId | null>(null);
  readonly indicatorSelected = output<IndicatorId>();

  protected readonly regionLabels = REGION_LABELS;

  protected readonly visible = computed(() => {
    const region = this.onlyRegion();
    const all = this.assessment().indicators;
    return region ? all.filter((indicator) => indicator.region === region) : all;
  });

  protected hasTrend(indicator: IndicatorAssessment): boolean {
    return indicator.trend.some((value) => value !== null);
  }
}
