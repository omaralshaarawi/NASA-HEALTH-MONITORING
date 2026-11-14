import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { AssessmentResponse, IndicatorId } from '../../../models/contract';
import { ModalShellComponent } from '../modal-shell/modal-shell.component';
import { REGION_LABELS, statusClass } from '../planner-labels';
import { SparklineComponent } from '../sparkline/sparkline.component';
import { GUIDES, limitsFor } from './measurement-guides';

const STATUS_COLOR = { GREEN: 'var(--color-ok)', YELLOW: 'var(--color-warn)', RED: 'var(--color-crit)' } as const;

// Opens from any indicator card: how to take the measurement in 0 g, why spaceflight changes it,
// and when to act. Today's value and the limits come from the assessment and rules.json.
@Component({
  selector: 'app-measurement-guide-modal',
  imports: [DecimalPipe, ModalShellComponent, SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './measurement-guide-modal.component.html',
})
export class MeasurementGuideModalComponent {
  readonly indicatorId = input.required<IndicatorId>();
  readonly assessment = input.required<AssessmentResponse>();
  readonly closed = output<void>();

  protected readonly statusClass = statusClass;

  protected readonly indicator = computed(
    () => this.assessment().indicators.find((i) => i.id === this.indicatorId()) ?? null,
  );
  protected readonly guide = computed(() => GUIDES[this.indicatorId()]);
  protected readonly limits = computed(() => limitsFor(this.indicatorId()));
  protected readonly region = computed(() => {
    const i = this.indicator();
    return i ? REGION_LABELS[i.region] : '';
  });
  protected readonly dotColor = computed(() => STATUS_COLOR[this.indicator()?.status ?? 'GREEN']);
  protected readonly hasTrend = computed(() => (this.indicator()?.trend ?? []).some((v) => v !== null));
}
