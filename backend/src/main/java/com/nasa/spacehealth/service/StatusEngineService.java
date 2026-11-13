package com.nasa.spacehealth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.model.AssessmentResponse;
import com.nasa.spacehealth.model.CommMode;
import com.nasa.spacehealth.model.Contract;
import com.nasa.spacehealth.model.DailyMeasurementAndCompliance;
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.model.IndicatorAssessment;
import com.nasa.spacehealth.model.IndicatorAssessment.Anchor;
import com.nasa.spacehealth.model.RegionAssessment;
import com.nasa.spacehealth.model.Rules;
import com.nasa.spacehealth.model.Rules.EnvironmentPreset;
import com.nasa.spacehealth.model.Rules.IndicatorRule;
import com.nasa.spacehealth.model.Status;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.nasa.spacehealth.service.PyFormat.fixed;
import static com.nasa.spacehealth.service.PyFormat.general;
import static com.nasa.spacehealth.service.PyFormat.round;
import static com.nasa.spacehealth.service.PyFormat.signedFixed;

/**
 * Java port of contract/status_engine.py. Reads rules.json (single source of truth) through {@link Rules}.
 *
 * Per indicator, three independent tests; the worst wins:
 *   1. Absolute limit - hard limit from rules.json (yellow / red). Works from day 1.
 *   2. Anchor Z       - distance from the astronaut's OWN normal (mission days 1-7), floored SD.
 *   3. Drift          - 7-day OLS slope in the bad direction. Can only make an indicator YELLOW.
 *
 * Safety valves
 *   - Z-based RED needs the previous reading to be Z-yellow too, otherwise YELLOW + "re-measure".
 *     (Absolute RED is never delayed.)
 *   - Drift only counts if today's value is also displaced (Z >= drift_min_z) in the bad direction.
 *
 * Region status = worst member. Overall = worst region.
 *
 * The port keeps the reference's order of floating-point operations and its quirks on purpose (for example the
 * immune composite compares the ROUNDED z and radiation ratio), so the output is identical field for field.
 * StatusEngineServiceTest proves that against contract/example_assessment_*.json.
 */
@Service
public class StatusEngineService {

    /** Mirrors the per-indicator dict built by the Python engine. */
    static final class IndicatorResult {
        int status;
        final List<String> reasons = new ArrayList<>();
        Double z;
        Double slopePerDay;
        Double driftZ;
        boolean absLimitHit;
        int absLevel;
        boolean remeasure;
        Anchor anchor;
        Double ratio;
        Integer consecutiveLowDays;
    }

    private record RegionResult(int status, List<String> drivers) {
    }

    private record DayResult(Map<String, IndicatorResult> indicators, Map<String, RegionResult> regions, int overall,
                             int streak, long distanceKm, double oneWayDelayS, boolean autonomous,
                             boolean triageRecommended) {
    }

    private record AbsHit(int level, String why) {
    }

    private final Rules rules;
    private final ObjectMapper mapper;
    private final Map<String, IndicatorRule> indicatorById = new LinkedHashMap<>();

    public StatusEngineService(Rules rules, ObjectMapper mapper) {
        this.rules = rules;
        this.mapper = mapper;
        rules.indicators().forEach(i -> indicatorById.put(i.id(), i));
    }

    public IndicatorRule indicator(String id) {
        return indicatorById.get(id);
    }

    // ---------------------------------------------------------------- helpers

    /** Python _get(): walk a dotted path, null when anything on the way is missing or null. */
    private static JsonNode node(JsonNode rec, String path) {
        if (path == null) {
            return null;
        }
        JsonNode cur = rec;
        for (String part : path.split("\\.")) {
            if (cur == null || !cur.isObject()) {
                return null;
            }
            cur = cur.get(part);
        }
        return (cur == null || cur.isNull()) ? null : cur;
    }

    private static Double num(JsonNode rec, String path) {
        JsonNode n = node(rec, path);
        return (n != null && n.isNumber()) ? n.doubleValue() : null;
    }

    /** Python "(value or 0)". */
    private static double numOrZero(JsonNode rec, String path) {
        Double v = num(rec, path);
        return v == null ? 0.0 : v;
    }

    private static boolean truthy(JsonNode rec, String path) {
        JsonNode n = node(rec, path);
        return n != null && (n.isBoolean() ? n.booleanValue() : n.isNumber() && n.doubleValue() != 0.0);
    }

