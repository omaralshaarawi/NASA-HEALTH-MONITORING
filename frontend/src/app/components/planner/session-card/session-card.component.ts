import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CountermeasurePlanDraft, ResourceBlock } from '../../../models/contract';
import { ExerciseFigureComponent } from '../exercise-figure/exercise-figure.component';
import { exerciseTitle, menuItem } from '../planner-labels';
import { RegionBadgeComponent } from '../region-badge/region-badge.component';
import { ResourceChipsComponent } from '../resource-chips/resource-chips.component';

type Session = CountermeasurePlanDraft['sessions'][number];
type SessionResources = ResourceBlock['sessions'][number];

@Component({
  selector: 'app-session-card',
  imports: [ExerciseFigureComponent, RegionBadgeComponent, ResourceChipsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block' },
  templateUrl: './session-card.component.html',
})
export class SessionCardComponent {
  readonly session = input.required<Session>();
  readonly index = input.required<number>();
  // the resource row for this session, if the backend sent one
  readonly resources = input<SessionResources | null>(null);

  protected readonly title = computed(() => exerciseTitle(this.session().exercise_id));
  protected readonly item = computed(() => menuItem(this.session().exercise_id));
  protected readonly number = computed(() => String(this.index() + 1).padStart(2, '0'));
}
