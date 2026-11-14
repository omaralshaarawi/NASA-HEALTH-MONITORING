import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';

import { DropdownDirective } from '../ui/dropdown.directive';
import { AstronautSwitcherComponent } from './astronaut-switcher.component';

const ANALYSIS_PAGES = [
  { path: '/analysis/readings', label: "Today's Readings & Triggers" },
  { path: '/analysis/plan', label: 'Training & Nutrition Plan' },
];

@Component({
  selector: 'app-top-nav',
  imports: [RouterLink, RouterLinkActive, DropdownDirective, AstronautSwitcherComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './top-nav.component.html',
  styleUrl: './top-nav.component.css',
})
export class TopNavComponent {
  private readonly router = inject(Router);

  protected readonly analysisPages = ANALYSIS_PAGES;
  protected readonly mobileOpen = signal(false);
  protected readonly mobileAnalysisOpen = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );
  protected readonly inAnalysis = computed(() => this.url().startsWith('/analysis'));

  protected closeMobile(): void {
    this.mobileOpen.set(false);
  }
}
