package com.nasa.spacehealth.engine;

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
import com.nasa.spacehealth.model.Status;
import com.nasa.spacehealth.rules.Rules;
import com.nasa.spacehealth.rules.Rules.EnvironmentPreset;
import com.nasa.spacehealth.rules.Rules.IndicatorRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.nasa.spacehealth.engine.PyFormat.fixed;
import static com.nasa.spacehealth.engine.PyFormat.general;
import static com.nasa.spacehealth.engine.PyFormat.round;
import static com.nasa.spacehealth.engine.PyFormat.signedFixed;
import static com.nasa.spacehealth.engine.RecordFields.isTrue;
import static com.nasa.spacehealth.engine.RecordFields.number;
import static com.nasa.spacehealth.engine.RecordFields.numberOrZero;

/*
 * Java version of contract/status_engine.py. All thresholds come from rules.json.
 *
 * Each indicator gets three checks and the worst one wins:
 *   - absolute limits (work from day 1)
 *   - Z score against the astronaut's own baseline (mission days 1-7)
 *   - 7-day drift (slope), which can only raise it to YELLOW
 * A Z-based RED needs two bad readings in a row; a single one becomes YELLOW + re-measure.
 * Region = worst member, overall = worst region.
 *
 * Operation order follows the Python code closely so the numbers come out identical
 * (StatusEngineTest checks this against the example files).
 */
@Service
public class StatusEngine {

    private static final int TREND_DAYS = 14;

    private record RegionResult(int status, List<String> drivers) {
    }

    private final Rules rules;
    private final EnvironmentResolver environments;
    private final ObjectMapper mapper;
    private final Map<String, IndicatorRule> indicatorsById = new LinkedHashMap<>();
    private final Map<String, String> regionOfIndicator = new LinkedHashMap<>();

    public StatusEngine(Rules rules, EnvironmentResolver environments, ObjectMapper mapper) {
        this.rules = rules;
        this.environments = environments;
        this.mapper = mapper;
        rules.indicators().forEach(i -> indicatorsById.put(i.id(), i));
        rules.regions().forEach((region, members) -> members.forEach(m -> regionOfIndicator.put(m, region)));
    }

    IndicatorRule indicator(String id) {
        return indicatorsById.get(id);
    }

    // Assesses the last record in the list. Records must be mission days 1..N of one astronaut.
    public AssessmentResponse assess(List<DailyMeasurementAndCompliance> records, String envId,
                                     Long requestedDistanceKm, String nowIso) {
        if (records.isEmpty()) {
            throw new IllegalArgumentException("No measurements to assess");
        }
        EnvironmentPreset env = environments.preset(envId);
        long distanceKm = environments.distanceKm(env, requestedDistanceKm);
        List<JsonNode> days = records.stream().map(r -> (JsonNode) mapper.valueToTree(r)).toList();
        JsonNode today = days.get(days.size() - 1);

        Map<String, IndicatorResult> results = assessIndicators(days, env);
        Map<String, RegionResult> regions = assessRegions(results);
        int overall = regions.values().stream().mapToInt(RegionResult::status).max().orElse(0);

        List<IndicatorAssessment> indicators = toIndicatorAssessments(results, days);
        IndicatorResult resistive = results.get("resistive");
        IndicatorResult ration = results.get("ration");
        int streak = complianceStreak(days, env);

        double delay = environments.oneWayDelaySeconds(distanceKm);
        boolean autonomous = environments.isAutonomous(delay);
        boolean triage = results.values().stream().anyMatch(r -> r.absLevel == 2);

        EnvironmentProfile envProfile = environments.profile(envId, requestedDistanceKm,
                number(today, "habitat.radiation_mSv_day"), number(today, "habitat.cabin_co2_mmHg"));

        Map<String, RegionAssessment> regionOut = new LinkedHashMap<>();
        regions.forEach((id, r) -> regionOut.put(id,
                new RegionAssessment(id, Status.of(r.status()), r.drivers(), rules.regions().get(id))));

        return new AssessmentResponse(
                Contract.SCHEMA_VERSION,
                today.path("astronaut_id").asText(),
                today.path("mission_day").asInt(),
                nowIso,
                envProfile,
                Status.of(overall),
                indicators,
                regionOut,
                new AssessmentResponse.ComplianceSummary(streak, resistive.consecutiveLowDays,
                        ration.consecutiveLowDays),
                flags(results, indicators),
                new AssessmentResponse.Comms(round(delay, 3), autonomous ? CommMode.AUTONOMOUS : CommMode.LIVE,
                        autonomous ? "AUTONOMOUS_TRIAGE_STORE_AND_FORWARD" : "CALL_FLIGHT_SURGEON"),
                triage,
                Contract.THRESHOLDS_STATUS,
                Contract.DISCLAIMER);
    }

