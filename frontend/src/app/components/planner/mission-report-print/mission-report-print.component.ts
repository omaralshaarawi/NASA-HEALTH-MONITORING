import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, computed, input, viewChild } from '@angular/core';

import { AssessmentResponse, CountermeasurePlan, DISCLAIMER, RegionId } from '../../../models/contract';
import { REGION_LABELS, exerciseTitle } from '../planner-labels';
import { SparklineComponent } from '../sparkline/sparkline.component';

// One-page A4 report for the flight surgeon: 14-day readings against the personal baseline,
// flags, the active plan and its resource estimate. Call print(), or just use the browser's print.
@Component({
  selector: 'app-mission-report-print',
  imports: [DatePipe, DecimalPipe, SparklineComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mission-report-print.component.html',
  styleUrl: './mission-report-print.component.css',
})
export class MissionReportPrintComponent implements OnDestroy {
  readonly assessment = input.required<AssessmentResponse>();
  readonly plan = input<CountermeasurePlan | null>(null);
  readonly crewName = input('');

  protected readonly disclaimer = DISCLAIMER;
  protected readonly exerciseTitle = exerciseTitle;

  private readonly sheet = viewChild.required<ElementRef<HTMLElement>>('sheet');
  private restore: (() => void) | null = null;

  protected readonly regions = computed(() =>
    Object.values(this.assessment().regions).map((r) => ({
      label: REGION_LABELS[r.id as RegionId],
      status: r.status,
      drivers: r.drivers
        .map((id) => this.assessment().indicators.find((i) => i.id === id)?.label ?? id)
        .join(', '),
    })),
  );

  protected readonly delay = computed(() => {
    const s = this.assessment().comms.one_way_delay_s;
    return s >= 60 ? (s / 60).toFixed(1) + ' min' : s + ' s';
  });

  constructor() {
    // also covers Ctrl+P while the report is on screen
    window.addEventListener('beforeprint', this.beforePrint);
    window.addEventListener('afterprint', this.afterPrint);
  }

  ngOnDestroy(): void {
    window.removeEventListener('beforeprint', this.beforePrint);
    window.removeEventListener('afterprint', this.afterPrint);
    this.afterPrint();
  }

  print(): void {
    window.print();
  }

  protected region(id: string): string {
    return REGION_LABELS[id as RegionId] ?? id;
  }

  // While printing, the sheet is moved to <body> and everything else is hidden (see styles.css).
  // That keeps the dark app, the dialog frame and their scroll containers out of the printout.
  private readonly beforePrint = (): void => {
    if (this.restore) {
      return;
    }
    const sheet = this.sheet().nativeElement;
    const parent = sheet.parentElement;
    const next = sheet.nextSibling;
    document.body.appendChild(sheet);
    document.body.classList.add('printing-report');
    document.documentElement.classList.add('printing-report');
    this.restore = () => {
      parent?.insertBefore(sheet, next);
      document.body.classList.remove('printing-report');
      document.documentElement.classList.remove('printing-report');
    };
  };

  private readonly afterPrint = (): void => {
    this.restore?.();
    this.restore = null;
  };
}
