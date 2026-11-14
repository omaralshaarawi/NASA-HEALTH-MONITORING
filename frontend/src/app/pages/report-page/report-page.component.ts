import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MissionReportPrintComponent } from '../../components/planner/mission-report-print/mission-report-print.component';
import { ModalShellComponent } from '../../components/planner/modal-shell/modal-shell.component';
import { CREW } from '../../core/crew';
import { DailyLogService } from '../../core/daily-log.service';
import { MissionStore } from '../../core/mission-store.service';
import { TrainingService } from '../../core/training.service';
import { SectionHeaderComponent } from '../../ui/section-header.component';
import { StatusChipComponent } from '../../ui/status-chip.component';

// what the main area shows: today's report, a closed day's saved report, or an earlier day's values
type View = { kind: 'today' } | { kind: 'closed'; day: number } | { kind: 'earlier'; day: number };

@Component({
  selector: 'app-report-page',
  imports: [DatePipe, MissionReportPrintComponent, ModalShellComponent, RouterLink, SectionHeaderComponent, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './report-page.component.html',
  styleUrl: './report-page.component.css',
})
export class ReportPageComponent {
  protected readonly store = inject(MissionStore);
  private readonly log = inject(DailyLogService);
  private readonly training = inject(TrainingService);

  private readonly report = viewChild(MissionReportPrintComponent);

  protected readonly view = signal<View>({ kind: 'today' });
  protected readonly confirming = signal<'close' | 'reset' | null>(null);
  protected readonly resetting = signal(false);

  protected readonly closedDays = computed(() => this.log.of(this.store.crewId()));
  protected readonly crewName = computed(() => CREW.find((m) => m.id === this.store.crewId())?.name ?? '');

  // the day the crew member is on: today's assessment, or the day waiting for readings
  protected readonly currentDay = computed(() => this.store.assessment()?.mission_day ?? this.store.pendingDay());

  protected readonly selectedClosed = computed(() => {
    const view = this.view();
    return view.kind === 'closed' ? (this.closedDays().find((entry) => entry.day === view.day) ?? null) : null;
  });

  // Days before the first closed one have no saved report, only the values recorded in the
  // trend arrays. They are listed without a status, because none was saved for them.
  private readonly reference = computed(() => this.closedDays().at(-1)?.assessment ?? this.store.assessment());

  protected readonly earlierDays = computed(() => {
    const reference = this.reference();
    if (!reference) return [];
    const span = Math.max(...reference.indicators.map((indicator) => indicator.trend.length));
    const first = Math.max(1, reference.mission_day - span + 1);
    const days: number[] = [];
    for (let day = reference.mission_day - 1; day >= first; day--) days.push(day);
    return days;
  });

  protected readonly earlierValues = computed(() => {
    const view = this.view();
    const reference = this.reference();
    if (view.kind !== 'earlier' || !reference) return [];
    return reference.indicators.map((indicator) => {
      const index = indicator.trend.length - 1 - (reference.mission_day - view.day);
      return { label: indicator.label, unit: indicator.unit, value: index >= 0 ? (indicator.trend[index] ?? null) : null };
    });
  });

  constructor() {
    // another crew member: start again from their today
    effect(() => {
      this.store.crewId();
      untracked(() => this.view.set({ kind: 'today' }));
    });
  }

  protected isOpen(kind: View['kind'], day?: number): boolean {
    const view = this.view();
    return view.kind === kind && (view.kind === 'today' || view.day === day);
  }

  protected print(): void {
    this.report()?.print();
  }

  protected closeDay(): void {
    this.confirming.set(null);
    this.store.startNextDay();
    this.view.set({ kind: 'today' });
  }

  protected resetDemo(): void {
    this.resetting.set(true);
    this.training.clearAll();
    this.store.resetDemo().subscribe(() => {
      this.resetting.set(false);
      this.confirming.set(null);
      this.view.set({ kind: 'today' });
    });
  }
}
