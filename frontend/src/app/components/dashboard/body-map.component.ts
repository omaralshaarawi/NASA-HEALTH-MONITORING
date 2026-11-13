import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { RegionAssessment, RegionId, Status } from '../../models/contract';
import { REGION_LABELS } from '../planner/planner-labels';

@Component({
  selector: 'app-body-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    @media (prefers-reduced-motion: no-preference) {
      .red-zone { animation: status-pulse 1.6s ease-in-out infinite; }
      @keyframes status-pulse { 50% { opacity: 0.48; } }
    }
  `,
  template: `
    <div class="panel p-4 sm:p-5">
      <div class="eyebrow">Crew status · select a region</div>
      <div class="mt-3 flex justify-center">
        <svg viewBox="0 0 300 340" class="h-auto w-full max-w-[19rem]" role="group" aria-label="Crew health body map">
          <circle cx="150" cy="42" r="28" fill="#16212f" stroke="#52647a" stroke-width="2" />
          <path d="M124 78 Q150 68 176 78 L197 169 Q190 183 177 178 L180 131 L190 215 L176 215 L170 301 L151 301 L146 218 L140 301 L121 301 L117 215 L103 215 L114 131 L106 178 Q93 183 86 169 L103 93 Q111 82 124 78Z"
                fill="#16212f" stroke="#52647a" stroke-width="2" stroke-linejoin="round" />
          <g class="cursor-pointer" [class.red-zone]="regionStatus('HEAD_NEURO') === 'RED'" role="button" tabindex="0" (click)="select('HEAD_NEURO')" (keydown.enter)="select('HEAD_NEURO')" (keydown.space)="select('HEAD_NEURO')"
             [attr.aria-label]="regionLabel('HEAD_NEURO') + ' ' + regionStatus('HEAD_NEURO')">
            <circle cx="150" cy="42" r="25" [attr.fill]="fill('HEAD_NEURO')" fill-opacity="0.82" stroke="#e8eef6" stroke-opacity="0.55" />
            @if (regionStatus('HEAD_NEURO') === 'GREEN') { <circle cx="150" cy="42" r="4" fill="#060a10" /> }
            @if (regionStatus('HEAD_NEURO') === 'YELLOW') { <polygon points="150,36 156,42 150,48 144,42" fill="#060a10" /> }
            @if (regionStatus('HEAD_NEURO') === 'RED') { <rect x="146" y="38" width="8" height="8" fill="#060a10" /> }
          </g>
          <g class="cursor-pointer" [class.red-zone]="regionStatus('CHEST_CARDIO') === 'RED'" role="button" tabindex="0" (click)="select('CHEST_CARDIO')" (keydown.enter)="select('CHEST_CARDIO')" (keydown.space)="select('CHEST_CARDIO')"
             [attr.aria-label]="regionLabel('CHEST_CARDIO') + ' ' + regionStatus('CHEST_CARDIO')">
            <path d="M112 92 Q150 78 188 92 L180 143 Q150 155 120 143Z" [attr.fill]="fill('CHEST_CARDIO')" fill-opacity="0.82" stroke="#e8eef6" stroke-opacity="0.55" />
            @if (regionStatus('CHEST_CARDIO') === 'GREEN') { <circle cx="150" cy="118" r="4" fill="#060a10" /> }
            @if (regionStatus('CHEST_CARDIO') === 'YELLOW') { <polygon points="150,112 156,118 150,124 144,118" fill="#060a10" /> }
            @if (regionStatus('CHEST_CARDIO') === 'RED') { <rect x="146" y="114" width="8" height="8" fill="#060a10" /> }
          </g>
          <g class="cursor-pointer" [class.red-zone]="regionStatus('SPINE_BONE') === 'RED'" role="button" tabindex="0" (click)="select('SPINE_BONE')" (keydown.enter)="select('SPINE_BONE')" (keydown.space)="select('SPINE_BONE')"
             [attr.aria-label]="regionLabel('SPINE_BONE') + ' ' + regionStatus('SPINE_BONE')">
            <path d="M122 150 L178 150 L184 213 L169 213 L166 275 L151 275 L148 214 L144 275 L129 275 L126 213 L111 213Z" [attr.fill]="fill('SPINE_BONE')" fill-opacity="0.82" stroke="#e8eef6" stroke-opacity="0.55" />
            @if (regionStatus('SPINE_BONE') === 'GREEN') { <circle cx="150" cy="186" r="4" fill="#060a10" /> }
            @if (regionStatus('SPINE_BONE') === 'YELLOW') { <polygon points="150,180 156,186 150,192 144,186" fill="#060a10" /> }
            @if (regionStatus('SPINE_BONE') === 'RED') { <rect x="146" y="182" width="8" height="8" fill="#060a10" /> }
          </g>
          <g class="cursor-pointer" [class.red-zone]="regionStatus('SYSTEMIC_IMMUNE_METABOLIC') === 'RED'" role="button" tabindex="0" (click)="select('SYSTEMIC_IMMUNE_METABOLIC')" (keydown.enter)="select('SYSTEMIC_IMMUNE_METABOLIC')" (keydown.space)="select('SYSTEMIC_IMMUNE_METABOLIC')"
             [attr.aria-label]="regionLabel('SYSTEMIC_IMMUNE_METABOLIC') + ' ' + regionStatus('SYSTEMIC_IMMUNE_METABOLIC')">
            <ellipse cx="150" cy="160" rx="25" ry="13" [attr.fill]="fill('SYSTEMIC_IMMUNE_METABOLIC')" fill-opacity="0.82" stroke="#e8eef6" stroke-opacity="0.55" />
            @if (regionStatus('SYSTEMIC_IMMUNE_METABOLIC') === 'GREEN') { <circle cx="150" cy="160" r="4" fill="#060a10" /> }
            @if (regionStatus('SYSTEMIC_IMMUNE_METABOLIC') === 'YELLOW') { <polygon points="150,154 156,160 150,166 144,160" fill="#060a10" /> }
            @if (regionStatus('SYSTEMIC_IMMUNE_METABOLIC') === 'RED') { <rect x="146" y="156" width="8" height="8" fill="#060a10" /> }
          </g>
          <text x="150" y="326" fill="#a9b8ca" font-size="10" text-anchor="middle" font-family="IBM Plex Mono, monospace">STATUS FROM LATEST ASSESSMENT</text>
        </svg>
      </div>
      <div class="mt-2 flex flex-wrap justify-center gap-2">
        @for (id of regionIds; track id) {
          <button type="button" class="chip" (click)="select(id)">
            <span [class]="statusClass(regionStatus(id))"><span class="sr-only">{{ regionStatus(id) }}</span></span>
            {{ regionStatus(id) }} ·
            {{ regionLabel(id) }}
          </button>
        }
      </div>
    </div>
  `,
})
export class BodyMapComponent {
  readonly regions = input.required<Record<RegionId, RegionAssessment>>();
  readonly regionSelected = output<RegionId>();
  protected readonly regionIds: RegionId[] = ['HEAD_NEURO', 'CHEST_CARDIO', 'SPINE_BONE', 'SYSTEMIC_IMMUNE_METABOLIC'];

  protected regionStatus(id: RegionId): Status {
    return this.regions()[id]?.status ?? 'GREEN';
  }

  protected regionLabel(id: RegionId): string {
    return REGION_LABELS[id];
  }

  protected fill(id: RegionId): string {
    return this.regionStatus(id) === 'RED' ? '#ff6464' : this.regionStatus(id) === 'YELLOW' ? '#f5b83d' : '#3ddc97';
  }

  protected statusClass(status: Status): string {
    return 'status status-' + status.toLowerCase();
  }

  protected select(id: RegionId): void {
    this.regionSelected.emit(id);
  }
}