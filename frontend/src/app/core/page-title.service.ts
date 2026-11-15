import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { MissionStore } from './mission-store.service';

// warning prefix so an emergency shows up in other tabs
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
      this.title.setTitle(warning + page + 'Astropital');
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.page.set(this.buildTitle(snapshot) ?? '');
  }
}
