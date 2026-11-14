# JARVIS voice operator (Ultravox + n8n)

JARVIS reads the triage checklist aloud and moves through it by calling the backend. It never decides a step
itself: every answer goes to `POST /api/triage/step`, and the script it speaks comes back from the backend.

```
Angular triage page --POST /webhook/jarvis-start--> n8n --> backend /api/triage/start
                                                     n8n --> Ultravox POST /api/calls  --> joinUrl
browser <== voice ==> Ultravox agent
   agent tool help_crewmate_step --> n8n /webhook/jarvis-help-step  --> backend /api/triage/step
   agent tool alert_nasa_agency  --> n8n /webhook/jarvis-alert-nasa --> backend /api/triage/end
```

## Files

- `jarvis_system_prompt.txt` - the system prompt, with `{{placeholders}}` filled in by n8n
- `ultravox_call_payload.json` - body for `POST https://api.ultravox.ai/api/calls` (same prompt, 3 tools)
- `n8n_jarvis_workflow.json` - importable workflow with the 3 webhooks (it already contains the payload)

## Setup

1. Start the backend (`cd backend && ./mvnw spring-boot:run`, port 8080).
2. Start n8n with the three variables it needs:

   ```bash
   ULTRAVOX_API_KEY=your-key WEBHOOK_URL=https://your-public-url BACKEND_URL=http://localhost:8080 npx n8n
   ```

   - `WEBHOOK_URL` must be reachable from the internet, because Ultravox calls the two tool webhooks.
     Locally, run a tunnel to port 5678 (for example `npx localtunnel --port 5678`) and use that address.
   - If n8n blocks `$env` in expressions, also set `N8N_BLOCK_ENV_ACCESS_IN_NODE=false`.
   - If n8n runs in Docker, use `BACKEND_URL=http://host.docker.internal:8080`.
3. In n8n: Workflows > Import from file > `n8n_jarvis_workflow.json`, then switch the workflow to Active.
4. Test without a microphone:

   ```bash
   curl -X POST http://localhost:5678/webhook/jarvis-start -H 'Content-Type: application/json' -d '{"patient_id":"ast_lin"}'
   ```

   The answer is `{ joinUrl, callId, incident_id, payload }`. Then, with that `incident_id`:

   ```bash
   curl -X POST http://localhost:5678/webhook/jarvis-help-step -H 'Content-Type: application/json' -d '{"incident_id":"inc_xxxxxxxx","answer":"no"}'
   ```

   ```bash
   curl -X POST http://localhost:5678/webhook/jarvis-alert-nasa -H 'Content-Type: application/json' -d '{"incident_id":"inc_xxxxxxxx","summary":"CPR in progress"}'
   ```

## Connecting the triage page

In `frontend/src/app/pages/triage-page/`:

```bash
npm install ultravox-client
```

```ts
import { UltravoxSession } from 'ultravox-client';

const res = await fetch('http://localhost:5678/webhook/jarvis-start', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ patient_id: 'ast_lin' }),
});
const { joinUrl, incident_id } = await res.json();

const session = new UltravoxSession();
session.joinCall(joinUrl);          // asks for the microphone
// session.leaveCall() when the page closes
```

Keep the on-screen checklist in sync by polling `GET /api/triage/{incident_id}` every second or two while the
call is running: voice and screen then show the same step, because the backend owns it.

## Things to know

- The protocol has 18 steps (`S0_RESPONSE` ... `END`), not 7. The prompt does not list the scripts; JARVIS reads
  whatever script the backend returns, so the prompt stays correct if the protocol changes.
- The backend's `/api/triage/end` field is `reason`; the workflow maps the tool's `summary` onto it.
- `/api/triage/step` returns the step id, not the script. The workflow looks the script up in
  `/api/triage/protocol` and returns it as `current_step.script`.
- `incident_id` is sent to the tools as a fixed value by Ultravox, so the model cannot get it wrong.
- Ultravox's built-in hang-up tool is called `hangUp`; that is the `end_call` of the brief.
- Not tested against the real Ultravox API or a running n8n: there was no key. If Ultravox rejects the body,
  check `model`, `voice` and `firstSpeaker` against their current docs first.
- The protocol is marked `PLACEHOLDER_REQUIRES_CLINICIAN_REVIEW`. JARVIS never names or doses a medication.
