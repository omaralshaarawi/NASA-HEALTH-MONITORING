package com.nasa.spacehealth.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** contract.ts TriageStep. `script` is spoken and shown verbatim; `next` is the only way forward. */
public record TriageStep(
        String id,
        String type,
        String script,
        String checklistLabel,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> next,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean terminal) {

    public boolean endsProtocol() {
        return Boolean.TRUE.equals(terminal);
    }
}