    /** Signed difference: positive means "worse" for this indicator. */
    private static double worse(IndicatorRule ind, double delta) {
        return switch (ind.bad()) {
            case "high" -> delta;
            case "low" -> -delta;
            default -> Math.abs(delta);
        };
    }

    private static AbsHit absLevel(IndicatorRule ind, double x) {
        Map<String, Double> a = ind.abs() == null ? Map.of() : ind.abs();
        String unit = ind.unit();
        if (a.containsKey("red_hi") && x > a.get("red_hi")) {
            return new AbsHit(2, general(x) + " " + unit + " above red limit " + general(a.get("red_hi")));
        }
        if (a.containsKey("red_lo") && x < a.get("red_lo")) {
            return new AbsHit(2, general(x) + " " + unit + " below red limit " + general(a.get("red_lo")));
        }
        if (a.containsKey("yellow_hi") && x >= a.get("yellow_hi")) {
            return new AbsHit(1, general(x) + " " + unit + " at/above yellow limit " + general(a.get("yellow_hi")));
        }
        if (a.containsKey("yellow_lo") && x <= a.get("yellow_lo")) {
            return new AbsHit(1, general(x) + " " + unit + " at/below yellow limit " + general(a.get("yellow_lo")));
        }
        return new AbsHit(0, null);
    }

    // Plain left-to-right sums, like numpy for the short (<= 7 element) arrays the engine uses.
    private static double mean(double[] v) {
        double sum = 0.0;
        for (double x : v) {
            sum += x;
        }
        return sum / v.length;
    }

    /** numpy std(ddof=1). */
    private static double sampleStd(double[] v) {
        double m = mean(v);
        double sum = 0.0;
        for (double x : v) {
            double d = x - m;
            sum += d * d;
        }
        return Math.sqrt(sum / (v.length - 1));
    }

    /** OLS slope of vals over idx. */
    private static double slope(double[] idx, double[] vals) {
        double tMean = mean(idx);
        double vMean = mean(vals);
        double num = 0.0;
        double den = 0.0;
        for (int i = 0; i < idx.length; i++) {
            double tm = idx[i] - tMean;
            num += tm * (vals[i] - vMean);
            den += tm * tm;
        }
        return num / den;
    }

