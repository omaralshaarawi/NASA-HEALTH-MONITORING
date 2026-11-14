import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { AlertStripComponent } from './alert-strip.component';
import { StatusStripComponent } from './status-strip.component';
import { TopNavComponent } from './top-nav.component';

// The frame around every page: space backdrop, top bar, alert strip, status strip.
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, TopNavComponent, AlertStripComponent, StatusStripComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .backdrop {
      position: fixed;
      inset: 0;
      /* sized from the window width so the planet sits right of centre, as in the design */
      background: var(--color-void) url('/assets/space-bg.webp') 60% top / max(105vw, 140vh) auto no-repeat;
    }
    .backdrop::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, rgb(5 7 10 / 0.45), rgb(5 7 10 / 0.15) 35%, rgb(5 7 10 / 0.6) 78%, var(--color-void));
      transition: background-color 300ms;
    }
    /* on a phone the text runs across the planet, so it is dimmed more */
    @media (max-width: 63.99rem) {
      .backdrop::after {
        background: linear-gradient(180deg, rgb(5 7 10 / 0.6), rgb(5 7 10 / 0.55) 40%, var(--color-void) 85%);
      }
    }
    /* inner pages keep the planet but push it back so text and tables stay readable */
    .backdrop.quiet::after { background: rgb(5 7 10 / 0.74); }
    .skip {
      position: absolute;
      left: 1rem;
      top: -4rem;
      z-index: 50;
    }
    .skip:focus { top: 1rem; }
    @media print {
      .backdrop { display: none; }
    }
  `,
  template: `
    <div class="backdrop" [class.quiet]="!hero()" aria-hidden="true"></div>
    <a class="skip btn btn-primary" href="#main">Skip to content</a>
    <div class="relative mx-auto flex min-h-screen max-w-[92rem] flex-col px-4 sm:px-6">
      <app-top-nav />
      <app-alert-strip />
      @if (!hero()) {
        <app-status-strip />
      }
      <main id="main" class="flex-1 pb-12" tabindex="-1"><router-outlet /></main>
    </div>
  `,
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  // the Home hero already carries the status line, so the strip is left out there
  protected readonly hero = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => {
        let page = this.route.snapshot;
        while (page.firstChild) page = page.firstChild;
        return page.data['hero'] === true;
      }),
    ),
    { initialValue: false },
  );
}
