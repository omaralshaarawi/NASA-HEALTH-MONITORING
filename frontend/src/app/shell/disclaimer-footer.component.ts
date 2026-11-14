import { ChangeDetectionStrategy, Component } from '@angular/core';

import { DISCLAIMER } from '../models/contract';

@Component({
  selector: 'app-disclaimer-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block print:hidden' },
  template: `<footer class="mt-12 border-t border-line py-5 text-xs leading-5 text-ink-mute">{{ disclaimer }}</footer>`,
})
export class DisclaimerFooterComponent {
  protected readonly disclaimer = DISCLAIMER;
}
