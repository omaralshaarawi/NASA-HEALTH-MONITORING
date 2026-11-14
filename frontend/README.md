# Frontend

Angular 21 (standalone components, signals) with Tailwind 4. Fonts and images are bundled, so it works offline.

```bash
cd frontend
npm install --legacy-peer-deps
npm start          # http://localhost:4200
npm test           # unit tests (Vitest)
```

The backend is expected on `http://localhost:8080` and the voice workflow on the n8n webhook; both addresses are in
`src/environments/environment.ts`. No keys or secrets belong in the frontend. If the backend is not running, every
page falls back to the bundled day-14 data in `src/app/mocks/` and says so.

## Pages

| Route | Menu | What it shows |
| --- | --- | --- |
| `/home` | Home | Hero, today at a glance, four section cards. Emergency card only when triage is recommended. |
| `/health` | Health | Wireframe body map with four regions, a card per reading, log today's readings, guided scan capture. |
| `/analysis/readings` | Daily Analysis | Readings against each crew member's own normal, and what triggered today's plan. |
| `/analysis/plan` | Daily Analysis | Training sessions with countdown timers, nutrition, life-support impact. |
| `/mission` | Mission | Habitat, gravity, distance and signal delay; habitat readings; simulate another habitat. |
| `/report` | Daily Report | Printable flight surgeon report, close the day, daily log, reset demo data. |
| `/triage` | (not in the menu) | Emergency checklist and the JARVIS voice call. Opened by the emergency buttons. |
| `/dev/figures` | (not in the menu) | All exercise drawings on one page, for checking them. |

Every page sits in the same frame: top bar with the crew switcher, the red alert strip while triage is recommended
(or a call is running), a status strip, and the disclaimer footer.

## How it is put together

```
src/environments/environment.ts   backend and webhook addresses, useFakeVoice
src/styles.css                    theme tokens (colours, fonts, radius, blur) and the shared classes
src/app/shell/                    frame: top nav, crew switcher, alert strip, status strip, footer
src/app/ui/                       small shared pieces: status chip, metric tile, section header, awaiting panel
src/app/core/
  mission-store.service.ts        the one source of crew data: selected crew, assessment(), plan(), habitat
  daily-log.service.ts            closed days, kept in this browser
  training.service.ts             training countdowns (screen state only)
  emergency.service.ts            the emergency action shared by the alert strip and Home
  voice/                          JARVIS call: service, Ultravox client, simulated client
  api.service.ts                  backend calls
src/app/components/               reusable parts (body map, plan, guides, scan capture, report, timers, voice panel)
src/app/pages/                    one folder per route
src/app/models/                   contract.ts, rules.json, exercise_menu.json, triage_protocol.json (copies)
src/app/models-local/             types derived from the contract that it does not export
src/app/mocks/                    bundled day-14 assessments and plans for Nova, Kai and Lin
public/assets/                    space background and body wireframe (optimised from docs/design/assets)
```

## Rules the UI keeps

- The frontend never calculates a health status, Z score, drift or resource number. It shows what the backend
  sends; limits in the guides are read from `rules.json`.
- Green, amber and red are only for health status, always with a shape and the word, never colour alone. The one
  accent colour is the sage `signal`.
- Resource totals always carry their label ("decision-support estimate").
- Scan attachments stay `simulated: true`; images are stored and forwarded, never interpreted.
- Backend details (model name, input hash, formulas) are not shown.

## Days and the daily log

"Close day N and start day N+1" on the Daily Report page saves today's assessment and plan, unchanged, to a log in
this browser. The next day then has no assessment until its readings are logged on the Health page and assessed by
the backend; until then every page shows "Awaiting day N readings". Closing a day is one method,
`MissionStore.startNextDay()`, so it can become a backend call later. "Reset demo data" clears the log, timers and
scans and reloads the backend's seed data.

## Emergency voice call

When triage is recommended, the emergency buttons open `/triage`, which starts the checklist and the JARVIS call
through the n8n webhook (`POST jarvis-start`, answer `{ joinUrl, incident_id }`). The call lives in
`VoiceCallService`, so it carries on across pages; the alert strip shows Mute, End and Open triage while it runs.
The Ultravox code is only downloaded when a call starts. Transcripts stay in memory.

Set `useFakeVoice: true` in the environment for a simulated call with no network and no microphone. It opens the
incident on the local backend and never reaches n8n or Ultravox. Keep it `false` for real calls.

The real call needs the page on HTTPS or localhost (microphone), and the n8n webhook must allow the page's origin in
CORS.
