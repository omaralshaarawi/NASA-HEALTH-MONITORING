import { Injectable, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { MissionStore } from './mission-store.service';

// The emergency action, shared by the alert strip and the Home card so both do exactly the same thing.
@Injectable({ providedIn: 'root' })
export class EmergencyService {
  private readonly store = inject(MissionStore);
  private readonly router = inject(Router);

  readonly active = computed(() => this.store.assessment()?.triage_recommended === true);

  // wording for comms.emergency_action
  readonly label = computed(() =>
    this.store.assessment()?.comms.emergency_action === 'CALL_FLIGHT_SURGEON'
      ? 'Call flight surgeon'
      : 'Autonomous triage + queue packet',
  );

  open(): void {
    const assessment = this.store.assessment();
    if (!assessment) return;
    void this.router.navigate(['/triage'], {
      queryParams: {
        patient: assessment.astronaut_id,
        env: assessment.environment.id,
        distanceKm: assessment.environment.distance_km,
        day: assessment.mission_day,
        autostart: 1,
      },
    });
  }
}