    private Map<String, IndicatorResult> assessIndicators(List<JsonNode> days, EnvironmentPreset env) {
        JsonNode today = days.get(days.size() - 1);
        Map<String, IndicatorResult> results = new LinkedHashMap<>();

        for (IndicatorRule ind : rules.indicators()) {
            switch (ind.kind()) {
                case "anchor" -> {
                    List<Double> series = new ArrayList<>();
                    days.forEach(d -> series.add(number(d, ind.path())));
                    results.put(ind.id(), assessAnchor(ind, series));
                }
                case "limit_only" -> results.put(ind.id(), assessLimitOnly(ind, number(today, ind.path())));
                case "ratio" -> results.put(ind.id(), assessRadiation(number(today, ind.path()), env));
                default -> {
                    // compliance and composite indicators are handled below
                }
            }
        }
        results.put("resistive", assessResistive(days, env));
        results.put("ration", assessRation(days));
        results.put("immune_risk", assessImmuneRisk(results));
        return results;
    }

    IndicatorResult assessAnchor(IndicatorRule ind, List<Double> series) {
        return assessAnchor(ind, series, null, true);
    }

    // series.get(i) is the value on mission day i+1 (null = not measured); the last one is today.
    IndicatorResult assessAnchor(IndicatorRule ind, List<Double> series, Double floorSd, boolean driftNeedsZ) {
        Rules.Global g = rules.global();
        IndicatorResult out = new IndicatorResult();
        Double today = series.isEmpty() ? null : series.get(series.size() - 1);
        if (today == null) {
            out.reasons.add("no reading today");
            return out;
        }

        checkAbsoluteLimits(ind, today, out);

        double[] baseline = series.subList(0, Math.min(g.anchorDays(), series.size())).stream()
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).toArray();
        if (series.size() <= g.anchorDays() || baseline.length < g.minPoints()) {
            return out;
        }

        double mean = Stats.mean(baseline);
        double sdRaw = Stats.sampleStd(baseline);
        double sd = Math.max(sdRaw, floorSd != null ? floorSd : ind.floorSd());
        out.anchor = new Anchor(round(mean, 3), round(sdRaw, 3), round(sd, 3), baseline.length);

        double z = worse(ind, (today - mean) / sd);
        out.z = round(z, 2);
        if (z >= g.zRed()) {
            Double prev = series.size() >= 2 ? series.get(series.size() - 2) : null;
            boolean prevAlsoHigh = prev != null && worse(ind, (prev - mean) / sd) >= g.zYellow();
            if (!g.redZNeedsConfirmation() || prevAlsoHigh) {
                out.flag(2, "Z=" + fixed(z, 1) + " on two readings in a row (own normal " + fixed(mean, 1) + ")");
            } else {
                out.remeasure = true;
                out.flag(1, "Z=" + fixed(z, 1) + " on a single reading: re-measure to confirm");
            }
        } else if (z >= g.zYellow()) {
            out.flag(1, "Z=" + fixed(z, 1) + " vs own normal " + fixed(mean, 1));
        }

