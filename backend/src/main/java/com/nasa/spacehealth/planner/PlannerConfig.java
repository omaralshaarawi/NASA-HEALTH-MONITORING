package com.nasa.spacehealth.planner;

import com.nasa.spacehealth.config.ContractFiles;
import com.nasa.spacehealth.planner.model.ExerciseMenu;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class PlannerConfig {

    @Bean
    public ExerciseMenu exerciseMenu(ContractFiles files) {
        return files.read("exercise_menu.json", ExerciseMenu.class);
    }

    @Bean
    public RestClient geminiRestClient(RestClient.Builder builder,
                                       @Value("${planner.gemini.base-url}") String baseUrl,
                                       @Value("${planner.gemini.timeout-seconds:20}") int timeoutSeconds) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
