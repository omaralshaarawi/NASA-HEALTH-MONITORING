package com.nasa.spacehealth.model;

import java.util.List;

public record RegionAssessment(String id, Status status, List<String> drivers, List<String> members) {
}
