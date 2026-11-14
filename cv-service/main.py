"""Camera coach: OpenCV + MediaPipe pose tracking for the exercise sessions.

Run:  uvicorn main:app --port 5001     (or: python main.py)

Endpoints
  GET  /exercises        camera profiles from contract/exercises.json
  POST /select           { exercise_id }  pick the exercise to track (resets the counters)
  GET  /video_feed       ?stream=<id>  MJPEG stream with the pose overlay
  POST /stop             ?stream=<id>  release the webcam now (the pop-up calls this when it closes)
  GET  /telemetry        live reps, hold time, cadence, stage, angle and form warning
  POST /sync-to-backend  { astronaut_id, day, minutes_override }  add the workout to today's record
"""

import json
import math
import os
import threading
import time
import urllib.request
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision
from pydantic import BaseModel

from measurement import build_measurement, credited_minutes

app = FastAPI(title="NASA OpenCV + MediaPipe Exercise Service")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

SPRING_BOOT_URL = os.getenv("SPRING_BOOT_URL", "http://localhost:8080")
CAMERA_INDEX = int(os.getenv("CAMERA_INDEX", "0"))

# Locate the camera profiles inside ../contract/
CONTRACT_DIR = Path(__file__).resolve().parent.parent / "contract"
for candidate in ("exercise.json", "exercises.json", "cv_exercises.json"):
    if (CONTRACT_DIR / candidate).exists():
        EXERCISE_JSON_PATH = CONTRACT_DIR / candidate
        break
else:
    raise FileNotFoundError(f"Place exercise.json inside {CONTRACT_DIR}")

with open(EXERCISE_JSON_PATH, "r", encoding="utf-8") as f:
    CV_DATA = json.load(f)

EXERCISES = {ex["exercise_id"]: ex for ex in CV_DATA["exercises"]}
DEFAULT_EX_ID = "resistive_moderate" if "resistive_moderate" in EXERCISES else next(iter(EXERCISES.keys()))

# Every exercise the planner can show comes from exercise_menu.json. Each one has a camera profile
# of the same id today; any that ever lacks one falls back to the default profile instead of a 404.
MENU_PATH = CONTRACT_DIR / "exercise_menu.json"
MENU_IDS = set()
if MENU_PATH.exists():
    with open(MENU_PATH, "r", encoding="utf-8") as f:
        MENU_IDS = {item["exercise_id"] for item in json.load(f)["items"]}
UNPROFILED = sorted(MENU_IDS - EXERCISES.keys())
if UNPROFILED:
    print(f"[cv-service] no camera profile for {UNPROFILED}; they use {DEFAULT_EX_ID}")

MODEL_PATH = Path(__file__).resolve().parent / "pose_landmarker_lite.task"
MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task"
)
if not MODEL_PATH.exists():
    try:
        urllib.request.urlretrieve(MODEL_URL, MODEL_PATH)
    except OSError as error:
        raise RuntimeError(
            f"Could not download the pose model from {MODEL_URL}. "
            f"Download it once and save it as {MODEL_PATH}."
        ) from error

POSE_CONNECTIONS = [
    (11, 12), (11, 13), (13, 15), (12, 14), (14, 16),
    (11, 23), (12, 24), (23, 24), (23, 25), (25, 27),
    (24, 26), (26, 28), (27, 29), (28, 30), (29, 31), (30, 32),
]

MIRROR_MAP = {
    0: 0,
    11: 12, 12: 11,
    13: 14, 14: 13,
    15: 16, 16: 15,
    23: 24, 24: 23,
    25: 26, 26: 25,
    27: 28, 28: 27,
}

state = {
    "exercise_id": DEFAULT_EX_ID,
    "requested_id": DEFAULT_EX_ID,
    "profile_fallback": False,
    "name": EXERCISES[DEFAULT_EX_ID]["name"],
    "target_system": EXERCISES[DEFAULT_EX_ID]["target_system"],
    "cv_tracking_mode": EXERCISES[DEFAULT_EX_ID]["cv_tracking_mode"],
    "setup_instruction_0g": EXERCISES[DEFAULT_EX_ID]["setup_instruction_0g"],
    "reps": 0,
    "target_reps": EXERCISES[DEFAULT_EX_ID]["rep_thresholds"]["default_target_reps"],
    "hold_seconds": 0.0,
    "target_hold_seconds": EXERCISES[DEFAULT_EX_ID]["rep_thresholds"]["default_hold_seconds"],
    "cadence_rpm": 0.0,
    "stage": EXERCISES[DEFAULT_EX_ID]["rep_thresholds"]["start_stage"],
    "tracked_side": "LEFT",
    "primary_angle": 180.0,
    "visibility_ok": False,
    "camera_active": False,
    "severity": "GREEN",
    "hud_warning": "FORM NOMINAL",
    "active_warnings": [],
    "rep_timestamps": [],
    "last_frame_time": time.time(),
}


