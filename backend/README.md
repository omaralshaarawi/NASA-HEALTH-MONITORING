# Backend

Spring Boot 3.5 / Java 21. It runs the status engine (a Java version of `contract/status_engine.py`) and the
triage flow from `contract/triage_protocol.json`. Data is kept in memory and loaded from `contract/seed.json` on start.

## Running it

You need JDK 21 (with `javac`).

```bash
cd backend
./mvnw spring-boot:run
```

The API is on http://localhost:8080. Tests: `./mvnw test`.

Settings in `application.properties`:

- `server.port` - default 8080
- `contract.dir` (or env `CONTRACT_DIR`) - path to the contract folder, default `../contract`
- `app.cors.allowed-origins` - the Angular dev server, default `http://localhost:4200`

## API

| Endpoint | What it does |
|---|---|
| `GET /api/astronauts` | list of astronauts with their current status |
| `GET /api/environments` | the 6 habitats from `rules.json` with light delay and comm mode |
| `GET /api/assessments/{id}?day=&envId=&distanceKm=` | assessment for one astronaut (all params optional) |
| `POST /api/measurements` | add or replace a day (`DailyMeasurementAndCompliance`), returns the new assessment |
| `POST /api/reset` | reload the seed data |
| `GET /api/triage/protocol` | the triage protocol |
| `POST /api/triage/start` | `{"patient_id": "ast_lin"}` |
| `POST /api/triage/step` | `{"incident_id": "...", "answer": "yes"}` (`yes`/`no` or `done`) |
| `POST /api/triage/end` | `{"incident_id": "..."}`, stops the protocol at any step |
| `GET /api/triage/{incidentId}` | current state of an incident |
| `POST /api/plans/generate?massKg=` | body is an `AssessmentResponse`, returns a `CountermeasurePlan` |
| `GET /api/plans/{astronautId}` | last generated plan, or the saved day-14 plan for Nova, Kai and Lin |

All JSON uses snake_case like `contract.ts`. Errors look like `{"status": 400, "error": "Bad Request", "message": "..."}`.

Notes:

- `day` defaults to the latest day, `envId` to the habitat the day was recorded in. `distanceKm` only matters for Mars.
- Posting a day that skips ahead (e.g. day 20 when the last one is 14) returns 409.
- The triage packet (`store_and_forward`) is created when the protocol reaches END or when `/end` is called.
  If the habitat is autonomous it is `QUEUED_FOR_UPLINK`, and `estimated_earth_receipt_iso` is the creation time plus
  the one-way light delay.

## Countermeasure planner (`planner/`)

`POST /api/plans/generate` takes the assessment the frontend already has and returns a full plan.

1. The menu (`contract/exercise_menu.json`) is filtered by the habitat's gravity.
2. Gemini writes a `CountermeasurePlanDraft` (sessions, nutrition, escalation, explanation). It gets a response
   schema where `exercise_id` can only be one of the allowed items.
3. The server clamps `minutes` (5..90) and `kcal_adjustment` (-300..600), then checks the draft: contract schema,
   the rules of `validate_draft()` from `resources.py`, no numbers in the explanation that are not in the assessment,
   no exercise while the status is RED. A rejected draft is sent back once with the reasons.
4. If there is no key, the call fails or the second draft is rejected too, the rule-based `FallbackPlanner` writes
   the draft instead. `llm.model` is then `offline-fallback`.
5. `ResourcesService` (Java version of `resources.py`) computes the resource block:
   `extra_kcal = (MET - 1) * mass_kg * hours`, water = 1.15 mL/kcal, CO2 = 0.34 g/kcal. The LLM never sees or writes
   these numbers.

To use Gemini, put the key in `backend/.env` (it is git-ignored and read at start-up, also when you run from the IDE):

```bash
GEMINI_API_KEY=your-key
```

A real environment variable with the same name takes priority over the file.

Other settings: `planner.gemini.model` (default `gemini-3.6-flash`; `gemini-2.5-flash` is closed to new keys and
3.7/3.8 were overloaded when tested), `planner.gemini.timeout-seconds` (20),
`planner.default-mass-kg` (80, override per request with `?massKg=`).

The saved plans for Nova, Kai and Lin (day 14) are in `src/main/resources/fallback-plans/`. The frontend can copy them
into its assets to have a plan even when the backend is down. They are produced by the offline planner; rewrite them
with `./mvnw test -Dtest=PlannerApiTest -Dfallback.write=true`.

## Things worth knowing

**Switching habitat rescales radiation.** The seed data was recorded in deep space (~1.4 mSv/day). Viewed on the
space station (expected 0.4) everyone would turn RED. So when you view a day in another habitat, the dose is scaled to
keep the same ratio to the expected rate. See `AssessmentService.rescaleRadiation`.

**Schema bug in the contract.** `EmergencyTriagePayload.schema.json` describes the two attachment lists as
`allOf: [array, object]`, which nothing can satisfy. We always send arrays (as `contract.ts` says), so the tests
check those two fields by hand.

## Tests

`StatusEngineTest` compares the Java output with the Python engine: the three example files in `/contract`, 47 more
cases in `src/test/resources/reference/python_reference.json` (regenerate with `make_reference.py`), and the checks
from `contract/test_engine.py`. `ApiIntegrationTest` covers the endpoints.

`ResourcesServiceTest` and `DraftValidatorTest` do the same for `resources.py` (reference in
`resources_reference.json`, regenerate with `make_resources_reference.py`). `PlannerApiTest` runs the planner with a
stub in place of the LLM and `GeminiDraftGeneratorTest` checks the request we send, so no test needs a key.

## Code layout

```
config/      Jackson, CORS, loading the contract files
controller/  REST endpoints
dto/         request bodies and the astronaut list item
engine/      StatusEngine and its helpers
exception/   ApiException and the error handler
model/       records matching contract.ts
planner/     countermeasure plans: Gemini client, draft checks, offline planner, resource math
repository/  in-memory measurement storage
rules/       typed rules.json
service/     assessments, triage, schema validation
```
