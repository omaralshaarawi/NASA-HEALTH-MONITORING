import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { exerciseTitle } from '../../components/planner/planner-labels';
import { EmergencyService } from '../../core/emergency.service';
import { MissionStore } from '../../core/mission-store.service';
import { RegionId } from '../../models/contract';
import { STATUS_WORD, delayText } from '../../ui/format';
import { MetricTileComponent } from '../../ui/metric-tile.component';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, MetricTileComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home-page.component.html',
  styleUrl: './home-page.component.css',
})
export class HomePageComponent {
  protected readonly store = inject(MissionStore);
  protected readonly emergency = inject(EmergencyService);

  protected readonly regionIds: RegionId[] = ['HEAD_NEURO', 'CHEST_CARDIO', 'SPINE_BONE', 'SYSTEMIC_IMMUNE_METABOLIC'];
  // one word each: the card is narrow
  protected readonly regionNames: Record<RegionId, string> = {
    HEAD_NEURO: 'Head',
    CHEST_CARDIO: 'Chest',
    SPINE_BONE: 'Spine',
    SYSTEMIC_IMMUNE_METABOLIC: 'Immune',
  };
  protected readonly statusWord = STATUS_WORD;
  protected readonly exerciseTitle = exerciseTitle;

  // how many of today's indicators have a value
  protected readonly measured = computed(() => this.store.assessment()?.indicators.filter((i) => i.value !== null).length ?? 0);

  protected readonly plannedMinutes = computed(
    () => this.store.plan()?.draft.sessions.reduce((sum, session) => sum + session.minutes, 0) ?? 0,
  );

  protected readonly delay = computed(() => {
    const assessment = this.store.assessment();
    return assessment ? delayText(assessment.comms.one_way_delay_s) : null;
  });
}
