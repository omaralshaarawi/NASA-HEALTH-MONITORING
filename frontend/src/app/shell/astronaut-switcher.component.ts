import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { MissionStore } from '../core/mission-store.service';
import { DropdownDirective } from '../ui/dropdown.directive';

// Crew switcher in the top bar. There is no login: picking a name changes every page.
@Component({
  selector: 'app-astronaut-switcher',
  imports: [DropdownDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .trigger {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      height: 2.75rem;
      padding: 0 0.875rem 0 0.375rem;
      border: 1px solid var(--color-line);
      border-radius: 999px;
      background: rgb(255 255 255 / 0.05);
      backdrop-filter: blur(var(--blur-glass));
      color: var(--color-ink);
      font: inherit;
      font-size: 0.875rem;
      cursor: pointer;
    }
    .trigger:hover { border-color: var(--color-line-strong); }
    .avatar {
      display: grid;
      place-items: center;
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      background: var(--color-signal-dim);
      color: var(--color-ink);
      font-size: 0.8125rem;
      font-weight: 500;
    }
    .list {
      position: absolute;
      right: 0;
      top: calc(100% + 0.5rem);
      z-index: 40;
      min-width: 13rem;
      padding: 0.375rem;
      border: 1px solid var(--color-line);
      border-radius: 1rem;
      background: rgb(16 20 26 / 0.92);
      backdrop-filter: blur(var(--blur-glass));
    }
    .item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.5rem 0.625rem;
      border: 0;
      border-radius: 0.625rem;
      background: none;
      color: var(--color-ink-dim);
      font: inherit;
      font-size: 0.875rem;
      text-align: left;
      cursor: pointer;
    }
    .item:hover, .item[aria-checked='true'] { background: rgb(255 255 255 / 0.08); color: var(--color-ink); }
    .tick { width: 0.875rem; visibility: hidden; }
    .item[aria-checked='true'] .tick { visibility: visible; }
  `,
  template: `
    <div appDropdown #menu="dropdown" class="relative">
      <button type="button" class="trigger" aria-haspopup="menu" [attr.aria-expanded]="menu.open()" (click)="menu.toggle()">
        <span class="avatar" aria-hidden="true">{{ store.member().name.charAt(0) }}</span>
        <span><span class="sr-only">Crew member: </span>Ast. {{ store.member().name }}</span>
        <svg viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
          <path d="M2.5 4.5 6 8l3.5-3.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>

      @if (menu.open()) {
        <div class="list" role="menu" aria-label="Crew member">
          @for (member of crew; track member.id) {
            <button
              type="button"
              class="item"
              role="menuitemradio"
              data-item
              [attr.aria-checked]="member.id === store.crewId()"
              (click)="store.selectCrew(member.id); menu.close()"
            >
              <svg class="tick" viewBox="0 0 12 12" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">
                <path d="M2 6.5 5 9l5-6" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <span class="flex-1">Ast. {{ member.name }}</span>
              @if (store.statusOf(member.id); as status) {
                <span [class]="'status status-' + status.toLowerCase()">{{ status }}</span>
              }
            </button>
          }
        </div>
      }
    </div>
  `,
})
export class AstronautSwitcherComponent {
  protected readonly store = inject(MissionStore);
  // listed by name, as on the design
  protected readonly crew = [...this.store.crew].sort((a, b) => a.name.localeCompare(b.name));
}
