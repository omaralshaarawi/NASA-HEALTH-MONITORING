# Backend: core engine and triage API

Java 21 + Spring Boot 3.5. A port of `contract/status_engine.py` behind a REST API, plus the deterministic triage
state machine over `contract/triage_protocol.json`. State is in memory and starts from `contract/seed.json`.

Everything is read from `/contract` at start-up; nothing from that folder is copied here.

## Run

Needs a **JDK 21** (with `javac`, not only a JRE). If `javac -version` does not say 21, point `JAVA_HOME` at a JDK 21.

```bash
cd backend
./mvnw spring-boot:run        # http://localhost:8080
./mvnw test                   # 77 tests, about 5 s
```

Or build once and run the jar from anywhere in the repo: `./mvnw package`, then
`java -jar backend/target/spacehealth-backend-1.0.0.jar`.

| Setting | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | API port |
| `contract.dir` (env `CONTRACT_DIR`) | `../contract` | Folder with `rules.json`, `seed.json`, `triage_protocol.json`, `schemas/` |
| `app.cors.allowed-origins` | `http://localhost:4200,http://127.0.0.1:4200` | Angular dev server(s), comma-separated |

## Endpoints

All JSON is snake_case, exactly as in `contract/contract.ts`.

| Method and path | Returns |
|---|---|
| `GET /api/astronauts` | Nova, Kai, Lin with `display_name`, `scenario`, `latest_mission_day`, `environment_id`, `overall`, `triage_recommended` |
| `GET /api/environments` | The 6 presets of `rules.json` as `EnvironmentProfile[]`, with computed `one_way_delay_s` and `comm_mode` |
| `GET /api/assessments/{astronautId}?day=&envId=&distanceKm=` | `AssessmentResponse` |
| `POST /api/measurements` | Body `DailyMeasurementAndCompliance`; returns the updated `AssessmentResponse` |
| `POST /api/reset` | Demo helper: forget posted measurements, reload `seed.json` |
| `GET /api/triage/protocol` | `TriageProtocol` (the protocol file as loaded) |
| `POST /api/triage/start` | `EmergencyTriagePayload` at the first step |
| `POST /api/triage/step` | `EmergencyTriagePayload` after answering the current step |
| `POST /api/triage/end` | `EmergencyTriagePayload` with `store_and_forward` (manual exit, any step) |
| `GET /api/triage/{incidentId}` | Current `EmergencyTriagePayload` (page reload) |

Errors are `{"status": 400, "error": "Bad Request", "message": "..."}`.

### Assessments

```bash
curl "http://localhost:8080/api/assessments/ast_kai?day=14&envId=DEEP_SPACECRAFT"
```

- `day`: optional, default is the latest logged day. Days `1..day` feed the engine.
- `envId`: optional, default is the habitat that day was recorded in.
- `distanceKm`: optional, only for the Mars habitats (the distance slider). Clamped to the `rules.json` range;
  ignored where the distance is fixed.
- `generated_at_iso` is the server clock.

### Measurements

`POST /api/measurements` validates the body against `schemas/DailyMeasurementAndCompliance.schema.json` (400 with the
list of violations), then replaces that `mission_day` or appends the next one. A day that would leave a gap is a 409,
because the anchor window and the drift slope index by mission day. The answer is the assessment for that day in the
record's own `environment_id`. A missing vital is "not measured", never zero.

### Triage

```bash
curl -X POST localhost:8080/api/triage/start -H 'Content-Type: application/json' -d '{"patient_id":"ast_lin"}'
curl -X POST localhost:8080/api/triage/step  -H 'Content-Type: application/json' -d '{"incident_id":"inc_ab12cd34","answer":"no"}'
curl -X POST localhost:8080/api/triage/end   -H 'Content-Type: application/json' -d '{"incident_id":"inc_ab12cd34"}'
```

| Route | Body |
|---|---|
| start | `patient_id` (required), `reporter_id`, `environment_id`, `distance_km`, `mission_day` (all optional) |
| step | `incident_id`, `answer` (`yes` / `no` on `ASK_YES_NO` steps, `done` on all others), optional `step_id` |
| end | `incident_id`, optional `reason` |

- The server owns the position in the protocol. `state.current_step_id` is the step to show; look its `script` and
  `checklist_label` up in `GET /api/triage/protocol`.
- A wrong answer type is a 400. If `step_id` is sent and is not the current step, the answer is a 409 (protects
  against double clicks).
- `store_and_forward` appears when the protocol reaches `END` or when `/end` is called. `/end` works at any step,
  which is the only way out of the CPR loop (`A3 -> A6 -> A3`) while the patient does not recover.
- `delivery` is `QUEUED_FOR_UPLINK` when `comm_mode` is `AUTONOMOUS`, otherwise `SENT_LIVE`.
- `estimated_earth_receipt_iso = created_at_iso + one_way_delay_s`, to the millisecond.
- `summary` is assembled by code from the snapshot and the step log. No text is generated.

## Two decisions to know about

**Habitat switching rescales radiation.** The seed was recorded in `DEEP_SPACECRAFT` (about 1.4 mSv/day). Judged
as-is against the station's expected 0.4 mSv/day, every astronaut would show RED radiation. So when a day is viewed in
a habitat other than the one it was recorded in, its dose rate is rescaled to keep the same ratio to the expected
rate: Nova stays GREEN everywhere and Kai's solar particle event stays at about 2.1x. Nothing else is rescaled, and
days viewed in their own habitat are untouched. This lives in `AssessmentService.viewedIn`; the engine itself is a
strict port.

**The generated schema rejects the attachment lists.** `contract.ts` types
`patient_snapshot.recent_attachments` and `store_and_forward.attachments` as `NonNullable<Attachment[]>`, and
`schemas/EmergencyTriagePayload.schema.json` renders that as `allOf: [array, object]`, which nothing can satisfy.
The backend follows `contract.ts` and always sends an array, so strict validation of a triage payload against that
schema fails on those two fields only. The contract is locked, so it was left alone.

## How the port is verified

`StatusEngineServiceTest` compares the Java output with the Python reference field for field (exact numbers, exact
reason strings, null versus absent):

- `contract/example_assessment_ast_{nova,kai,lin}.json`
- 47 more Python outputs in `src/test/resources/reference/python_reference.json`: all six habitats, days inside and
  just after the anchor window, and synthetic series for gaps, a confirmed Z-RED, a single spike, absolute lows,
  cabin CO2 RED and ration RED. Regenerate with `python3 src/test/resources/reference/make_reference.py` if the
  contract ever changes.
- the unit checks of `contract/test_engine.py` (tests 1 to 5)

Every response in the tests is also validated against `contract/schemas`. `rules.json` is loaded strictly: a rule the
Java model cannot find stops start-up instead of silently becoming `0` or `false`.

## Layout

```
src/main/java/com/nasa/spacehealth/
  config/      Jackson snake_case, CORS, contract file loading, clock
  model/       records matching contract.ts, plus Rules (typed rules.json)
  service/     StatusEngineService (the port), PyFormat (Python-exact rounding and formatting),
               MeasurementStore, AssessmentService, TriageService, SchemaValidator
  controller/  AssessmentController, TriageController, error handling
```
