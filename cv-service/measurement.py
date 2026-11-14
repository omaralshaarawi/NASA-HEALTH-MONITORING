"""Turns a finished camera workout into a day record the backend accepts.

The backend only takes whole-day records (DailyMeasurementAndCompliance), and posting a day it
already has replaces that day. So today's other readings are carried over from today's
assessment unchanged, and only the resistive-exercise minutes go up.

Kept free of OpenCV and MediaPipe so it can be tested on its own.
"""

MAX_RESISTIVE_MINUTES = 240

VITAL_FIELDS = {
    "hr_rest": "hr_rest_bpm",
    "sbp": "sbp_mmHg",
    "dbp": "dbp_mmHg",
    "glucose": "glucose_mg_dl",
    "temp": "temp_c",
    "sleep": "sleep_h",
    "mood": "mood_1_10",
}


def credited_minutes(reps: int, minutes_override: int | None) -> int:
    """Minutes to add: what the crew member entered, otherwise 5 per rep with a 15 minute floor."""
    if minutes_override is not None:
        return max(0, int(minutes_override))
    if reps <= 0:
        raise ValueError("No reps recorded yet. Do some reps, or enter the minutes to credit.")
    return max(15, reps * 5)


def build_measurement(assessment: dict, extra_minutes: int) -> dict:
    """Today's record, rebuilt from today's assessment, with extra resistive minutes."""
    values = {i["id"]: i.get("value") for i in assessment["indicators"]}
    environment = assessment["environment"]

    vitals = {field: values.get(indicator) for indicator, field in VITAL_FIELDS.items()}
    previous = int(values.get("resistive") or 0)

    record = {
        "astronaut_id": assessment["astronaut_id"],
        "mission_day": assessment["mission_day"],
        "environment_id": environment["id"],
        "source": "SENSOR",
        "vitals": vitals,
        "habitat": {
            "radiation_mSv_day": values.get("radiation", environment["radiation_mSv_day"]),
            "cabin_co2_mmHg": values.get("cabin_co2", environment["cabin_co2_mmHg"]),
        },
        "compliance": {
            # the assessment does not carry this flag; vitals present today is the closest sign of it
            "morning_vitals_logged": any(v is not None for v in vitals.values()),
            "ared_resistive_minutes": min(MAX_RESISTIVE_MINUTES, previous + extra_minutes),
            "ration_intake_pct": values.get("ration") or 0,
        },
    }

    # Only the median reaction time is in the assessment, and it is the only part the engine
    # reads. Lapses and trial count are not known here, so they are sent as 0.
    if values.get("pvt") is not None:
        record["pvt"] = {"median_rt_ms": values["pvt"], "lapses_gt500ms": 0, "n_trials": 0, "source": "BROWSER_30S"}

    return record
