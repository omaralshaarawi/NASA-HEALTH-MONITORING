"""Day record for /sync-to-backend.

Posting a day the backend already has replaces it, so today's other readings are copied over
from the assessment and only the resistive minutes change.
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
    """The user's number if given, otherwise 5 min per rep with a 15 min floor."""
    if minutes_override is not None:
        return max(0, int(minutes_override))
    if reps <= 0:
        raise ValueError("No reps recorded yet. Do some reps, or enter the minutes to credit.")
    return max(15, reps * 5)


def build_measurement(assessment: dict, extra_minutes: int) -> dict:
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
            # not in the assessment; vitals logged today is the closest proxy
            "morning_vitals_logged": any(v is not None for v in vitals.values()),
            "ared_resistive_minutes": min(MAX_RESISTIVE_MINUTES, previous + extra_minutes),
            "ration_intake_pct": values.get("ration") or 0,
        },
    }

    # lapses and trial count aren't in the assessment; the engine only reads the median
    if values.get("pvt") is not None:
        record["pvt"] = {"median_rt_ms": values["pvt"], "lapses_gt500ms": 0, "n_trials": 0, "source": "BROWSER_30S"}

    return record
