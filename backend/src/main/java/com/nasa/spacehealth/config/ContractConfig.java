package com.nasa.spacehealth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nasa.spacehealth.model.TriageProtocol;
import com.nasa.spacehealth.rules.Rules;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ContractConfig {

    @Bean
    public ContractFiles contractFiles(@Value("${contract.dir:../contract}") String dir, ObjectMapper mapper) {
        return new ContractFiles(dir, mapper);
    }

    @Bean
    public Rules rules(ContractFiles files) {
        return files.readStrict("rules.json", Rules.class);
    }

    @Bean
    public TriageProtocol triageProtocol(ContractFiles files) {
        return files.read("triage_protocol.json", TriageProtocol.class);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
