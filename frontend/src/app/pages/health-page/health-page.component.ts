import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { BodyMapComponent } from '../../components/dashboard/body-map.component';
import { IndicatorListComponent } from '../../components/dashboard/indicator-list.component';
import { MeasurementFormModalComponent } from '../../components/dashboard/measurement-form-modal.component';
import { MeasurementGuideModalComponent } from '../../components/planner/measurement-guide-modal/measurement-guide-modal.component';
import { REGION_LABELS } from '../../components/planner/planner-labels';
import { SCAN_GUIDES } from '../../components/planner/ultrasound-attachment-modal/scan-guides';
import { UltrasoundAttachmentModalComponent } from '../../components/planner/ultrasound-attachment-modal/ultrasound-attachment-modal.component';
import { apiErrorMessage } from '../../core/api.service';
import { MissionStore } from '../../core/mission-store.service';
import { AttachmentKind } from '../../models-local/attachments';
import { DailyMeasurementAndCompliance, IndicatorId, RegionId } from '../../models/contract';
import { SectionHeaderComponent } from '../../ui/section-header.component';
import { StatusChipComponent } from '../../ui/status-chip.component';

// What each region stands for, in the crew member's words rather than the engine's.
const REGION_NOTES: Record<RegionId, string> = {
  HEAD_NEURO:
    'Sleep, mood and reaction time. Without gravity, body fluid moves towards the head, so rest and alertness are watched closely.',
  CHEST_CARDIO:
    'Resting heart rate and blood pressure. The heart and vessels adapt to weightlessness, so a change from your own normal matters more than a textbook range.',
  SPINE_BONE:
    'Resistive exercise against the daily target. Bone and muscle that carry no load weaken, so missed sessions are counted.',
  SYSTEMIC_IMMUNE_METABOLIC:
    'Temperature, glucose, rations, cabin air and radiation. Together they show how the whole body, and the habitat around it, are coping.',
};

@Component({
  selector: 'app-health-page',
  imports: [
    BodyMapComponent,
    IndicatorListComponent,
    MeasurementFormModalComponent,
    MeasurementGuideModalComponent,
    SectionHeaderComponent,
    StatusChipComponent,
    UltrasoundAttachmentModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './health-page.component.html',
})
export class HealthPageComponent {
  protected readonly store = inject(MissionStore);

  protected readonly regionLabels = REGION_LABELS;
  protected readonly regionNotes = REGION_NOTES;

  protected readonly region = signal<RegionId | null>(null);
  protected readonly guideFor = signal<IndicatorId | null>(null);
  protected readonly scanOpen = signal(false);
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal('');
  protected readonly notice = signal('');

  // labels of the indicators that set the selected region's status
  protected readonly drivers = computed(() => {
    const assessment = this.store.assessment();
    const region = this.region();
    if (!assessment || !region) return [];
    return assessment.regions[region].drivers.map(
      (id) => assessment.indicators.find((indicator) => indicator.id === id)?.label ?? id,
    );
  });

  protected toggleRegion(id: RegionId): void {
    this.region.set(this.region() === id ? null : id);
  }

  protected scanName(kind: AttachmentKind): string {
    return SCAN_GUIDES.find((guide) => guide.kind === kind)?.tab ?? kind;
  }

  protected openForm(): void {
    this.formError.set('');
    this.formOpen.set(true);
  }

  protected save(measurement: DailyMeasurementAndCompliance): void {
    this.saving.set(true);
    this.formError.set('');
    this.store.logReadings(measurement).subscribe({
      next: (assessment) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.notice.set(`Mission day ${assessment.mission_day} readings saved.`);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.formError.set(apiErrorMessage(error, 'These readings could not be saved. Check the connection and try again.'));
      },
    });
  }
}
