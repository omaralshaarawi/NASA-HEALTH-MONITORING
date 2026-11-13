import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';

import { CountermeasurePlanComponent } from '../../components/planner/countermeasure-plan/countermeasure-plan.component';
import { MissionReportPrintComponent } from '../../components/planner/mission-report-print/mission-report-print.component';
import { ModalShellComponent } from '../../components/planner/modal-shell/modal-shell.component';
import { Attachment, ScanKind } from '../../components/planner/ultrasound-attachment-modal/scan-guides';
import { UltrasoundAttachmentModalComponent } from '../../components/planner/ultrasound-attachment-modal/ultrasound-attachment-modal.component';
import { MeasurementGuideModalComponent } from '../../components/planner/measurement-guide-modal/measurement-guide-modal.component';
import { statusClass } from '../../components/planner/planner-labels';
import { TopBarComponent } from '../../components/dashboard/top-bar.component';
import { CREW } from '../../core/crew';
import { ApiService, GeneratedPlan, PlanSource } from '../../core/api.service';
import { MOCK_ASSESSMENTS, MOCK_PLANS } from '../../mocks/mock-data';
import { AssessmentResponse, CountermeasurePlan, IndicatorId } from '../../models/contract';

const SOURCE_TEXT: Record<PlanSource, string> = {
  LIVE: 'Live answer from the planner',
  SAVED: 'Saved plan for this assessment',
  OFFLINE: 'Planner offline: rule-based plan',
  MOCK: 'Backend not reachable: bundled demo data',
};

@Component({
  selector: 'app-planner-page',
  imports: [
    CountermeasurePlanComponent,
    MeasurementGuideModalComponent,
    MissionReportPrintComponent,
    ModalShellComponent,
    TopBarComponent,
    UltrasoundAttachmentModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './planner-page.component.html',
})
export class PlannerPageComponent {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly crew = CREW;
  protected readonly statusClass = statusClass;

  protected readonly selectedId = signal('ast_kai');
  protected readonly assessment = signal<AssessmentResponse | null>(null);
  protected readonly plan = signal<CountermeasurePlan | null>(null);
  protected readonly source = signal<PlanSource>('MOCK');
  protected readonly loading = signal(false);

  protected readonly sourceText = computed(() => SOURCE_TEXT[this.source()]);
  protected readonly selected = computed(() => CREW.find((c) => c.id === this.selectedId()) ?? CREW[0]);

  // the measurement guide currently open, if any
  protected readonly guideFor = signal<IndicatorId | null>(null);
  protected readonly scanOpen = signal<ScanKind | null>(null);
  protected readonly reportOpen = signal(false);

  // captures attached in this session, per crew member (the backend has no endpoint for them yet)
  private readonly attachments = signal<Record<string, Attachment[]>>({});
  protected readonly todaysAttachments = computed(() => this.attachments()[this.selectedId()] ?? []);

  constructor() {
    // deep links for the demo: /planner?crew=ast_lin&guide=sleep
    const query = this.route.snapshot.queryParamMap;
    const crew = query.get('crew');
    if (crew && CREW.some((c) => c.id === crew)) {
      this.selectedId.set(crew);
    }
    const guide = query.get('guide');
    if (guide) {
      this.guideFor.set(guide as IndicatorId);
    }
    const scan = query.get('scan');
    if (scan) {
      this.scanOpen.set(scan as ScanKind);
    }
    this.reportOpen.set(query.has('report'));
    this.load(this.selectedId());
  }

  protected addAttachment(attachment: Attachment): void {
    const id = this.selectedId();
    this.attachments.update((all) => ({ ...all, [id]: [...(all[id] ?? []), attachment] }));
  }

  protected scanLabel(kind: ScanKind): string {
    return kind === 'ECG' ? 'ECG' : kind === 'OCULAR_ULTRASOUND' ? 'Eye ultrasound' : 'Heart ultrasound';
  }

  protected select(id: string): void {
    if (id !== this.selectedId()) {
      this.selectedId.set(id);
      this.load(id);
    }
  }

  // fresh = ask the LLM again instead of reusing the saved plan
  protected load(id: string, fresh = false): void {
    this.loading.set(true);
    this.api
      .assessment(id)
      .pipe(
        switchMap((assessment) => {
          this.assessment.set(assessment);
          return this.api.generatePlan(assessment, fresh);
        }),
        catchError(() => {
          // no backend: fall back to the bundled day-14 data so the page still works
          this.assessment.set(MOCK_ASSESSMENTS[id]);
          return of<GeneratedPlan>({ plan: MOCK_PLANS[id], source: 'MOCK' });
        }),
      )
      .subscribe((result) => {
        this.plan.set(result.plan);
        this.source.set(result.source);
        this.loading.set(false);
      });
  }
}
