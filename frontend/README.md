# Frontend

Angular 21 (standalone components, signals) with Tailwind 4. Fonts are bundled, so it works offline.

```bash
cd frontend
npm install
npm start          # http://localhost:4200
```

The backend is expected on `http://localhost:8080` (see `src/app/core/api.service.ts`). If it is not running, the
dashboard and planner fall back to bundled day-14 data in `src/app/mocks/`; dashboard controls are read-only offline.

## What is here

```
src/styles.css                 shared theme: colours, fonts, .panel / .btn / .chip / .status classes
src/app/models/                contract.ts, rules.json and exercise_menu.json copied from /contract
src/app/mocks/                 MOCK_ASSESSMENTS, MOCK_PLANS (Nova, Kai, Lin on day 14)
src/app/models/triage_protocol.json  locked triage checklist copy for local fallback
src/app/core/api.service.ts    dashboard, measurements, triage and planner API calls
src/app/core/crew.ts           crew labels shared by dashboard, triage and planner
src/app/components/dashboard/ shared header, body map, indicator groups, context controls, measurement/PVT flow
src/app/pages/dashboard-page/ /: crew dashboard, assessments, habitat/distance/day controls and daily logging
src/app/pages/triage-page/    /triage: emergency checklist and store-and-forward packet
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

Dashboard links: `/?crew=ast_lin&env=PLANETARY_BASE_MARS&day=12`, `&log=1`. The dashboard loads all crew
assessments through the backend; it never generates an AI plan on page load. Use **Open countermeasures planner**
or the **Planner** nav link when a plan is needed. `/triage?patient=ast_lin&env=PLANETARY_BASE_MARS&day=14`
starts with the specified patient context; the incident URL is updated after start so a reload resumes it.

Daily logging submits `source: MANUAL`, leaves blank vitals omitted, and includes the optional 30-second browser PVT
and simulated attachments when recorded. The PVT is a measurement exercise, not a medical assessment. Triage scripts
are displayed verbatim from the backend protocol (or the bundled copy offline); local-only fallback cannot queue a
store-and-forward packet.

## Design rules

- Dark "mission control" theme. One accent colour (cyan, `signal`).
- Green, amber and red are only used for health status, always with a shape and the word
  (`class="status status-yellow"`), never colour alone.
- Numbers use the mono font (`.num`), units keep their own capitalisation (`.unit`).
- The frontend never calculates a health or resource number. It shows what the backend sends; the limits in
  the measurement guides are read from `rules.json`.
- Background facts in the guides come from NASA HRP material collected by the team. Check the exact wording
  before quoting them in the pitch.
