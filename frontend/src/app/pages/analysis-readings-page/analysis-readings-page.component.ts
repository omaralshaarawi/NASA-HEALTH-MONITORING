import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MeasurementGuideModalComponent } from '../../components/planner/measurement-guide-modal/measurement-guide-modal.component';
import { REGION_LABELS } from '../../components/planner/planner-labels';
import { MissionStore } from '../../core/mission-store.service';
import { IndicatorId } from '../../models/contract';
import { AwaitingPanelComponent } from '../../ui/awaiting-panel.component';
import { delayText } from '../../ui/format';
import { PlainWordsPipe } from '../../ui/plain-words.pipe';
import { SectionHeaderComponent } from '../../ui/section-header.component';

// Today's readings next to each crew member's own normal, and which of them shaped today's plan.
@Component({
  selector: 'app-analysis-readings-page',
  imports: [AwaitingPanelComponent, DecimalPipe, MeasurementGuideModalComponent, PlainWordsPipe, RouterLink, SectionHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analysis-readings-page.component.html',
  styles: `
    table { width: 100%; border-collapse: collapse; font-size: 0.875rem; }
    th {
      padding: 0 0.75rem 0.625rem;
      font-size: 0.6875rem;
      font-weight: 400;
      letter-spacing: 0.1em;
      text-align: left;
      text-transform: uppercase;
      white-space: nowrap;
      color: var(--color-ink-mute);
    }
    td {
      padding: 0.625rem 0.75rem;
      border-top: 1px solid var(--color-line);
      vertical-align: top;
      color: var(--color-ink-dim);
    }
    th:first-child, td:first-child { padding-left: 0; }
    th:last-child, td:last-child { padding-right: 0; }
    .r { text-align: right; white-space: nowrap; }
    .name {
      padding: 0;
      border: 0;
      background: none;
      color: var(--color-ink);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .name:hover { color: var(--color-signal); }
  `,
})
export class AnalysisReadingsPageComponent {
  protected readonly store = inject(MissionStore);

  protected readonly regionLabels = REGION_LABELS;
  protected readonly guideFor = signal<IndicatorId | null>(null);

  // the plan names its triggers by id; the label and today's value are looked up in the assessment
  protected readonly triggers = computed(() => {
    const assessment = this.store.assessment();
    const plan = this.store.plan();
    if (!assessment || !plan) return [];
    return plan.triggers.map((trigger) => {
      const indicator = assessment.indicators.find((i) => i.id === trigger.indicator);
      return {
        id: trigger.indicator,
        status: trigger.status,
        reasons: trigger.reasons,
        label: indicator?.label ?? trigger.indicator,
        value: indicator?.value ?? null,
        unit: indicator?.unit ?? '',
      };
    });
  });

  protected readonly delay = computed(() => {
    const assessment = this.store.assessment();
    return assessment ? delayText(assessment.comms.one_way_delay_s) : null;
  });
}
