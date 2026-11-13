package com.nasa.spacehealth.engine;

import com.nasa.spacehealth.model.IndicatorAssessment.Anchor;

import java.util.ArrayList;
import java.util.List;

// Working result for one indicator while a day is being assessed. status: 0 green, 1 yellow, 2 red.
class IndicatorResult {

    int status;
    final List<String> reasons = new ArrayList<>();
    Double z;
    Double slopePerDay;
    Double driftZ;
    int absLevel;
    boolean remeasure;
    Anchor anchor;
    Double ratio;
    Integer consecutiveLowDays;

    void raiseTo(int level) {
        status = Math.max(status, level);
    }

    void flag(int level, String reason) {
        raiseTo(level);
        reasons.add(reason);
    }
}
