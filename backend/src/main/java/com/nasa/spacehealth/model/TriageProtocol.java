package com.nasa.spacehealth.model;

import java.util.List;

public record TriageProtocol(String protocolId, String version, String scenario, String reviewStatus,
                             String start, List<TriageStep> steps) {
}