def resolve_exercise_id(raw: str) -> tuple[str, bool]:
    """The camera profile for an id from the UI, and whether the default had to stand in for it."""
    key = raw.strip().lower()
    if key in EXERCISES:
        return key, False
    if key in MENU_IDS:
        return DEFAULT_EX_ID, True
    raise HTTPException(status_code=404, detail=f"Unknown exercise_id: {raw}")


def load_exercise_state(raw_id: str):
    ex_id, fallback = resolve_exercise_id(raw_id)
    ex = EXERCISES[ex_id]
    rt = ex["rep_thresholds"]
    state.update({
        "exercise_id": ex_id,
        "requested_id": raw_id,
        "profile_fallback": fallback,
        "name": ex["name"],
        "target_system": ex["target_system"],
        "cv_tracking_mode": ex["cv_tracking_mode"],
        "setup_instruction_0g": ex["setup_instruction_0g"],
        "reps": 0,
        "target_reps": rt["default_target_reps"],
        "hold_seconds": 0.0,
        "target_hold_seconds": rt["default_hold_seconds"],
        "cadence_rpm": 0.0,
        "stage": rt["start_stage"],
        "primary_angle": 180.0,
        "severity": "GREEN",
        "hud_warning": "FORM NOMINAL",
        "active_warnings": [],
        "rep_timestamps": [],
        "last_frame_time": time.time(),
    })


def calc_angle(a, b, c) -> float:
    rad = math.atan2(c.y - b.y, c.x - b.x) - math.atan2(a.y - b.y, a.x - b.x)
    deg = abs(rad * 180.0 / math.pi)
    return 360.0 - deg if deg > 180.0 else deg


def draw_pose_skeleton(frame, landmarks, joint_color):
    h, w, _ = frame.shape
    for idx_a, idx_b in POSE_CONNECTIONS:
        if idx_a < len(landmarks) and idx_b < len(landmarks):
            pa, pb = landmarks[idx_a], landmarks[idx_b]
            if getattr(pa, "visibility", 1.0) > 0.5 and getattr(pb, "visibility", 1.0) > 0.5:
                cv2.line(
                    frame,
                    (int(pa.x * w), int(pa.y * h)),
                    (int(pb.x * w), int(pb.y * h)),
                    (255, 255, 255),
                    2,
                )
    for lm in landmarks:
        if getattr(lm, "visibility", 1.0) > 0.5:
            cv2.circle(frame, (int(lm.x * w), int(lm.y * h)), 4, joint_color, -1)


class CameraHandle:
    """The webcam for one stream. Reading and releasing share a lock, so another request can
    release it safely even while the stream is between frames."""

    def __init__(self, cap):
        self.cap = cap
        self.lock = threading.Lock()
        self.closed = False

    def read(self):
        with self.lock:
            if self.closed:
                return False, None
            return self.cap.read()

    def close(self):
        with self.lock:
            if not self.closed:
                self.closed = True
                self.cap.release()


# Streams that currently hold the webcam, by the stream id the browser gave. There is at most one.
_streams: dict[str, CameraHandle] = {}
_streams_lock = threading.Lock()
CAMERA_OPEN_TIMEOUT_S = 3.0


def release_camera(stream: str | None = None) -> bool:
    """Releases the webcam held by that stream, or by every stream when none is named."""
    with _streams_lock:
        keys = [stream] if stream is not None else list(_streams)
        handles = [_streams.pop(k) for k in keys if k in _streams]
        state["camera_active"] = bool(_streams)
    for handle in handles:
        handle.close()
    return bool(handles)


def open_camera() -> CameraHandle | None:
    """Opens the webcam, retrying for a moment in case it is still being let go."""
    deadline = time.time() + CAMERA_OPEN_TIMEOUT_S
    while True:
        cap = cv2.VideoCapture(CAMERA_INDEX)
        if cap.isOpened():
            return CameraHandle(cap)
        cap.release()
        if time.time() >= deadline:
            return None
        time.sleep(0.25)


def message_frame(text: str) -> bytes:
    """One MJPEG part with a message, for when there is no camera picture to show."""
    frame = np.zeros((360, 640, 3), dtype=np.uint8)
    frame[:] = (23, 15, 10)
    cv2.putText(frame, text, (24, 186), cv2.FONT_HERSHEY_SIMPLEX, 0.7, (220, 230, 240), 2)
    _, buf = cv2.imencode(".jpg", frame)
    return b"--frame\r\nContent-Type: image/jpeg\r\n\r\n" + buf.tobytes() + b"\r\n"