        checkDrift(ind, series, sd, z >= g.driftMinZ() || !driftNeedsZ, out);
        return out;
    }

    private void checkDrift(IndicatorRule ind, List<Double> series, double sd, boolean zHighEnough,
                            IndicatorResult out) {
        Rules.Global g = rules.global();
        int window = g.driftWindowDays();
        List<Double> recent = series.subList(Math.max(0, series.size() - window), series.size());
        List<Integer> measured = new ArrayList<>();
        for (int i = 0; i < recent.size(); i++) {
            if (recent.get(i) != null) {
                measured.add(i);
            }
        }
        if (measured.size() < g.minPoints()) {
            return;
        }

        double slope = Stats.slope(measured.stream().mapToDouble(Integer::doubleValue).toArray(),
                measured.stream().mapToDouble(recent::get).toArray());
        double driftZ = worse(ind, slope * (window - 1) / sd);
        out.slopePerDay = round(slope, 3);
        out.driftZ = round(driftZ, 2);
        if (driftZ >= g.driftYellow() && zHighEnough) {
            out.flag(1, "drifting " + signedFixed(slope, 2) + " " + ind.unit() + "/day (drift Z="
                    + fixed(driftZ, 1) + ")");
        }
    }

    private IndicatorResult assessLimitOnly(IndicatorRule ind, Double value) {
        IndicatorResult out = new IndicatorResult();
        if (value != null) {
            checkAbsoluteLimits(ind, value, out);
        }
        return out;
    }

    private void checkAbsoluteLimits(IndicatorRule ind, double x, IndicatorResult out) {
        Map<String, Double> lim = ind.abs() == null ? Map.of() : ind.abs();
        String value = general(x) + " " + ind.unit();
        int level = 0;
        String why = null;
        if (lim.containsKey("red_hi") && x > lim.get("red_hi")) {
            level = 2;
            why = value + " above red limit " + general(lim.get("red_hi"));
        } else if (lim.containsKey("red_lo") && x < lim.get("red_lo")) {
            level = 2;
            why = value + " below red limit " + general(lim.get("red_lo"));
        } else if (lim.containsKey("yellow_hi") && x >= lim.get("yellow_hi")) {
            level = 1;
            why = value + " at/above yellow limit " + general(lim.get("yellow_hi"));
        } else if (lim.containsKey("yellow_lo") && x <= lim.get("yellow_lo")) {
            level = 1;
            why = value + " at/below yellow limit " + general(lim.get("yellow_lo"));
        }
        out.absLevel = level;
        if (why != null) {
            out.flag(level, why);
        }
    }

    private IndicatorResult assessRadiation(Double dose, EnvironmentPreset env) {
        IndicatorResult out = new IndicatorResult();
        if (dose == null) {
            return out;
        }
        Rules.Radiation cfg = rules.radiation();
        double expected = env.radiationExpectedMsvDay();
        double ratio = dose / expected;
        out.ratio = round(ratio, 2);

        String label = cfg.eventLabel();
        String msg = general(dose) + " mSv/day is " + fixed(ratio, 1) + "x the expected " + general(expected)
                + (label != null && !label.isEmpty() ? " - " + label : "");
        if (ratio >= cfg.ratioRed()) {
            out.flag(2, msg);
        } else if (ratio >= cfg.ratioYellow()) {
            out.flag(1, msg);
        }
        return out;
    }

    private double resistiveLowThreshold(EnvironmentPreset env) {
        return rules.compliance().resistive().lowFractionOfTarget() * env.resistiveTargetMin();
    }

    // Only ever YELLOW: bone loss is a slow problem, not an emergency.
    private IndicatorResult assessResistive(List<JsonNode> days, EnvironmentPreset env) {
        double threshold = resistiveLowThreshold(env);
        List<Double> minutes = days.stream().map(d -> numberOrZero(d, "compliance.ared_resistive_minutes")).toList();
        int lowDays = Stats.trailingRun(minutes, m -> m < threshold);

        IndicatorResult out = new IndicatorResult();
        out.consecutiveLowDays = lowDays;
        if (lowDays >= rules.compliance().resistive().lowDaysYellow()) {
            out.flag(1, "under " + general(threshold) + " min resistive exercise for " + lowDays
                    + " days (target " + env.resistiveTargetMin() + ")");
        }
        return out;
    }

    private IndicatorResult assessRation(List<JsonNode> days) {
        Rules.ComplianceRules.Ration cfg = rules.compliance().ration();
        List<Double> intake = days.stream().map(d -> numberOrZero(d, "compliance.ration_intake_pct")).toList();
        int lowDays = Stats.trailingRun(intake, p -> p < cfg.lowPct());
        int veryLowDays = Stats.trailingRun(intake, p -> p < cfg.veryLowPct());

        IndicatorResult out = new IndicatorResult();
        out.consecutiveLowDays = lowDays;
        if (veryLowDays >= cfg.veryLowDaysRed()) {
            out.flag(2, "ration under " + cfg.veryLowPct() + "% for " + veryLowDays + " days");
        } else if (lowDays >= cfg.lowDaysYellow()) {
            out.flag(1, "ration under " + cfg.lowPct() + "% for " + lowDays + " days");
        }
        return out;
    }

    // High radiation plus any immune-related sign at the same time. Uses the rounded z and ratio, like Python.
    private IndicatorResult assessImmuneRisk(Map<String, IndicatorResult> results) {
        Rules.Composites.ImmuneRisk cfg = rules.composites().immuneRisk();
        IndicatorResult out = new IndicatorResult();
        Double ratio = results.containsKey("radiation") ? results.get("radiation").ratio : null;
        if (ratio == null || ratio < cfg.radiationRatioMin()) {
            return out;
        }

        List<String> signs = new ArrayList<>();
        for (String id : cfg.members()) {
            Double z = results.get(id).z;
            if (z != null && z >= cfg.mildZ()) {
                signs.add(id);
            }
        }
        for (String id : cfg.members()) {
            if (!signs.contains(id) && results.get(id).status >= 1) {
                signs.add(id);
            }
        }
        if (!signs.isEmpty()) {
            out.flag(1, "radiation " + fixed(ratio, 1) + "x expected together with " + String.join(", ", signs));
        }
        return out;
    }

    // Days in a row (ending today) with vitals logged, enough exercise and enough food.
    private int complianceStreak(List<JsonNode> days, EnvironmentPreset env) {
        double exerciseMin = resistiveLowThreshold(env);
        int rationMin = rules.compliance().ration().lowPct();
        return Stats.trailingRun(days, d -> isTrue(d, "compliance.morning_vitals_logged")
                && numberOrZero(d, "compliance.ared_resistive_minutes") >= exerciseMin
                && numberOrZero(d, "compliance.ration_intake_pct") >= rationMin);
    }

    private Map<String, RegionResult> assessRegions(Map<String, IndicatorResult> results) {
        Map<String, RegionResult> regions = new LinkedHashMap<>();
        rules.regions().forEach((region, members) -> {
            int worst = members.stream().mapToInt(m -> results.get(m).status).max().orElse(0);
            List<String> drivers = members.stream()
                    .filter(m -> worst > 0 && results.get(m).status == worst)
                    .toList();
            regions.put(region, new RegionResult(worst, drivers));
        });
        return regions;
    }

    private List<IndicatorAssessment> toIndicatorAssessments(Map<String, IndicatorResult> results,
                                                             List<JsonNode> days) {
        JsonNode today = days.get(days.size() - 1);
        List<JsonNode> recent = days.subList(Math.max(0, days.size() - TREND_DAYS), days.size());
        List<IndicatorAssessment> out = new ArrayList<>();
        for (IndicatorRule ind : rules.indicators()) {
            IndicatorResult r = results.get(ind.id());
            List<Double> trend = new ArrayList<>();
            if (ind.path() != null) {
                recent.forEach(d -> trend.add(number(d, ind.path())));
            }
            out.add(new IndicatorAssessment(ind.id(), ind.label(), ind.unit(), regionOfIndicator.get(ind.id()),
                    number(today, ind.path()), Status.of(r.status), List.copyOf(r.reasons), r.anchor, r.z,
                    r.slopePerDay, r.driftZ, r.absLevel, r.remeasure, trend, r.ratio, r.consecutiveLowDays));
        }
        return out;
    }

    private static List<String> flags(Map<String, IndicatorResult> results, List<IndicatorAssessment> indicators) {
        List<String> flags = new ArrayList<>();
        if (results.get("immune_risk").status >= 1) {
            flags.add("SYSTEMIC_IMMUNE");
        }
        if (results.get("resistive").status >= 1) {
            flags.add("SPINE_BONE");
        }
        if (results.get("ration").status >= 1) {
            flags.add("INADEQUATE_NUTRITION");
        }
        if (indicators.stream().anyMatch(IndicatorAssessment::remeasure)) {
            flags.add("REMEASURE_NEEDED");
        }
        return flags;
    }

    // Positive means "worse" for this indicator.
    private static double worse(IndicatorRule ind, double delta) {
        return switch (ind.bad()) {
            case "high" -> delta;
            case "low" -> -delta;
            default -> Math.abs(delta);
        };
    }
}
