"""
Reference status engine for the Hour-1 contract. Reads rules.json (single source of truth).

Per indicator, three independent tests; the worst wins:
  1. Absolute limit   - hard limit from rules.json (yellow / red). Works from day 1.
  2. Anchor Z         - distance from the astronaut's OWN normal (mission days 1-7), floored SD.
  3. Drift            - 7-day OLS slope in the bad direction. Can only make an indicator YELLOW.

Safety valves
  * Z-based RED needs the previous reading to be Z-yellow too, otherwise YELLOW + "re-measure".
    (Absolute RED is never delayed.)
  * Drift only counts if today's value is also displaced (Z >= drift_min_z) in the bad direction.

Region status = worst member. Overall = worst region.
Output matches AssessmentResponse in contract.ts (see to_assessment()).
"""
from __future__ import annotations
import json
from pathlib import Path
import numpy as np

RULES = json.loads((Path(__file__).parent / "rules.json").read_text())
G = RULES["global"]
LEVEL = ["GREEN", "YELLOW", "RED"]
IND = {i["id"]: i for i in RULES["indicators"]}
C_KM_S = RULES["constants"]["c_km_s"]


# ---------------------------------------------------------------- helpers
def _get(rec, path):
    cur = rec
    for p in path.split("."):
        if not isinstance(cur, dict):
            return None
        cur = cur.get(p)
    return cur


def _worse(ind, delta):
    """Signed difference -> positive means 'worse' for this indicator."""
    if ind["bad"] == "high":
        return delta
    if ind["bad"] == "low":
        return -delta
    return abs(delta)


def _abs_level(ind, x):
    a = ind["abs"]
    unit = ind["unit"]
    if "red_hi" in a and x > a["red_hi"]:
        return 2, f"{x:g} {unit} above red limit {a['red_hi']:g}"
    if "red_lo" in a and x < a["red_lo"]:
        return 2, f"{x:g} {unit} below red limit {a['red_lo']:g}"
    if "yellow_hi" in a and x >= a["yellow_hi"]:
        return 1, f"{x:g} {unit} at/above yellow limit {a['yellow_hi']:g}"
    if "yellow_lo" in a and x <= a["yellow_lo"]:
        return 1, f"{x:g} {unit} at/below yellow limit {a['yellow_lo']:g}"
    return 0, None


def _slope(idx, vals):
    t = np.asarray(idx, float)
    v = np.asarray(vals, float)
    tm = t - t.mean()
    return float((tm * (v - v.mean())).sum() / (tm ** 2).sum())


def _consec(vals, pred):
    n = 0
    for v in reversed(vals):
        if pred(v):
            n += 1
        else:
            break
    return n


# ---------------------------------------------------------------- core rule
def assess_anchor(ind, series, floor_sd=None, drift_requires_z=True):
    """series[i] = value on mission day i+1 (None = missing). Last element = today."""
    floor = ind["floor_sd"] if floor_sd is None else floor_sd
    today = series[-1]
    out = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None,
               abs_limit_hit=False, abs_level=0, remeasure=False, anchor=None)
    if today is None:
        out["reasons"].append("no reading today")
        return out

    lvl, why = _abs_level(ind, today)
    status = lvl
    out["abs_level"] = lvl
    if why:
        out["reasons"].append(why)
        out["abs_limit_hit"] = True

    anchor = [v for v in series[:G["anchor_days"]] if v is not None]
    if len(series) > G["anchor_days"] and len(anchor) >= G["min_points"]:
        mu = float(np.mean(anchor))
        sd_raw = float(np.std(anchor, ddof=1))
        sd_eff = max(sd_raw, floor)
        out["anchor"] = dict(mean=round(mu, 3), sd_raw=round(sd_raw, 3), sd_eff=round(sd_eff, 3), n=len(anchor))
        z = _worse(ind, (today - mu) / sd_eff)
        out["z"] = round(z, 2)

        if z >= G["z_red"]:
            prev = series[-2] if len(series) >= 2 else None
            prev_z = _worse(ind, (prev - mu) / sd_eff) if prev is not None else None
            if (not G["red_z_needs_confirmation"]) or (prev_z is not None and prev_z >= G["z_yellow"]):
                status = max(status, 2)
                out["reasons"].append(f"Z={z:.1f} on two readings in a row (own normal {mu:.1f})")
            else:
                status = max(status, 1)
                out["remeasure"] = True
                out["reasons"].append(f"Z={z:.1f} on a single reading: re-measure to confirm")
        elif z >= G["z_yellow"]:
            status = max(status, 1)
            out["reasons"].append(f"Z={z:.1f} vs own normal {mu:.1f}")

        W = G["drift_window_days"]
        win = series[-W:]
        idx = [i for i, v in enumerate(win) if v is not None]
        if len(idx) >= G["min_points"]:
            beta = _slope(idx, [win[i] for i in idx])
            out["slope_per_day"] = round(beta, 3)
            dz = _worse(ind, beta * (W - 1) / sd_eff)
            out["drift_z"] = round(dz, 2)
            if dz >= G["drift_yellow"] and (z >= G["drift_min_z"] or not drift_requires_z):
                status = max(status, 1)
                out["reasons"].append(f"drifting {beta:+.2f} {ind['unit']}/day (drift Z={dz:.1f})")
    out["status"] = status
    return out


