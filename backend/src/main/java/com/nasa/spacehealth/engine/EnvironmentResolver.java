package com.nasa.spacehealth.engine;

import com.nasa.spacehealth.model.CommMode;
import com.nasa.spacehealth.model.EnvironmentProfile;
import com.nasa.spacehealth.rules.Rules;
import com.nasa.spacehealth.rules.Rules.EnvironmentPreset;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EnvironmentResolver {

    private final Rules rules;

    public EnvironmentResolver(Rules rules) {
        this.rules = rules;
    }

    public EnvironmentPreset preset(String envId) {
        EnvironmentPreset env = rules.environments().get(envId);
        if (env == null) {
            throw new IllegalArgumentException("Unknown environment '" + envId + "'. Valid: "
                    + rules.environments().keySet());
        }
        return env;
    }

    // LEO and the Moon have a fixed distance. For Mars use the requested value (clamped) or the default.
    public long distanceKm(EnvironmentPreset env, Long requestedKm) {
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

    public double oneWayDelaySeconds(long distanceKm) {
        return distanceKm / rules.constants().cKmS();
    }

    public boolean isAutonomous(double delaySeconds) {
        return delaySeconds > rules.constants().autonomousDelayS();
    }

    public EnvironmentProfile profile(String envId, Long requestedKm, Double radiationMsvDay, Double cabinCo2MmHg) {
        EnvironmentPreset env = preset(envId);
        long distance = distanceKm(env, requestedKm);
        double delay = oneWayDelaySeconds(distance);
        Map<String, Long> d = env.distanceKm();
        EnvironmentProfile.DistanceRange range = d.get("min") != null && d.get("max") != null
                ? new EnvironmentProfile.DistanceRange(d.get("min"), d.get("max"))
                : null;

        return new EnvironmentProfile(envId, env.label(), env.environment(), env.body(), env.gravityG(), distance,
                PyFormat.round(delay, 3), env.commLabel(), isAutonomous(delay) ? CommMode.AUTONOMOUS : CommMode.LIVE,
                env.radiationExpectedMsvDay(), radiationMsvDay, cabinCo2MmHg, env.resistiveTargetMin(), range);
    }
}
