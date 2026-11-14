import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { SectionHeaderComponent } from '../../ui/section-header.component';

// Stands in for a page until its build phase. The route supplies the heading.
@Component({
  selector: 'app-placeholder-page',
  imports: [SectionHeaderComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-section-header [eyebrow]="eyebrow" [heading]="heading">This page is being rebuilt in the new layout.</app-section-header>
  `,
})
export class PlaceholderPageComponent {
  private readonly route = inject(ActivatedRoute).snapshot;

  protected readonly eyebrow: string = this.route.data['eyebrow'] ?? '';
  protected readonly heading: string = this.route.title ?? '';
}
