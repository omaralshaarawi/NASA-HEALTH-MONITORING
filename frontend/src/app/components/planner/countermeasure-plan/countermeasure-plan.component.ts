import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CountermeasurePlan } from '../../../models/contract';
import { PlainWordsPipe } from '../../../ui/plain-words.pipe';
import { exerciseTitle, menuItem } from '../planner-labels';
import { SessionCardComponent } from '../session-card/session-card.component';
import { SessionTimerComponent } from '../session-timer/session-timer.component';

// Today's plan for one crew member: training sessions with their timers, nutrition, and what
// the plan costs life support. Display only: every number comes from the plan as received.
@Component({
  selector: 'app-countermeasure-plan',
  imports: [DecimalPipe, PlainWordsPipe, SessionCardComponent, SessionTimerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './countermeasure-plan.component.html',
})
export class CountermeasurePlanComponent {
  readonly plan = input.required<CountermeasurePlan>();

  protected readonly draft = computed(() => this.plan().draft);
  protected readonly resources = computed(() => this.plan().resources);

  // how much of the total extra energy each session accounts for, for the bar in the life-support panel
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

  protected needsDirection(exerciseId: string): boolean {
    return menuItem(exerciseId)?.requires_flight_surgeon_direction === true;
  }

  protected signed(n: number): string {
    return n > 0 ? '+' + n : String(n);
  }
}
