# Astropital

Health monitoring and emergency triage for astronauts on long missions, built for the Space Apps
Challenge 2026.

Far from Earth, a flight surgeon can be minutes away by radio. Astropital watches each crew member
against their own normal rather than textbook ranges, catches slow drift before it turns into an
emergency, plans the day's training around what it finds, and walks the crew through an emergency
checklist by voice when there is no time to wait for Earth.

All crew data in this repository is simulated. This is a decision-support prototype, not a medical
device.

## What it does

- **Home**: today's status, signal delay to Earth, and an emergency card when triage is recommended.
- **Health**: a body map with four regions (head, chest, spine, immune/metabolic), a card per
  reading with its 14-day trend, logging today's readings, a 30-second reaction test, and guided
  ultrasound/ECG capture.
- **Daily Analysis**: every reading next to the crew member's own baseline, what triggered today's
  plan, and the training and nutrition plan with countdown timers and its cost to life support.
- **Camera coach**: counts reps and checks form through the webcam during a training session, then
  adds the minutes to the day's record.
- **Mission**: habitat, gravity, distance and signal delay, and what-if switching between the station,
  deep space, the Moon and Mars.
- **Daily Report**: a one-page report for the flight surgeon (print or PDF), closing the day, and a
  log of past days.
- **Triage**: a fixed emergency checklist, with the JARVIS voice operator reading each step and
  listening for yes, no or done.

## How the assessment works

Mission days 1 to 7 set each crew member's personal baseline. From then on every reading gets three
checks and the worst one wins:

- absolute limits, which apply from day 1,
- a Z score against the personal baseline (YELLOW from 2.5, RED from 3.0 on two readings in a row),
- a 7-day drift, which catches slow decline and can raise a reading to YELLOW.

Readings roll up into four body regions, and the regions into an overall status. A RED absolute
limit recommends triage. All thresholds live in `contract/rules.json`.

## Repository layout

| Folder | What's in it |
| --- | --- |
| `contract/` | The shared contract: data types, JSON schemas, rules, seed data, the reference engine in Python, the triage protocol and exercise menus. Treat it as read-only. |
| `backend/` | Spring Boot API (Java 21): the assessment engine, measurements, triage incidents and the training planner. |
| `frontend/` | Angular 21 app. |
| `cv-service/` | Python service for the camera coach (OpenCV + MediaPipe pose tracking). |
| `voice-n8n/` | n8n workflow and prompt for the JARVIS voice operator. |

Each folder has its own README with the details.

## Running it locally

You need JDK 21, Node 22 and Python 3.10 or newer. The voice operator also needs n8n.

**1. Backend** (http://localhost:8080)

```bash
cd backend
./mvnw spring-boot:run
```

**2. Frontend** (http://localhost:4200)

```bash
cd frontend
npm install --legacy-peer-deps
npm start
```

**3. Camera coach** (http://localhost:5001), optional

```bash
cd cv-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 5001
```

**4. Voice operator**, optional: see `voice-n8n/README.md`. It needs a public URL for n8n so the voice
service can reach its webhooks.

The app still works when the optional parts are off: plans fall back to a rule-based planner, the
camera coach says it is offline, and the triage checklist runs on screen without the voice.

### Configuration

| Setting | Where | What for |
| --- | --- | --- |
| `GEMINI_API_KEY` | `backend/.env` or the environment | Written training plans. Without it the rule-based planner is used. |
| `ULTRAVOX_API_KEY`, `WEBHOOK_URL`, `BACKEND_URL` | n8n's environment | The voice operator. |
| `apiBase`, `cvServiceUrl`, `jarvisStartUrl` | `frontend/src/environments/environment.ts` | Where the app finds the backend, camera coach and voice workflow. |

Keep keys out of git: `.env` is ignored.

## Demo crew

| Crew member | Day 14 status | Story |
| --- | --- | --- |
| Nova | GREEN | Every reading inside the personal baseline. |
| Kai | YELLOW | Slow drift across several systems since day 8, and a solar particle event on days 13 and 14. |
| Lin | RED | Chest-pain emergency on day 14: triage is recommended and the voice operator can be started. |

Use **Reset demo data** on the Daily Report page to go back to the original day 14.

## Tests

```bash
cd backend && ./mvnw test
cd frontend && npx ng test --watch=false
```

## Use of AI

Some of the code in this project was generated with AI tools to accelerate innovation. AI was also
used for:

- the space background and body wireframe images, which carry a sparkle watermark in the app,
- the voice of the JARVIS emergency operator, which the app labels as an SI-generated voice,
- the wording of the daily training plans, which the backend checks against the rules before use.
