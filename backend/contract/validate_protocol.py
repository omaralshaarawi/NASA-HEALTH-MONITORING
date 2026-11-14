import json, sys, jsonschema
p = json.load(open("triage_protocol.json"))
jsonschema.validate(p, json.load(open("schemas/TriageProtocol.schema.json")))
steps = {s["id"]: s for s in p["steps"]}
errs = []
assert len(steps) == len(p["steps"]), "duplicate ids"
for s in p["steps"]:
    nx = s.get("next", {})
    if s["type"] == "ASK_YES_NO" and set(nx) != {"yes", "no"}: errs.append(f"{s['id']}: ASK_YES_NO needs yes+no")
    if s["type"] in ("SAY", "CONFIRM_DONE", "SYSTEM") and not s.get("terminal") and set(nx) != {"done"}: errs.append(f"{s['id']}: needs next.done")
    if s.get("terminal") and nx: errs.append(f"{s['id']}: terminal step has next")
    for k, t in nx.items():
        if t not in steps: errs.append(f"{s['id']}.{k} -> unknown {t}")
# reachability from start
seen, stack = set(), [p["start"]]
while stack:
    x = stack.pop()
    if x in seen: continue
    seen.add(x); stack += list(steps[x].get("next", {}).values())
unreach = set(steps) - seen
# every step can reach a terminal
term = {i for i, s in steps.items() if s.get("terminal")}
rev = {i: set() for i in steps}
for i, s in steps.items():
    for t in s.get("next", {}).values(): rev[t].add(i)
can, stack = set(term), list(term)
while stack:
    x = stack.pop()
    for y in rev[x]:
        if y not in can: can.add(y); stack.append(y)
dead = set(steps) - can
# scripts must stay short enough to speak, and contain no dosing numbers
long = [i for i, s in steps.items() if len(s["script"].split()) > 55]
dose = [i for i, s in steps.items() if any(w in s["script"].lower() for w in (" mg", "milligram", "aspirin", "nitro"))]
print("steps:", len(steps), "| unreachable:", sorted(unreach), "| cannot reach END:", sorted(dead), "| >55 words:", long, "| dosing words:", dose)
# scenario walk-throughs
def walk(answers, limit=40):
    cur, path = p["start"], []
    while limit:
        s = steps[cur]; path.append(cur)
        if s.get("terminal"): return path
        if s["type"] == "ASK_YES_NO": k = answers.pop(0)
        else: k = "done"
        cur = s["next"][k]; limit -= 1
    return path + ["!!LOOP"]
w1 = walk(["no", "no", "yes", "yes"])           # unresponsive, no pulse, shock advised, then recovers
w2 = walk(["yes", "yes", "yes"])                # conscious, warning signs, stays responsive
w3 = walk(["yes", "no", "no", "yes"])           # conscious -> collapses -> unresponsive but breathing
print("collapse + shock path:", " > ".join(w1)); print("conscious path:", " > ".join(w2)); print("conscious -> collapses path:", " > ".join(w3))
ok = not errs and not unreach and not dead and not long and not dose and w1[-1] == w2[-1] == w3[-1] == "END"
print("errors:", errs or "none"); print("PROTOCOL VALID" if ok else "PROTOCOL INVALID"); sys.exit(0 if ok else 1)
