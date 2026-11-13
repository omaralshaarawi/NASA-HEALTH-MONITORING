package com.nasa.spacehealth.engine;

import com.fasterxml.jackson.databind.JsonNode;

// Reads values out of a daily record by the dotted paths used in rules.json ("vitals.hr_rest_bpm").
final class RecordFields {

    private RecordFields() {
    }

    static JsonNode get(JsonNode record, String path) {
        if (path == null) {
            return null;
        }
        JsonNode current = record;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current == null || current.isNull() ? null : current;
    }

    static Double number(JsonNode record, String path) {
        JsonNode n = get(record, path);
        return n != null && n.isNumber() ? n.doubleValue() : null;
    }

    static double numberOrZero(JsonNode record, String path) {
        Double v = number(record, path);
        return v == null ? 0.0 : v;
    }

    static boolean isTrue(JsonNode record, String path) {
        JsonNode n = get(record, path);
        if (n == null) {
            return false;
        }
        return n.isBoolean() ? n.booleanValue() : n.isNumber() && n.doubleValue() != 0.0;
    }
}
