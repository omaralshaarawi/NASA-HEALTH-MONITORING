import numpy as np, json
from status_engine import *
from seed import build

NM = LEVEL
R = IND
anchor_hr = [60, 61, 62, 61, 60, 62, 61]
fails = []
def check(label, cond):
    print(("  PASS " if cond else "  FAIL ") + label)
    if not cond: fails.append(label)

print("TEST 1 small-variance blow-up (HR 61 -> 66)")
h = anchor_hr + [61, 62, 61, 62, 61, 62, 66]
sd = np.std(anchor_hr, ddof=1)
r = assess_anchor(R["hr_rest"], h)
print(f"  naive Z with raw sigma {sd:.2f} = {(66-61)/sd:.1f}; engine z={r['z']} status={NM[r['status']]}")
check("naive Z would scream (>5) but engine stays GREEN", (66-61)/sd > 5 and r["status"] == 0)

print("TEST 2 slow drift +1 bpm/day from day 8")
series = anchor_hr + list(range(62, 76))
first = next(d for d in range(8, len(series)+1) if assess_anchor(R["hr_rest"], series[:d])["status"] >= 1)
roll = [(series[i]-np.mean(series[i-7:i]))/np.std(series[i-7:i], ddof=1) for i in range(7, len(series))]
print(f"  engine first YELLOW on day {first} (HR {series[first-1]}); rolling-7d Z max {max(roll):.2f}")
late = [z for i, z in zip(range(7, len(series)), roll) if i >= 14]   # windows fully inside the ramp
print(f"  rolling-7d Z once the window sits inside the ramp: {min(late):.2f}-{max(late):.2f} (transient at onset reached {max(roll):.2f})")
check("engine flags the drift within 8 days of onset", first - 7 <= 8)
check("rolling baseline goes blind to a steady ramp (Z < 2 throughout)", max(late) < 2.0)
check("engine is still >= YELLOW on day 21", assess_anchor(R["hr_rest"], series[:21])["status"] >= 1)

print("TEST 3 absolute limits are immediate (also inside the anchor window)")
check("HR 102 on day 3 -> RED", assess_anchor(R["hr_rest"], [61, 62, 102])["status"] == 2)
for g, want in [(110, 1), (130, 1), (142, 2), (68, 1), (62, 2), (88, 0)]:
    got = assess_anchor(R["glucose"], [90, 92, 91, 93, 90, 92, 91, 91, g])["status"]
    check(f"glucose {g} -> {NM[want]} (got {NM[got]})", got == want)
check("temp 38.0 -> YELLOW", assess_anchor(R["temp"], [36.8]*8+[38.0])["status"] == 1)
check("temp 38.4 -> RED", assess_anchor(R["temp"], [36.8]*8+[38.4])["status"] == 2)
check("SBP 88 -> RED (low side)", assess_anchor(R["sbp"], [118]*8+[88])["status"] == 2)

print("TEST 4 sleep floor: two nights of 4.8 h after a ~7 h anchor")
sl = [7.0, 6.8, 7.2, 7.0, 6.9, 7.1, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 4.8, 4.8]
for f in (0.5, 1.0):
    r = assess_anchor(R["sleep"], sl, floor_sd=f)
    print(f"  floor {f}: {NM[r['status']]} z={r['z']} {r['reasons']}")
r1 = assess_anchor(R["sleep"], [7.0, 6.8, 7.2, 7.0, 6.9, 7.1, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 5.9])
print(f"  one night of 5.9 h with floor 1.0: {NM[r1['status']]} z={r1['z']};   with floor 0.5: ", end="")
r2 = assess_anchor(R["sleep"], [7.0, 6.8, 7.2, 7.0, 6.9, 7.1, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 5.9], floor_sd=0.5)
print(f"{NM[r2['status']]} z={r2['z']}")
check("two nights of 4.8 h -> at least YELLOW", assess_anchor(R["sleep"], sl)["status"] >= 1)

print("TEST 5 single outlier needs confirmation")
r = assess_anchor(R["pvt"], [250,255,248,252,249,251,250,251,250,249,252,250,251,320])
print(f"  PVT 320: {NM[r['status']]} z={r['z']} remeasure={r['remeasure']}")
r = assess_anchor(R["pvt"], [250,255,248,252,249,251,250,251,250,249,252,250,320,322])
print(f"  PVT 320 then 322: {NM[r['status']]} z={r['z']} {r['reasons']}")
check("two high readings in a row -> RED via Z", r["status"] == 2)

print("TEST 6 seeded astronauts end-to-end")
seed = build()
res = {a: assess_day(rs, "DEEP_SPACECRAFT") for a, rs in seed.items()}
for a, x in res.items():
    print(f"  {a}: overall={NM[x['overall']]} regions={ {k: NM[v['status']] for k,v in x['regions'].items()} } streak={x['streak']} triage={x['triage_recommended']}")
    for iid, ir in x["indicators"].items():
        if ir["status"]: print(f"      {iid}: {NM[ir['status']]}  {ir['reasons']}")
check("Nova is GREEN overall", res["ast_nova"]["overall"] == 0)
check("Nova streak = 14", res["ast_nova"]["streak"] == 14)
k = res["ast_kai"]
check("Kai: HR yellow via drift", k["indicators"]["hr_rest"]["status"] >= 1)
check("Kai: SPINE_BONE yellow", k["regions"]["SPINE_BONE"]["status"] == 1)
check("Kai: immune composite fires", k["indicators"]["immune_risk"]["status"] == 1)
check("Kai: ration flagged", k["indicators"]["ration"]["status"] >= 1)
check("Kai: not an emergency (no RED, no triage)", k["overall"] <= 1 and not k["triage_recommended"])
l = res["ast_lin"]
check("Lin: CHEST_CARDIO RED", l["regions"]["CHEST_CARDIO"]["status"] == 2)
check("Lin: triage recommended", l["triage_recommended"])
check("Lin: HEAD_NEURO not RED", l["regions"]["HEAD_NEURO"]["status"] < 2)

print("TEST 7 delays")
for e, v in RULES["environments"].items():
    d = assess_day(seed["ast_nova"], e)["comms"]
    print(f"  {e:22s} delay {d['one_way_delay_s']:8.3f} s  autonomous={d['autonomous']}")
dmin = RULES["environments"]["DEEP_SPACECRAFT"]["distance_km"]
print("  Mars range one-way: %.2f - %.2f min" % (dmin["min"]/C_KM_S/60, dmin["max"]/C_KM_S/60))

print(f"TEST 8 false alarms on pure noise at the floor SD (z_yellow={G['z_yellow']}, drift_yellow={G['drift_yellow']}; limits excluded)")
rng = np.random.default_rng(7)
bases = {"hr_rest":62,"sbp":120,"dbp":76,"glucose":90,"temp":36.8,"sleep":7.0,"mood":7,"pvt":250}
for req in (False, True):
    rates = []
    for iid, base in bases.items():
        ind = R[iid]; flagged = total = 0
        for _ in range(400):
            x = rng.normal(base, ind["floor_sd"], 14).tolist()
            for d in range(8, 15):
                r = assess_anchor(ind, x[:d], drift_requires_z=req)
                flagged += any("limit" not in s for s in r["reasons"]); total += 1
        rates.append(flagged/total)
    p_any = 1 - np.prod([1-p for p in rates])
    print(f"  drift_requires_z={req}: per-indicator daily alarm {min(rates):.1%}-{max(rates):.1%}; P(some indicator alarms on a day)={p_any:.0%}")

print("\nFAILED:", fails if fails else "none")
raise SystemExit(1 if fails else 0)
