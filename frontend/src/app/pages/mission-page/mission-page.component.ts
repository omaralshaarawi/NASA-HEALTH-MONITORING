import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { HabitatSwitcherComponent } from '../../components/dashboard/habitat-switcher.component';
import { MissionStore } from '../../core/mission-store.service';
import { Body, EnvironmentProfile, EnvironmentType, IndicatorId } from '../../models/contract';
import rules from '../../models/rules.json';
import { AwaitingPanelComponent } from '../../ui/awaiting-panel.component';
import { delayText } from '../../ui/format';
import { SectionHeaderComponent } from '../../ui/section-header.component';
import { StatusChipComponent } from '../../ui/status-chip.component';

const BODY: Record<Body, string> = {
  EARTH_ORBIT: 'Earth orbit',
  DEEP_SPACE: 'Deep space',
  MOON: 'Moon',
  MARS: 'Mars',
};

const KIND: Record<EnvironmentType, string> = {
  SPACE_STATION: 'Space station',
  DEEP_SPACECRAFT: 'Spacecraft in transit',
  PLANETARY_BASE: 'Surface base',
  SURFACE_ROVER: 'Surface rover',
};

// Where the crew member is and what that means: gravity, distance and signal delay to Earth,
// and the habitat's own readings. A different habitat can be simulated here.
@Component({
  selector: 'app-mission-page',
  imports: [AwaitingPanelComponent, DecimalPipe, HabitatSwitcherComponent, SectionHeaderComponent, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mission-page.component.html',
  styleUrl: './mission-page.component.css',
})
export class MissionPageComponent {
  protected readonly store = inject(MissionStore);

  protected readonly body = BODY;
  protected readonly kind = KIND;
  // above this one-way delay the crew works autonomously (rules.json)
  protected readonly autonomousAfterS = rules.constants.autonomous_delay_s;

  protected readonly delay = computed(() => {
    const assessment = this.store.assessment();
    return assessment ? delayText(assessment.comms.one_way_delay_s) : null;
  });

  // the three habitat readings, as the engine assessed them today
  protected readonly radiation = computed(() => this.indicator('radiation'));
  protected readonly co2 = computed(() => this.indicator('cabin_co2'));
  protected readonly resistive = computed(() => this.indicator('resistive'));

  protected chooseHabitat(environment: EnvironmentProfile): void {
    this.store.selectHabitat(environment.id, environment.distance_km);
  }

  protected chooseDistance(distanceKm: number): void {
    const assessment = this.store.assessment();
    if (assessment) this.store.selectHabitat(assessment.environment.id, distanceKm);
  }

  private indicator(id: IndicatorId) {
    return this.store.assessment()?.indicators.find((indicator) => indicator.id === id) ?? null;
  }
}
