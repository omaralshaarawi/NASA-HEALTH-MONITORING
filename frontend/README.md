# Frontend

Angular 21 (standalone components, signals) with Tailwind 4. Fonts are bundled, so it works offline.

```bash
cd frontend
npm install
npm start          # http://localhost:4200
```

The backend is expected on `http://localhost:8080` (see `src/app/core/api.service.ts`). If it is not running, the
planner page falls back to the bundled day-14 data in `src/app/mocks/`, so the UI can be built and demoed without it.

## What is here

```
src/styles.css                 shared theme: colours, fonts, .panel / .btn / .chip / .status classes
src/app/models/                contract.ts, rules.json and exercise_menu.json copied from /contract
src/app/mocks/                 MOCK_ASSESSMENTS, MOCK_PLANS (Nova, Kai, Lin on day 14)
src/app/core/api.service.ts    assessment + plan calls
src/app/components/planner/    the planner components (below)
src/app/pages/planner-page/    /planner: page that puts them together
src/app/pages/figure-gallery/  /dev/figures: all exercise drawings on one page
```

## Planner components

```html
<app-countermeasure-plan [plan]="plan" [assessment]="assessment" (indicatorSelected)="openGuide($event)" />

<app-measurement-guide-modal [indicatorId]="'sleep'" [assessment]="assessment" (closed)="..." />

<app-ultrasound-attachment-modal [assessment]="assessment" initialKind="OCULAR_ULTRASOUND"
                                 (attached)="save($event)" (closed)="..." />

<app-mission-report-print #report [assessment]="assessment" [plan]="plan" crewName="Kai" />
<button (click)="report.print()">Print</button>
```

- The modals are rendered with `@if` and close themselves; listen to `(closed)` to remove them.
- `attached` emits a contract attachment (`kind`, `ref`, `captured_at_iso`, `note`, `simulated: true`).
  The backend has no endpoint for attachments yet, so the planner page only keeps them in memory.
- The report is one A4 page. `print()` (or Ctrl+P while it is on screen) prints only the report.
- Smaller pieces that can be reused on the dashboard: `app-sparkline`, `app-region-badge`,
  `app-exercise-figure`, `app-resource-chips`, `app-modal-shell`.

Demo links: `/planner?crew=ast_lin`, `&guide=sleep`, `&scan=ECG`, `&report=1`.

## Design rules

- Dark "mission control" theme. One accent colour (cyan, `signal`).
- Green, amber and red are only used for health status, always with a shape and the word
  (`class="status status-yellow"`), never colour alone.
- Numbers use the mono font (`.num`), units keep their own capitalisation (`.unit`).
- The frontend never calculates a health or resource number. It shows what the backend sends; the limits in
  the measurement guides are read from `rules.json`.
- Background facts in the guides come from NASA HRP material collected by the team. Check the exact wording
  before quoting them in the pitch.
