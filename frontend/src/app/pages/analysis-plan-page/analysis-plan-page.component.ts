import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { CountermeasurePlanComponent } from '../../components/planner/countermeasure-plan/countermeasure-plan.component';
import { CvCoachModalComponent } from '../../components/planner/cv-coach-modal/cv-coach-modal.component';
import { REGION_LABELS, exerciseTitle } from '../../components/planner/planner-labels';
import { MissionStore } from '../../core/mission-store.service';
import { TrainingService } from '../../core/training.service';
import { AwaitingPanelComponent } from '../../ui/awaiting-panel.component';
import { SectionHeaderComponent } from '../../ui/section-header.component';

// Today's training sessions with their timers, the nutrition change and the life-support cost.
@Component({
  selector: 'app-analysis-plan-page',
  imports: [AwaitingPanelComponent, CountermeasurePlanComponent, CvCoachModalComponent, SectionHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analysis-plan-page.component.html',
})
export class AnalysisPlanPageComponent {
  protected readonly store = inject(MissionStore);
  protected readonly training = inject(TrainingService);

  // the exercise the camera coach is open for, if any
  protected readonly coachFor = signal<string | null>(null);

  // the plan in one sentence, put together from its own fields
  protected readonly summary = computed(() => {
    const plan = this.store.plan();
    if (!plan) return '';
    const { sessions, nutrition } = plan.draft;
    const kcal = (nutrition.kcal_adjustment > 0 ? '+' : '') + nutrition.kcal_adjustment;
    const food = nutrition.kcal_adjustment
      ? `Nutrition is ${kcal} kcal against the planned ration.`
      : 'Nutrition stays on the planned ration.';
    if (!sessions.length) return `No training is planned today. ${food}`;
    const regions = [...new Set(sessions.map((s) => REGION_LABELS[s.target_system]))].join(', ');
    const count = sessions.length === 1 ? 'One session' : `${sessions.length} sessions`;
    return `${count} today, ${this.training.summary().minutesPlanned} minutes in total, for ${regions}. ${food}`;
  });

  protected readonly announcement = computed(() => {
    const id = this.training.lastCompleted();
    return id ? `${exerciseTitle(id)} completed.` : '';
  });
}