    /** Length of the run at the END of the list for which the predicate holds. */
    private static <T> int consecutive(List<T> values, Predicate<T> pred) {
        int n = 0;
        for (int i = values.size() - 1; i >= 0; i--) {
            if (!pred.test(values.get(i))) {
                break;
            }
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- core rule

    IndicatorResult assessAnchor(IndicatorRule ind, List<Double> series) {
        return assessAnchor(ind, series, null, true);
    }

    /** series.get(i) = value on mission day i+1 (null = missing). Last element = today. */
    IndicatorResult assessAnchor(IndicatorRule ind, List<Double> series, Double floorSd, boolean driftRequiresZ) {
        Rules.Global g = rules.global();
        double floor = floorSd != null ? floorSd : ind.floorSd();
        IndicatorResult out = new IndicatorResult();
        Double today = series.isEmpty() ? null : series.get(series.size() - 1);
        if (today == null) {
            out.reasons.add("no reading today");
            return out;
        }

        AbsHit hit = absLevel(ind, today);
        int status = hit.level();
        out.absLevel = hit.level();
        if (hit.why() != null) {
            out.reasons.add(hit.why());
            out.absLimitHit = true;
        }

        double[] anchor = series.subList(0, Math.min(g.anchorDays(), series.size())).stream()
                .filter(v -> v != null).mapToDouble(Double::doubleValue).toArray();
        if (series.size() > g.anchorDays() && anchor.length >= g.minPoints()) {
            double mu = mean(anchor);
            double sdRaw = sampleStd(anchor);
            double sdEff = Math.max(sdRaw, floor);
            out.anchor = new Anchor(round(mu, 3), round(sdRaw, 3), round(sdEff, 3), anchor.length);
            double z = worse(ind, (today - mu) / sdEff);
            out.z = round(z, 2);

            if (z >= g.zRed()) {
                Double prev = series.size() >= 2 ? series.get(series.size() - 2) : null;
                Double prevZ = prev != null ? worse(ind, (prev - mu) / sdEff) : null;
                if (!g.redZNeedsConfirmation() || (prevZ != null && prevZ >= g.zYellow())) {
                    status = Math.max(status, 2);
                    out.reasons.add("Z=" + fixed(z, 1) + " on two readings in a row (own normal " + fixed(mu, 1) + ")");
                } else {
                    status = Math.max(status, 1);
                    out.remeasure = true;
                    out.reasons.add("Z=" + fixed(z, 1) + " on a single reading: re-measure to confirm");
                }
            } else if (z >= g.zYellow()) {
                status = Math.max(status, 1);
                out.reasons.add("Z=" + fixed(z, 1) + " vs own normal " + fixed(mu, 1));
            }

            int w = g.driftWindowDays();
            List<Double> win = series.subList(Math.max(0, series.size() - w), series.size());
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < win.size(); i++) {
                if (win.get(i) != null) {
                    idx.add(i);
                }
            }
            if (idx.size() >= g.minPoints()) {
                double beta = slope(idx.stream().mapToDouble(Integer::doubleValue).toArray(),
                        idx.stream().mapToDouble(win::get).toArray());
                out.slopePerDay = round(beta, 3);
                double dz = worse(ind, beta * (w - 1) / sdEff);
                out.driftZ = round(dz, 2);
                if (dz >= g.driftYellow() && (z >= g.driftMinZ() || !driftRequiresZ)) {
                    status = Math.max(status, 1);
                    out.reasons.add("drifting " + signedFixed(beta, 2) + " " + ind.unit() + "/day (drift Z="
                            + fixed(dz, 1) + ")");
                }
            }
        }
        out.status = status;
        return out;
    }

    // ---------------------------------------------------------------- day-level assessment

    /** records: contiguous mission days 1..N as JSON trees (exactly the contract's DailyMeasurementAndCompliance). */
    private DayResult assessDay(List<JsonNode> records, EnvironmentPreset env, long distanceKm) {
        Map<String, IndicatorResult> res = new LinkedHashMap<>();

        for (IndicatorRule ind : rules.indicators()) {
            switch (ind.kind()) {
                case "anchor" -> {
                    List<Double> series = new ArrayList<>();
                    for (JsonNode r : records) {
                        series.add(num(r, ind.path()));
                    }
                    res.put(ind.id(), assessAnchor(ind, series));
                }
                case "limit_only" -> {
                    Double v = num(records.get(records.size() - 1), ind.path());
                    IndicatorResult r = new IndicatorResult();
                    if (v != null) {
                        AbsHit hit = absLevel(ind, v);
                        r.status = hit.level();
                        r.absLevel = hit.level();
                        if (hit.why() != null) {
                            r.reasons.add(hit.why());
                            r.absLimitHit = true;
                        }
                    }
                    res.put(ind.id(), r);
                }
                case "ratio" -> {
                    Double v = num(records.get(records.size() - 1), ind.path());
                    IndicatorResult r = new IndicatorResult();
                    if (v != null) {
                        double expected = env.radiationExpectedMsvDay();
                        double ratio = v / expected;
                        r.ratio = round(ratio, 2);
                        String label = rules.radiation().eventLabel();
                        String msg = general(v) + " mSv/day is " + fixed(ratio, 1) + "x the expected " + general(expected)
                                + (label != null && !label.isEmpty() ? " - " + label : "");
                        if (ratio >= rules.radiation().ratioRed()) {
                            r.status = 2;
                            r.reasons.add(msg);
                        } else if (ratio >= rules.radiation().ratioYellow()) {
                            r.status = 1;
                            r.reasons.add(msg);
                        }
                    }
                    res.put(ind.id(), r);
                }
                default -> {
                    // "compliance" and "composite" indicators are computed below
                }
            }
        }

        // compliance ------------------------------------------------------------------
        Rules.ComplianceRules cfg = rules.compliance();
        int target = env.resistiveTargetMin();
        double lowThr = cfg.resistive().lowFractionOfTarget() * target;
        List<Double> mins = records.stream().map(r -> numOrZero(r, "compliance.ared_resistive_minutes")).toList();
        int lowDays = consecutive(mins, m -> m < lowThr);
        IndicatorResult rr = new IndicatorResult();
        rr.consecutiveLowDays = lowDays;
        if (lowDays >= cfg.resistive().lowDaysYellow()) {
            rr.status = 1; // capped at YELLOW by design: bone loss is chronic, not acute
            rr.reasons.add("under " + general(lowThr) + " min resistive exercise for " + lowDays + " days (target "
                    + target + ")");
        }
        res.put("resistive", rr);

        List<Double> pct = records.stream().map(r -> numOrZero(r, "compliance.ration_intake_pct")).toList();
        int nLow = consecutive(pct, p -> p < cfg.ration().lowPct());
        int nVeryLow = consecutive(pct, p -> p < cfg.ration().veryLowPct());
        IndicatorResult ra = new IndicatorResult();
        ra.consecutiveLowDays = nLow;
        if (nVeryLow >= cfg.ration().veryLowDaysRed()) {
            ra.status = 2;
            ra.reasons.add("ration under " + cfg.ration().veryLowPct() + "% for " + nVeryLow + " days");
        } else if (nLow >= cfg.ration().lowDaysYellow()) {
            ra.status = 1;
            ra.reasons.add("ration under " + cfg.ration().lowPct() + "% for " + nLow + " days");
        }
        res.put("ration", ra);

        // composite immune risk ---------------------------------------------------------
        Rules.Composites.ImmuneRisk comp = rules.composites().immuneRisk();
        Double radRatio = res.containsKey("radiation") ? res.get("radiation").ratio : null;
        IndicatorResult ir = new IndicatorResult();
        if (radRatio != null && radRatio >= comp.radiationRatioMin()) {
            List<String> mild = new ArrayList<>();
            for (String m : comp.members()) {
                Double mz = res.get(m).z;
                if (mz != null && mz >= comp.mildZ()) {
                    mild.add(m);
                }
            }
            for (String m : comp.members()) {
                if (!mild.contains(m) && res.get(m).status >= 1) {
                    mild.add(m);
                }
            }
            if (!mild.isEmpty()) {
                ir.status = 1;
                ir.reasons.add("radiation " + fixed(radRatio, 1) + "x expected together with " + String.join(", ", mild));
            }
        }
        res.put("immune_risk", ir);

        // streak ------------------------------------------------------------------------
        int streak = consecutive(records, r -> truthy(r, "compliance.morning_vitals_logged")
                && numOrZero(r, "compliance.ared_resistive_minutes") >= lowThr
                && numOrZero(r, "compliance.ration_intake_pct") >= cfg.ration().lowPct());

        // regions -----------------------------------------------------------------------
        Map<String, RegionResult> regions = new LinkedHashMap<>();
        int overall = 0;
        for (Map.Entry<String, List<String>> e : rules.regions().entrySet()) {
            int worst = e.getValue().stream().mapToInt(m -> res.get(m).status).max().orElse(0);
            List<String> drivers = e.getValue().stream().filter(m -> worst > 0 && res.get(m).status == worst).toList();
            regions.put(e.getKey(), new RegionResult(worst, drivers));
            overall = Math.max(overall, worst);
        }

        // comms -------------------------------------------------------------------------
        double delayS = distanceKm / rules.constants().cKmS();
        boolean autonomous = delayS > rules.constants().autonomousDelayS();
        boolean triage = res.values().stream().anyMatch(r -> r.absLevel == 2);

        return new DayResult(res, regions, overall, streak, distanceKm, round(delayS, 3), autonomous, triage);
    }

    // ---------------------------------------------------------------- environment

    public EnvironmentPreset preset(String envId) {
        EnvironmentPreset env = rules.environments().get(envId);
        if (env == null) {
            throw new IllegalArgumentException("Unknown environment '" + envId + "'. Valid: " + rules.environments().keySet());
        }
        return env;
    }

    /**
     * Fixed distance for LEO and the Moon; for Mars the rules.json default, or the caller's slider value clamped to
     * the min..max range. A requested distance is ignored where the distance is fixed.
     */
    public long resolveDistanceKm(EnvironmentPreset env, Long requestedKm) {
        Map<String, Long> d = env.distanceKm();
        if (d.get("fixed") != null) {
            return d.get("fixed");
        }
        if (requestedKm == null) {
            return d.get("default");
        }
        long km = requestedKm;
        if (d.get("min") != null) {
            km = Math.max(km, d.get("min"));
        }
        if (d.get("max") != null) {
            km = Math.min(km, d.get("max"));
        }
        return km;
    }

    /** EnvironmentProfile with the computed light-time delay and comm mode. */
    public EnvironmentProfile environmentProfile(String envId, Long requestedDistanceKm, Double radiationMsvDay,
                                                 Double cabinCo2MmHg) {
        EnvironmentPreset env = preset(envId);
        long dist = resolveDistanceKm(env, requestedDistanceKm);
        double delayS = dist / rules.constants().cKmS();
        boolean autonomous = delayS > rules.constants().autonomousDelayS();
        Map<String, Long> d = env.distanceKm();
        return new EnvironmentProfile(envId, env.label(), env.environment(), env.body(), env.gravityG(), dist,
                round(delayS, 3), env.commLabel(), autonomous ? CommMode.AUTONOMOUS : CommMode.LIVE,
                env.radiationExpectedMsvDay(), radiationMsvDay, cabinCo2MmHg, env.resistiveTargetMin(),
                d.get("min") != null && d.get("max") != null
                        ? new EnvironmentProfile.DistanceRange(d.get("min"), d.get("max")) : null);
    }

    // ---------------------------------------------------------------- contract output

    /**
     * Python to_assessment(): an AssessmentResponse for the LAST record in {@code records}.
     *
     * @param records             contiguous mission days 1..N for one astronaut
     * @param envId               habitat to judge against (radiation expectation, resistive target, comm delay)
     * @param requestedDistanceKm optional Earth distance for the Mars habitats (null = rules.json default)
     * @param nowIso              value for generated_at_iso
     */
    public AssessmentResponse assess(List<DailyMeasurementAndCompliance> records, String envId,
                                     Long requestedDistanceKm, String nowIso) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No measurements to assess");
        }
        EnvironmentPreset env = preset(envId);
        List<JsonNode> trees = records.stream().map(r -> (JsonNode) mapper.valueToTree(r)).toList();
        JsonNode last = trees.get(trees.size() - 1);
        DayResult a = assessDay(trees, env, resolveDistanceKm(env, requestedDistanceKm));

