# cv-service: camera coach

A small local service that watches a training session through the webcam with OpenCV and MediaPipe Pose:
it counts reps, times holds, measures cadence and checks form against the guardrails in
`contract/exercises.json`. The Training & Nutrition Plan page uses it through **Launch camera coach**.

```bash
cd cv-service
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --port 5001        # or: python main.py
```

On first start it downloads the MediaPipe pose model (`pose_landmarker_lite.task`, a few MB) next to `main.py`.

| Setting | Default | |
| --- | --- | --- |
| `SPRING_BOOT_URL` | `http://localhost:8080` | backend used by the sync |
| `CAMERA_INDEX` | `0` | which webcam to open |
| `CV_PORT` | `5001` | port when started with `python main.py` |

## Endpoints

| | |
| --- | --- |
| `GET /exercises` | camera profiles from `contract/exercises.json` |
| `POST /select` `{ exercise_id }` | track this exercise; counters start again from zero |
| `GET /video_feed?stream=<id>` | MJPEG stream with the pose overlay |
| `POST /stop?stream=<id>` | release the webcam now; without an id, from every stream |
| `GET /telemetry` | reps, hold, cadence, stage, joint angle, tracked side, form severity and warning |
| `POST /sync-to-backend` `{ astronaut_id, day, minutes_override }` | add the workout to that day's record |

- Every exercise id in `contract/exercise_menu.json` has a camera profile of the same id. If one ever lacks a profile,
  `/select` tracks the default exercise and says so (`profile_fallback: true`) instead of failing.
- The webcam is held only while the camera coach is open. Closing the pop-up (or the tab) calls `POST /stop`,
  which releases it at once; opening it again starts a new stream that takes the camera back from any earlier
  one and retries for up to 3 seconds if it is still being let go. A stop for an old stream never touches a newer
  one. If the camera is busy or missing, the feed shows a message instead.

## Sync

The backend only takes whole-day records, and posting a day it already has replaces that day. So the sync reads
that day's assessment, rebuilds the record from it (vitals, habitat readings, ration, reaction time), and raises
only the resistive-exercise minutes: by `minutes_override` if given, otherwise 5 minutes per rep with a 15 minute
floor, capped at 240. It refuses when no reps were done and no minutes were given. See `measurement.py`.

Two things the assessment does not carry are filled in: `morning_vitals_logged` is true when that day has any
vital, and the reaction test's lapse and trial counts are sent as 0 (the engine reads only the median). Scans
attached to that day are not in the assessment either, so they are not carried over.
