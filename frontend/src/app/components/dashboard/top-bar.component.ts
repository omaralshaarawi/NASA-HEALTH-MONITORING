import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AssessmentResponse } from '../../models/contract';

@Component({
  selector: 'app-top-bar',
  imports: [RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="flex flex-wrap items-center justify-between gap-4 border-b border-line py-4">
      <a routerLink="/" class="flex items-center gap-3 no-underline" aria-label="Crew Health Console dashboard">
        <svg viewBox="0 0 32 32" width="32" height="32" fill="none" aria-hidden="true">
          <circle cx="16" cy="16" r="6" fill="#e8eef6" />
          <ellipse cx="16" cy="16" rx="14" ry="5.5" stroke="#5ad1f5" stroke-width="1.5" transform="rotate(-24 16 16)" />
          <circle cx="27.6" cy="10.4" r="1.8" fill="#5ad1f5" />
        </svg>
        <span>
          <span class="display block text-xl leading-5 text-ink">Crew Health Console</span>
          <span class="eyebrow">Mission health · crew operations</span>
        </span>
      </a>
      <nav class="flex items-center gap-2" aria-label="Main navigation">
        <a routerLink="/" routerLinkActive="border-signal text-ink" [routerLinkActiveOptions]="{ exact: true }" class="btn">Dashboard</a>
        <a routerLink="/planner" routerLinkActive="border-signal text-ink" class="btn">Planner</a>
      </nav>
      @if (assessment(); as a) {
        <div class="flex w-full flex-wrap items-center gap-2 md:w-auto">
          <span class="chip">{{ a.environment.label }}</span>
          <span class="chip num">{{ a.environment.gravity_g }} g</span>
          <span class="chip" [class.border-signal]="a.comms.mode === 'AUTONOMOUS'" [class.text-signal]="a.comms.mode === 'AUTONOMOUS'">
            {{ a.comms.mode === 'AUTONOMOUS' ? 'Autonomous · no live link' : 'Live link to Earth' }}
          </span>
        </div>
      }
    </header>
  `,
})
export class TopBarComponent {
  readonly assessment = input<AssessmentResponse | null>(null);
}