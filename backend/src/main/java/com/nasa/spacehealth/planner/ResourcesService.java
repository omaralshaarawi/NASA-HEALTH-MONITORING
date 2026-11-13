package com.nasa.spacehealth.planner;

import com.nasa.spacehealth.engine.PyFormat;
import com.nasa.spacehealth.planner.model.CountermeasurePlanDraft;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import com.nasa.spacehealth.planner.model.ResourceBlock;
import com.nasa.spacehealth.planner.model.ResourceBlock.SessionImpact;
import com.nasa.spacehealth.rules.Rules;
import org.springframework.stereotype.Service;

import java.util.List;

// Java version of contract/resources.py: what a plan costs the closed-loop life support
// in extra food energy, water and CO2. Constants come from rules.json "resource_math".
@Service
public class ResourcesService {

    private final Rules.ResourceMath math;
    private final ExerciseMenu menu;

    public ResourcesService(Rules rules, ExerciseMenu menu) {
        this.math = rules.resourceMath();
        this.menu = menu;
    }

    public SessionImpact sessionImpact(String exerciseId, int minutes, double massKg) {
        ExerciseMenu.Item item = menu.find(exerciseId);
        if (item == null) {
            throw new IllegalArgumentException("Unknown exercise_id '" + exerciseId + "'");
        }
        double met = item.met();
        double hours = minutes / 60.0;
        double gross = met * math.kcalPerMetKgH() * massKg * hours;
        // only the part above resting counts as extra: resting energy is already in the ration
        double extra = Math.max(met - math.restMet(), 0) * math.kcalPerMetKgH() * massKg * hours;

        return new SessionImpact(exerciseId, minutes, met,
                round(gross),
                round(extra),
                round(extra * math.waterMlPerKcal()),
                round(extra * math.co2GPerKcal()));
    }

    public ResourceBlock buildResourceBlock(List<CountermeasurePlanDraft.Session> sessions, double massKg) {
        List<SessionImpact> rows = sessions.stream()
                .map(s -> sessionImpact(s.exerciseId(), s.minutes(), massKg))
                .toList();
        ResourceBlock.Totals totals = new ResourceBlock.Totals(
                rows.stream().mapToLong(SessionImpact::extraKcal).sum(),
                rows.stream().mapToLong(SessionImpact::extraWaterMl).sum(),
                rows.stream().mapToLong(SessionImpact::extraCo2G).sum());
        ResourceBlock.Assumptions assumptions = new ResourceBlock.Assumptions(massKg, math.restMet(),
                math.waterMlPerKcal(), math.co2GPerKcal(), ResourceBlock.FORMULA);
        return new ResourceBlock(assumptions, rows, totals, ResourceBlock.LABEL);
    }

    // Python round(): half to even
    private static long round(double x) {
        return (long) PyFormat.round(x, 0);
    }
}