        EnvironmentProfile envOut = environmentProfile(envId, requestedDistanceKm,
                num(last, "habitat.radiation_mSv_day"), num(last, "habitat.cabin_co2_mmHg"));

        Map<String, String> regionOf = new LinkedHashMap<>();
        rules.regions().forEach((rid, members) -> members.forEach(m -> regionOf.put(m, rid)));

        List<JsonNode> recent = trees.subList(Math.max(0, trees.size() - 14), trees.size());
        List<IndicatorAssessment> indicators = new ArrayList<>();
        for (IndicatorRule ind : rules.indicators()) {
            IndicatorResult r = a.indicators().get(ind.id());
            List<Double> trend = new ArrayList<>();
            if (ind.path() != null) {
                recent.forEach(x -> trend.add(num(x, ind.path())));
            }
            indicators.add(new IndicatorAssessment(ind.id(), ind.label(), ind.unit(), regionOf.get(ind.id()),
                    num(last, ind.path()), Status.of(r.status), List.copyOf(r.reasons), r.anchor, r.z, r.slopePerDay,
                    r.driftZ, r.absLevel, r.remeasure, trend, r.ratio, r.consecutiveLowDays));
        }

        Map<String, RegionAssessment> regions = new LinkedHashMap<>();
        a.regions().forEach((rid, v) -> regions.put(rid,
                new RegionAssessment(rid, Status.of(v.status()), v.drivers(), rules.regions().get(rid))));

