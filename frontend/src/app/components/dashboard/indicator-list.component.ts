import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { SparklineComponent } from '../planner/sparkline/sparkline.component';
import { REGION_LABELS, statusClass } from '../planner/planner-labels';
import { AssessmentResponse, IndicatorAssessment, IndicatorId, RegionId } from '../../models/contract';

@Component({
  selector: 'app-indicator-list',
  imports: [SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section aria-labelledby="indicator-title">
      <div class="flex flex-wrap items-baseline justify-between gap-3">
        <div>
          <div class="eyebrow">Assessment detail</div>
          <h2 id="indicator-title" class="display m-0 mt-1 text-2xl text-ink">Today's indicators</h2>
        </div>
        <span class="eyebrow">Tap a reading for its measurement guide</span>
      </div>
      <div class="mt-4 grid gap-4 xl:grid-cols-2">
        @for (region of visibleRegions(); track region) {
          <section class="panel p-4" [attr.aria-label]="regionLabel(region) + ' indicators'">
            <h3 class="eyebrow m-0">{{ regionLabel(region) }}</h3>
            <ul class="m-0 mt-3 grid list-none gap-2 p-0 sm:grid-cols-2">
              @for (indicator of inRegion(region); track indicator.id) {
                <li>
                  <button type="button" class="w-full rounded border border-line bg-hull p-3 text-left hover:border-signal" (click)="indicatorSelected.emit(indicator.id)">
                    <span class="flex items-start justify-between gap-2">
                      <span class="min-w-0">
                        <span class="block text-sm text-ink-dim">{{ indicator.label }}</span>
                        <span class="num mt-1 block text-xl text-ink">
                          {{ indicator.value ?? '—' }}<span class="unit ml-1">{{ indicator.value !== null ? indicator.unit : '' }}</span>
                        </span>
                      </span>
                      <span [class]="statusClass(indicator.status)">{{ indicator.status }}</span>
                    </span>
                    @if (indicator.reasons.length) {
                      <span class="mt-2 block text-xs leading-5 text-ink-dim">{{ indicator.reasons.join(' · ') }}</span>
                    }
                    @if (indicator.remeasure) {
                      <span class="mt-2 block text-xs text-signal">Re-measure to confirm</span>
                    }
                    @if (hasTrend(indicator)) {
                      <app-sparkline class="mt-3 h-8 text-signal" [values]="indicator.trend" [baseline]="indicator.anchor?.mean ?? null"
                                     [label]="indicator.label + ' trend'" />
                    }
                  </button>
                </li>
              }
            </ul>
          </section>
        }
      </div>
    </section>
  `,
})
export class IndicatorListComponent {
  readonly assessment = input.required<AssessmentResponse>();
  readonly onlyRegion = input<RegionId | null>(null);
  readonly indicatorSelected = output<IndicatorId>();
  protected readonly regionIds: RegionId[] = ['HEAD_NEURO', 'CHEST_CARDIO', 'SPINE_BONE', 'SYSTEMIC_IMMUNE_METABOLIC'];

  protected visibleRegions(): RegionId[] {
    return this.onlyRegion() ? [this.onlyRegion() as RegionId] : this.regionIds;
  }

  protected regionLabel(region: RegionId): string {
    return REGION_LABELS[region];
  }

  protected inRegion(region: RegionId): IndicatorAssessment[] {
    return this.assessment().indicators.filter((indicator) => indicator.region === region);
  }

  protected statusClass = statusClass;

  protected hasTrend(indicator: IndicatorAssessment): boolean {
    return indicator.trend.some((value) => value !== null);
  }
}