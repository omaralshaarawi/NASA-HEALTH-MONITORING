import { Injectable, computed, inject } from '@angular/core';
import { Router } from '@angular/router';

import { MissionStore } from './mission-store.service';
import { VoiceCallService } from './voice/voice-call.service';

@Injectable({ providedIn: 'root' })
export class EmergencyService {
  private readonly store = inject(MissionStore);
  private readonly router = inject(Router);
  private readonly voice = inject(VoiceCallService);

  readonly active = computed(() => this.store.assessment()?.triage_recommended === true);

  readonly label = computed(() =>
    this.store.assessment()?.comms.emergency_action === 'CALL_FLIGHT_SURGEON'
      ? 'Call flight surgeon'
      : 'Autonomous triage + queue packet',
  );

  open(): void {
    // already in a call: just show it
    const incidentId = this.voice.incidentId();
    if (this.voice.active()) {
      void this.router.navigate(['/triage'], {
        queryParams: incidentId ? { incident: incidentId } : {},
      });
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
