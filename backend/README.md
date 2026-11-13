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

All JSON uses snake_case like `contract.ts`. Errors look like `{"status": 400, "error": "Bad Request", "message": "..."}`.

Notes:

- `day` defaults to the latest day, `envId` to the habitat the day was recorded in. `distanceKm` only matters for Mars.
- Posting a day that skips ahead (e.g. day 20 when the last one is 14) returns 409.
- The triage packet (`store_and_forward`) is created when the protocol reaches END or when `/end` is called.
  If the habitat is autonomous it is `QUEUED_FOR_UPLINK`, and `estimated_earth_receipt_iso` is the creation time plus
  the one-way light delay.

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

## Code layout

```
config/      Jackson, CORS, loading the contract files
controller/  REST endpoints
dto/         request bodies and the astronaut list item
engine/      StatusEngine and its helpers
exception/   ApiException and the error handler
model/       records matching contract.ts
repository/  in-memory measurement storage
rules/       typed rules.json
service/     assessments, triage, schema validation
```