        Map<String, IndicatorResult> ix = a.indicators();
        List<String> flags = new ArrayList<>();
        if (ix.get("immune_risk").status >= 1) {
            flags.add("SYSTEMIC_IMMUNE");
        }
        if (ix.get("resistive").status >= 1) {
            flags.add("SPINE_BONE");
        }
        if (ix.get("ration").status >= 1) {
            flags.add("INADEQUATE_NUTRITION");
        }
        if (indicators.stream().anyMatch(IndicatorAssessment::remeasure)) {
            flags.add("REMEASURE_NEEDED");
        }

        boolean auto = a.autonomous();
        return new AssessmentResponse(
                Contract.SCHEMA_VERSION,
                last.path("astronaut_id").asText(),
                last.path("mission_day").asInt(),
                nowIso,
                envOut,
                Status.of(a.overall()),
                indicators,
                regions,
                new AssessmentResponse.ComplianceSummary(a.streak(), ix.get("resistive").consecutiveLowDays,
                        ix.get("ration").consecutiveLowDays),
                flags,
                new AssessmentResponse.Comms(a.oneWayDelayS(), auto ? CommMode.AUTONOMOUS : CommMode.LIVE,
                        auto ? "AUTONOMOUS_TRIAGE_STORE_AND_FORWARD" : "CALL_FLIGHT_SURGEON"),
                a.triageRecommended(),
                Contract.THRESHOLDS_STATUS,
                Contract.DISCLAIMER);
    }
}