# ---------------------------------------------------------------- day-level assessment
def assess_day(records, env_id, floor_overrides=None):
    """records: list of DailyMeasurementAndCompliance dicts, contiguous mission days 1..N."""
    env = RULES["environments"][env_id]
    n = len(records)
    res = {}

    def series(path):
        return [_get(r, path) for r in records]

    for ind in RULES["indicators"]:
        iid, kind = ind["id"], ind["kind"]
        if kind == "anchor":
            fl = (floor_overrides or {}).get(iid)
            res[iid] = assess_anchor(ind, series(ind["path"]), floor_sd=fl)
        elif kind == "limit_only":
            v = series(ind["path"])[-1]
            r = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None,
                     abs_limit_hit=False, remeasure=False, anchor=None)
            if v is not None:
                lvl, why = _abs_level(ind, v)
                r["status"] = lvl
                r["abs_level"] = lvl
                if why:
                    r["reasons"].append(why)
                    r["abs_limit_hit"] = True
            res[iid] = r
        elif kind == "ratio":
            v = series(ind["path"])[-1]
            r = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None,
                     abs_limit_hit=False, remeasure=False, anchor=None, ratio=None)
            if v is not None:
                ratio = v / env["radiation_expected_mSv_day"]
                r["ratio"] = round(ratio, 2)
                if ratio >= RULES["radiation"]["ratio_red"]:
                    r["status"] = 2
                    r["reasons"].append(f"{v:g} mSv/day is {ratio:.1f}x the expected {env['radiation_expected_mSv_day']:g}")
                elif ratio >= RULES["radiation"]["ratio_yellow"]:
                    r["status"] = 1
                    r["reasons"].append(f"{v:g} mSv/day is {ratio:.1f}x the expected {env['radiation_expected_mSv_day']:g}")
            res[iid] = r

    # compliance --------------------------------------------------------------------
    cfg = RULES["compliance"]
    target = env["resistive_target_min"]
    low_thr = cfg["resistive"]["low_fraction_of_target"] * target
    mins = [(_get(r, "compliance.ared_resistive_minutes") or 0) for r in records]
    low_days = _consec(mins, lambda m: m < low_thr)
    rr = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None, abs_limit_hit=False,
              remeasure=False, anchor=None, consecutive_low_days=low_days)
    if low_days >= cfg["resistive"]["low_days_yellow"]:
        rr["status"] = 1  # capped at YELLOW by design: bone loss is chronic, not acute
        rr["reasons"].append(f"under {low_thr:g} min resistive exercise for {low_days} days (target {target})")
    res["resistive"] = rr

    pct = [(_get(r, "compliance.ration_intake_pct")) for r in records]
    pct0 = [0 if p is None else p for p in pct]
    nlow = _consec(pct0, lambda p: p < cfg["ration"]["low_pct"])
    nvlow = _consec(pct0, lambda p: p < cfg["ration"]["very_low_pct"])
    ra = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None, abs_limit_hit=False,
              remeasure=False, anchor=None, consecutive_low_days=nlow)
    if nvlow >= cfg["ration"]["very_low_days_red"]:
        ra["status"] = 2
        ra["reasons"].append(f"ration under {cfg['ration']['very_low_pct']}% for {nvlow} days")
    elif nlow >= cfg["ration"]["low_days_yellow"]:
        ra["status"] = 1
        ra["reasons"].append(f"ration under {cfg['ration']['low_pct']}% for {nlow} days")
    res["ration"] = ra

    # composite immune risk ---------------------------------------------------------
    comp = RULES["composites"]["immune_risk"]
    rad_ratio = res["radiation"].get("ratio")
    ir = dict(status=0, reasons=[], z=None, slope_per_day=None, drift_z=None, abs_limit_hit=False,
              remeasure=False, anchor=None)
    if rad_ratio is not None and rad_ratio >= comp["radiation_ratio_min"]:
        mild = [m for m in comp["members"] if res[m]["z"] is not None and res[m]["z"] >= comp["mild_z"]]
        mild += [m for m in comp["members"] if m not in mild and res[m]["status"] >= 1]
        if mild:
            ir["status"] = 1
            ir["reasons"].append(f"radiation {rad_ratio:.1f}x expected together with {', '.join(mild)}")
    res["immune_risk"] = ir

    # streak ----------------------------------------------------------------------------
    def met(r):
        return (bool(_get(r, "compliance.morning_vitals_logged"))
                and (_get(r, "compliance.ared_resistive_minutes") or 0) >= low_thr
                and (_get(r, "compliance.ration_intake_pct") or 0) >= cfg["ration"]["low_pct"])
    streak = _consec(records, met)

    # regions ---------------------------------------------------------------------------
    regions = {}
    for rid, members in RULES["regions"].items():
        worst = max(res[m]["status"] for m in members)
        drivers = [m for m in members if res[m]["status"] == worst and worst > 0]
        regions[rid] = dict(status=worst, drivers=drivers)

    # comms -----------------------------------------------------------------------------
    d = env["distance_km"]
    dist = d.get("fixed", d.get("default"))
    delay_s = dist / C_KM_S
    auto = delay_s > RULES["constants"]["autonomous_delay_s"]
    overall = max(r["status"] for r in regions.values())

    return dict(indicators=res, regions=regions, overall=overall, streak=streak,
                comms=dict(distance_km=dist, one_way_delay_s=round(delay_s, 3), autonomous=auto),
                triage_recommended=any(r.get("abs_level", 0) == 2 for r in res.values()))


