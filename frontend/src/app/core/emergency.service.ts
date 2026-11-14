import { Injectable, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { MissionStore } from './mission-store.service';
import { VoiceCallService } from './voice/voice-call.service';

// The emergency action, shared by the alert strip and the Home card so both do exactly the same thing:
// open the triage page, which starts the checklist and the voice call.
@Injectable({ providedIn: 'root' })
export class EmergencyService {
  private readonly store = inject(MissionStore);
  private readonly router = inject(Router);
  private readonly voice = inject(VoiceCallService);

  readonly active = computed(() => this.store.assessment()?.triage_recommended === true);

  // wording for comms.emergency_action
  readonly label = computed(() =>
    this.store.assessment()?.comms.emergency_action === 'CALL_FLIGHT_SURGEON'
      ? 'Call flight surgeon'
      : 'Autonomous triage + queue packet',
  );

  open(): void {
    // a call is already connecting or running: show it, never start a second one
    const incidentId = this.voice.incidentId();
    if (this.voice.active()) {
      void this.router.navigate(['/triage'], { queryParams: incidentId ? { incident: incidentId } : {} });
      return;
    }
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