def generate_frames(stream: str):
    # A new stream takes the webcam over from any earlier one, which may still be waiting to be
    # cleaned up after its pop-up closed.
    release_camera()
    handle = open_camera()
    if handle is None:
        yield message_frame("CAMERA NOT AVAILABLE - CLOSE OTHER APPS USING IT")
        return
    with _streams_lock:
        _streams[stream] = handle
        state["camera_active"] = True

    # The webcam is released however this ends: POST /stop, a newer stream, or the browser
    # dropping the connection.
    try:

        options = mp_vision.PoseLandmarkerOptions(
            base_options=mp_python.BaseOptions(model_asset_path=str(MODEL_PATH)),
            running_mode=mp_vision.RunningMode.IMAGE,
            num_poses=1,
            min_pose_detection_confidence=0.65,
            min_pose_presence_confidence=0.65,
            min_tracking_confidence=0.65,
        )
        with mp_vision.PoseLandmarker.create_from_options(options) as landmarker:
            while not handle.closed:
                ok, frame = handle.read()
                if not ok:
                    time.sleep(0.05)
                    continue

                now = time.time()
                dt = min(0.2, now - state["last_frame_time"])
                state["last_frame_time"] = now

                frame = cv2.flip(frame, 1)
                h, w, _ = frame.shape
                rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
                results = landmarker.detect(mp_image)

                ex = EXERCISES[state["exercise_id"]]
                rt = ex["rep_thresholds"]
                pa = ex["primary_angle"]

                if results.pose_landmarks:
                    lm = results.pose_landmarks[0]

                    l_idx = [pa["point_a"]["index"], pa["vertex_b"]["index"], pa["point_c"]["index"]]
                    bm = pa["bilateral_mirror"]
                    r_idx = [bm["point_a_index"], bm["vertex_b_index"], bm["point_c_index"]]

                    l_vis = min(getattr(lm[i], "visibility", 1.0) for i in l_idx)
                    r_vis = min(getattr(lm[i], "visibility", 1.0) for i in r_idx)
                    use_right = r_vis > l_vis
                    use_idx = r_idx if use_right else l_idx
                    state["tracked_side"] = "RIGHT" if use_right else "LEFT"
                    state["visibility_ok"] = max(l_vis, r_vis) >= rt["min_visibility_confidence"]

                    if state["visibility_ok"]:
                        angle = calc_angle(lm[use_idx[0]], lm[use_idx[1]], lm[use_idx[2]])
                        state["primary_angle"] = round(angle, 1)

                        warnings = []
                        worst_sev = "GREEN"
                        for g in ex["form_guardrails"]:
                            ia, ib, ic = g["point_a_index"], g["vertex_b_index"], g["point_c_index"]
                            if use_right:
                                ia, ib, ic = MIRROR_MAP.get(ia, ia), MIRROR_MAP.get(ib, ib), MIRROR_MAP.get(ic, ic)

                            ga, gb, gc = lm[ia], lm[ib], lm[ic]
                            if g["check_id"] in ("shoulder_level", "hip_sway") and abs(ga.x - gb.x) < 0.12:
                                continue
                            if min(getattr(ga, "visibility", 1.0), getattr(gb, "visibility", 1.0), getattr(gc, "visibility", 1.0)) < 0.55:
                                continue

                            g_angle = calc_angle(ga, gb, gc)
                            if g_angle < g["min_safe_deg"] or g_angle > g["max_safe_deg"]:
                                warnings.append(g["hud_warning_text"])
                                if g["severity"] == "RED" or worst_sev == "GREEN":
                                    worst_sev = g["severity"]

                        state["severity"] = worst_sev
                        state["active_warnings"] = warnings
                        state["hud_warning"] = warnings[0] if warnings else "FORM NOMINAL"

                        mode = ex["cv_tracking_mode"]
                        if mode in ("REP_COUNTER", "CADENCE_TRACKER"):
                            if angle <= rt["down_trigger_deg"]:
                                state["stage"] = rt["eccentric_stage"]
                            elif angle >= rt["up_trigger_deg"] and state["stage"] == rt["eccentric_stage"]:
                                state["stage"] = rt["start_stage"]
                                if worst_sev != "RED":
                                    state["reps"] += 1
                                    state["rep_timestamps"].append(now)
                                    state["rep_timestamps"] = [t for t in state["rep_timestamps"] if now - t <= 20.0]
                                    if len(state["rep_timestamps"]) >= 2:
                                        span = state["rep_timestamps"][-1] - state["rep_timestamps"][0]
                                        state["cadence_rpm"] = round((len(state["rep_timestamps"]) - 1) * 60.0 / max(1.0, span), 1)
                        elif mode == "POSTURE_HOLD":
                            in_hold = (angle >= rt["down_trigger_deg"]) and (worst_sev != "RED")
                            state["stage"] = rt["start_stage"] if in_hold else rt["eccentric_stage"]
                            if in_hold:
                                state["hold_seconds"] = round(state["hold_seconds"] + dt, 1)
                                if state["target_hold_seconds"] > 0 and state["hold_seconds"] >= state["target_hold_seconds"]:
                                    state["reps"] += 1
                                    state["hold_seconds"] = 0.0

                        v_pt = lm[use_idx[1]]
                        cv2.putText(frame, f"{int(angle)} deg", (int(v_pt.x * w), int(v_pt.y * h)),
                                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
                    else:
                        state["severity"] = "YELLOW"
                        state["hud_warning"] = "STEP BACK - KEEP FULL BODY IN FRAME"

                    color = (0, 255, 128) if state["severity"] == "GREEN" else ((0, 215, 255) if state["severity"] == "YELLOW" else (0, 50, 255))
                    draw_pose_skeleton(frame, lm, color)

                cv2.rectangle(frame, (0, 0), (w, 60), (15, 23, 42), -1)
                cv2.putText(
                    frame,
                    f"{state['exercise_id']} | REPS: {state['reps']}/{state['target_reps']} | HOLD: {state['hold_seconds']}s | {state['stage']}",
                    (12, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.55, (255, 255, 255), 2
                )
                hud_color = (100, 255, 160) if state["severity"] == "GREEN" else ((0, 215, 255) if state["severity"] == "YELLOW" else (80, 80, 255))
                cv2.putText(frame, state["hud_warning"], (12, 48), cv2.FONT_HERSHEY_SIMPLEX, 0.55, hud_color, 2)

                _, buf = cv2.imencode(".jpg", frame)
                yield b"--frame\r\nContent-Type: image/jpeg\r\n\r\n" + buf.tobytes() + b"\r\n"
    finally:
        with _streams_lock:
            if _streams.get(stream) is handle:
                del _streams[stream]
            state["camera_active"] = bool(_streams)
        handle.close()


@app.get("/exercises")
def get_exercises():
    return CV_DATA["exercises"]


@app.get("/video_feed")
def video_feed(stream: str = "default"):
    return StreamingResponse(generate_frames(stream), media_type="multipart/x-mixed-replace; boundary=frame")


@app.post("/stop")
def stop_camera(stream: str | None = None):
    """Releases the webcam. With a stream id, only that stream's, so a late stop from a closed
    pop-up never cuts off a newer one."""
    return {"released": release_camera(stream), "camera_active": state["camera_active"]}


@app.get("/telemetry")
def get_telemetry():
    return state


class SelectRequest(BaseModel):
    exercise_id: str = DEFAULT_EX_ID


@app.post("/select")
def select_exercise(req: SelectRequest):
    load_exercise_state(req.exercise_id)
    return state


class SyncToSpringRequest(BaseModel):
    astronaut_id: str = "ast_kai"
    day: int = 14
    minutes_override: int | None = None


def backend_error(response: requests.Response) -> HTTPException:
    try:
        detail = response.json().get("message") or response.text
    except ValueError:
        detail = response.text
    return HTTPException(status_code=response.status_code, detail=f"Mission computer refused it: {detail}")


@app.post("/sync-to-backend")
def sync_to_spring_boot(req: SyncToSpringRequest):
    try:
        extra = credited_minutes(state["reps"], req.minutes_override)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error

    try:
        get_res = requests.get(
            f"{SPRING_BOOT_URL}/api/assessments/{req.astronaut_id}", params={"day": req.day}, timeout=5
        )
        if not get_res.ok:
            raise backend_error(get_res)
        current = get_res.json()

        measurement = build_measurement(current, extra)
        post_res = requests.post(f"{SPRING_BOOT_URL}/api/measurements", json=measurement, timeout=5)
        if not post_res.ok:
            raise backend_error(post_res)
    except requests.RequestException as error:
        raise HTTPException(status_code=502, detail=f"Mission computer not reachable at {SPRING_BOOT_URL}") from error

    previous = next((i["value"] for i in current["indicators"] if i["id"] == "resistive"), None) or 0
    return {
        "synced": True,
        "exercise_id": state["exercise_id"],
        "reps_completed": state["reps"],
        "credited_minutes": extra,
        "previous_ared_minutes": previous,
        "new_ared_minutes": measurement["compliance"]["ared_resistive_minutes"],
        "updated_assessment": post_res.json(),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=int(os.getenv("CV_PORT", "5001")))