def name(i):
    return LEVEL[i]


# ---------------------------------------------------------------- contract output
def to_assessment(records, env_id, now_iso="2026-11-14T08:00:00Z", floor_overrides=None):
    """Return an AssessmentResponse (contract.ts) for the last record in `records`."""
    from datetime import datetime  # noqa: F401  (kept for callers who pass real timestamps)
    a = assess_day(records, env_id, floor_overrides)
    env = RULES["environments"][env_id]
    last = records[-1]
    d = env["distance_km"]
    dist = d.get("fixed", d.get("default"))
    env_out = dict(
        id=env_id, label=env["label"], environment=env["environment"], body=env["body"],
        gravity_g=env["gravity_g"], distance_km=dist,
        one_way_delay_s=a["comms"]["one_way_delay_s"], comm_label=env["comm_label"],
        comm_mode="AUTONOMOUS" if a["comms"]["autonomous"] else "LIVE",
        radiation_expected_mSv_day=env["radiation_expected_mSv_day"],
        radiation_mSv_day=_get(last, "habitat.radiation_mSv_day"),
        cabin_co2_mmHg=_get(last, "habitat.cabin_co2_mmHg"),
        resistive_target_min=env["resistive_target_min"])
    if "min" in d:
        env_out["distance_range_km"] = dict(min=d["min"], max=d["max"])

    region_of = {m: rid for rid, ms in RULES["regions"].items() for m in ms}
    inds = []
    for ind in RULES["indicators"]:
        iid = ind["id"]
        r = a["indicators"][iid]
        trend = [(_get(x, ind["path"]) if ind["path"] else None) for x in records[-14:]]
        item = dict(
            id=iid, label=ind["label"], unit=ind["unit"], region=region_of[iid],
            value=_get(last, ind["path"]) if ind["path"] else None,
            status=LEVEL[r["status"]], reasons=r["reasons"], anchor=r["anchor"], z=r["z"],
            slope_per_day=r["slope_per_day"], drift_z=r["drift_z"], abs_level=r.get("abs_level", 0),
            remeasure=bool(r["remeasure"]), trend=trend if ind["path"] else [])
        if "ratio" in r and r["ratio"] is not None:
            item["ratio"] = r["ratio"]
        if "consecutive_low_days" in r:
            item["consecutive_low_days"] = r["consecutive_low_days"]
        inds.append(item)

    regions = {rid: dict(id=rid, status=LEVEL[v["status"]], drivers=v["drivers"], members=RULES["regions"][rid])
               for rid, v in a["regions"].items()}
    ix = a["indicators"]
    flags = []
    if ix["immune_risk"]["status"] >= 1: flags.append("SYSTEMIC_IMMUNE")
    if ix["resistive"]["status"] >= 1: flags.append("SPINE_BONE")
    if ix["ration"]["status"] >= 1: flags.append("INADEQUATE_NUTRITION")
    if any(i["remeasure"] for i in inds): flags.append("REMEASURE_NEEDED")
    auto = a["comms"]["autonomous"]
    return dict(
        schema_version="1.0.0", astronaut_id=last["astronaut_id"], mission_day=last["mission_day"],
        generated_at_iso=now_iso, environment=env_out, overall=LEVEL[a["overall"]],
        indicators=inds, regions=regions,
        compliance=dict(streak_days=a["streak"], bone_low_days=ix["resistive"]["consecutive_low_days"],
                        nutrition_low_days=ix["ration"]["consecutive_low_days"]),
        flags=flags,
        comms=dict(one_way_delay_s=a["comms"]["one_way_delay_s"], mode="AUTONOMOUS" if auto else "LIVE",
                   emergency_action="AUTONOMOUS_TRIAGE_STORE_AND_FORWARD" if auto else "CALL_FLIGHT_SURGEON"),
        triage_recommended=a["triage_recommended"], thresholds_status="PROTOTYPE",
        disclaimer="Prototype decision-support tool using simulated data. Not a medical device and not a diagnosis. Follow flight-surgeon and onboard medical procedures.")
