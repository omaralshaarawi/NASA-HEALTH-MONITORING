import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { AssessmentResponse, CountermeasurePlan, IndicatorId } from '../../../models/contract';
import { exerciseTitle, statusClass } from '../planner-labels';
import { SessionCardComponent } from '../session-card/session-card.component';

// The full countermeasure plan for one astronaut: briefing, sessions, nutrition and the
// "close the loop" resource estimate. Display only: every number comes from the backend.
@Component({
  selector: 'app-countermeasure-plan',
  imports: [DatePipe, DecimalPipe, SessionCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './countermeasure-plan.component.html',
})
export class CountermeasurePlanComponent {
  readonly plan = input.required<CountermeasurePlan>();
  readonly assessment = input.required<AssessmentResponse>();

  // a trigger was clicked: the host can open the measurement guide for it
  readonly indicatorSelected = output<IndicatorId>();

  protected readonly statusClass = statusClass;

  protected readonly draft = computed(() => this.plan().draft);
  protected readonly resources = computed(() => this.plan().resources);
  protected readonly writtenByAi = computed(() => this.plan().llm.model !== 'offline-fallback');
  protected readonly emergency = computed(
    () => this.assessment().overall === 'RED' || this.assessment().triage_recommended,
  );

  // triggers come from the plan; the label and today's value are looked up in the assessment
  protected readonly triggers = computed(() =>
    this.plan().triggers.map((t) => {
      const indicator = this.assessment().indicators.find((i) => i.id === t.indicator);
      return {
        id: t.indicator,
        status: t.status,
        label: indicator?.label ?? t.indicator,
        value: indicator?.value ?? null,
        unit: indicator?.unit ?? '',
        reason: t.reasons[0] ?? '',
      };
    }),
  );

  // how much of the total extra energy each session accounts for, for the bar in the resource panel
  protected readonly shares = computed(() => {
    const total = this.resources().totals.extra_kcal;
    return this.resources().sessions.map((s) => ({
      title: exerciseTitle(s.exercise_id),
      kcal: s.extra_kcal,
      percent: total > 0 ? (s.extra_kcal / total) * 100 : 0,
    }));
  });

  protected sessionResources(index: number) {
    return this.resources().sessions[index] ?? null;
  }

  protected signed(n: number): string {
    return n > 0 ? '+' + n : String(n);
  }
}
