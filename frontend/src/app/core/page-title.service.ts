import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { MissionStore } from './mission-store.service';

// Browser tab title: the page name, with a warning in front while triage is recommended,
// so the emergency is visible from another tab.
@Injectable({ providedIn: 'root' })
export class PageTitle extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly store = inject(MissionStore);
  private readonly page = signal('');

  constructor() {
    super();
    effect(() => {
      const warning = this.store.assessment()?.triage_recommended ? '⚠ Emergency · ' : '';
      const page = this.page() ? this.page() + ' · ' : '';
      this.title.setTitle(warning + page + 'Crew Health');
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.page.set(this.buildTitle(snapshot) ?? '');
  }
}
