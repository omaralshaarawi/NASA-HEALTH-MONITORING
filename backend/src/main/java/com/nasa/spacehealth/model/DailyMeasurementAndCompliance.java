package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// A null vital means it was not measured that day (not zero).
public record DailyMeasurementAndCompliance(
        String astronautId,
        Integer missionDay,
        @JsonInclude(JsonInclude.Include.NON_NULL) String dateIso,
        String environmentId,
        String source,
        Vitals vitals,
        Pvt pvt,
        Habitat habitat,
        Compliance compliance,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Attachment> attachments) {

    public record Vitals(
            @JsonProperty("hr_rest_bpm") Double hrRestBpm,
            @JsonProperty("sbp_mmHg") Double sbpMmHg,
            @JsonProperty("dbp_mmHg") Double dbpMmHg,
            @JsonProperty("glucose_mg_dl") Double glucoseMgDl,
            @JsonProperty("temp_c") Double tempC,
            @JsonProperty("sleep_h") Double sleepH,
            @JsonProperty("mood_1_10") Double mood1To10) {
    }

    public record Pvt(
            @JsonProperty("median_rt_ms") Double medianRtMs,
            @JsonProperty("lapses_gt500ms") Integer lapsesGt500ms,
            @JsonProperty("n_trials") Integer nTrials,
            String source) {
    }

    public record Habitat(
            @JsonProperty("radiation_mSv_day") Double radiationMsvDay,
            @JsonProperty("cabin_co2_mmHg") Double cabinCo2MmHg) {
    }

    public record Compliance(
            Boolean morningVitalsLogged,
            Double aredResistiveMinutes,
            Double rationIntakePct) {
    }

    public record Attachment(
            String kind,
            String ref,
            String capturedAtIso,
            @JsonInclude(JsonInclude.Include.NON_NULL) String note,
            Boolean simulated) {
    }

    public DailyMeasurementAndCompliance withHabitat(Habitat newHabitat) {
        return new DailyMeasurementAndCompliance(astronautId, missionDay, dateIso, environmentId, source,
                vitals, pvt, newHabitat, compliance, attachments);
    }
}
