import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { RegionAssessment, RegionId, Status } from '../../models/contract';
import { REGION_LABELS } from '../planner/planner-labels';

interface Spot {
  id: RegionId;
  // marker position on the stage, in percent
  x: number;
  y: number;
  // how far the warm patch reaches around the marker
  reach: [number, number];
  // which side the label sits on, and where its leader line ends
  side: 'left' | 'right';
  labelY: number;
}

// Positions are tied to body-wireframe.webp, which is centred in a square stage.
const SPOTS: Spot[] = [
  { id: 'HEAD_NEURO', x: 50, y: 12.5, reach: [9, 8], side: 'right', labelY: 12 },
  { id: 'CHEST_CARDIO', x: 52.3, y: 30, reach: [13, 8], side: 'right', labelY: 30 },
  { id: 'SYSTEMIC_IMMUNE_METABOLIC', x: 48.5, y: 41, reach: [10, 6], side: 'left', labelY: 40 },
  { id: 'SPINE_BONE', x: 50, y: 50.5, reach: [11, 6], side: 'left', labelY: 66 },
];

// The four body regions drawn over the wireframe figure. Colour, shape and word all come from
// the region status in the assessment; pressing a label or a marker selects that region.
@Component({
  selector: 'app-body-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host { display: block; }
    .stage {
      position: relative;
      width: 100%;
      max-width: 31rem;
      margin-inline: auto;
      aspect-ratio: 1;
    }
    .stage img {
      position: absolute;
      left: 50%;
      top: 0;
      height: 100%;
      width: auto;
      transform: translateX(-50%);
      pointer-events: none;
    }
    .stage svg {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      overflow: visible;
    }

    .green { --tone: var(--color-ok); }
    .yellow { --tone: var(--color-warn); }
    .red { --tone: var(--color-crit); }

    .heat { fill: var(--tone); opacity: 0.2; filter: blur(14px); }
    .heat.yellow { opacity: 0.34; }
    .heat.red { opacity: 0.5; }

    .lead { stroke: var(--tone); stroke-opacity: 0.55; stroke-width: 1; }
    .halo { fill: var(--tone); opacity: 0.28; }
    .dot { fill: var(--tone); stroke: var(--color-void); stroke-width: 1.5; }
    .mark { cursor: pointer; }
    .mark.on .halo { opacity: 0.5; }
    @media (prefers-reduced-motion: no-preference) {
      .mark.red .halo { animation: beat 1.6s ease-in-out infinite; }
      @keyframes beat { 50% { opacity: 0.6; } }
    }

    .tag {
      position: absolute;
      display: grid;
      gap: 0.25rem;
      justify-items: start;
      max-width: 27%;
      padding: 0.25rem 0.375rem;
      border: 1px solid transparent;
      border-radius: 0.625rem;
      background: none;
      color: var(--color-ink);
      font: inherit;
      text-align: left;
      transform: translateY(-50%);
      cursor: pointer;
    }
    .tag.right { left: 72.5%; }
    .tag.left { right: 72.5%; justify-items: end; text-align: right; }
    .tag:hover { border-color: var(--color-line); }
    .tag[aria-pressed='true'] {
      border-color: var(--color-line-strong);
      background: rgb(255 255 255 / 0.07);
    }
    .name {
      font-size: 0.6875rem;
      letter-spacing: 0.08em;
      line-height: 1.25;
      text-transform: uppercase;
    }
    .tag .status { font-size: 0.5625rem; padding: 0.0625rem 0.4375rem; }
  `,
  template: `
    <div class="stage" role="group" aria-label="Body regions">
      <svg aria-hidden="true">
        @for (spot of spots; track spot.id) {
          <ellipse
            [class]="'heat ' + tone(spot.id)"
            [attr.cx]="spot.x + '%'"
            [attr.cy]="spot.y + '%'"
            [attr.rx]="spot.reach[0] + '%'"
            [attr.ry]="spot.reach[1] + '%'"
          />
        }
      </svg>

      <img src="/assets/body-wireframe.webp" alt="" width="840" height="1125" />

      <svg aria-hidden="true">
        @for (spot of spots; track spot.id) {
          <g [class]="'mark ' + tone(spot.id)" [class.on]="selected() === spot.id" (click)="regionSelected.emit(spot.id)">
            <line
              class="lead"
              [attr.x1]="spot.x + '%'"
              [attr.y1]="spot.y + '%'"
              [attr.x2]="(spot.side === 'right' ? 72 : 28) + '%'"
              [attr.y2]="spot.labelY + '%'"
            />
            <circle class="halo" [attr.cx]="spot.x + '%'" [attr.cy]="spot.y + '%'" r="15" />
            <circle class="dot" [attr.cx]="spot.x + '%'" [attr.cy]="spot.y + '%'" r="6" />
          </g>
        }
      </svg>

      @for (spot of spots; track spot.id) {
        <button
          type="button"
          [class]="'tag ' + spot.side"
          [style.top.%]="spot.labelY"
          [attr.aria-pressed]="selected() === spot.id"
          (click)="regionSelected.emit(spot.id)"
        >
          <span class="name">{{ labels[spot.id] }}</span>
          <span [class]="'status status-pill status-' + tone(spot.id)">{{ statusOf(spot.id) }}</span>
        </button>
      }
    </div>
  `,
})
export class BodyMapComponent {
  readonly regions = input.required<Record<RegionId, RegionAssessment>>();
  readonly selected = input<RegionId | null>(null);
  readonly regionSelected = output<RegionId>();

  protected readonly spots = SPOTS;
  protected readonly labels = REGION_LABELS;

  protected statusOf(id: RegionId): Status {
    return this.regions()[id].status;
  }

  protected tone(id: RegionId): string {
    return this.statusOf(id).toLowerCase();
  }
}
