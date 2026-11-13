package com.nasa.spacehealth.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Every JSON key follows contract.ts, which is snake_case.
 *
 * Keys that are NOT plain snake_case (sbp_mmHg, radiation_mSv_day, cabin_co2_mmHg, mood_1_10 ...) carry an explicit
 * {@code @JsonProperty} on the record component, because SNAKE_CASE alone would turn them into sbp_mm_hg etc.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer snakeCaseCustomizer() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
}
