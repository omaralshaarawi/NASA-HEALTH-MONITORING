package com.nasa.spacehealth;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

// Compares two JSON trees field by field and returns one line per difference.
// Numbers must be the exact same double (-0.0 != 0.0); null and missing are different.
public final class JsonDiff {

    private JsonDiff() {
    }

    public static List<String> diff(JsonNode expected, JsonNode actual) {
        List<String> out = new ArrayList<>();
        walk("$", expected, actual, out);
        return out;
    }

    private static void walk(String path, JsonNode e, JsonNode a, List<String> out) {
        if (e.isObject() && a.isObject()) {
            TreeSet<String> keys = new TreeSet<>();
            for (Iterator<String> it = e.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            for (Iterator<String> it = a.fieldNames(); it.hasNext(); ) {
                keys.add(it.next());
            }
            for (String k : keys) {
                if (!e.has(k)) {
                    out.add(path + "." + k + ": unexpected field, value " + a.get(k));
                } else if (!a.has(k)) {
                    out.add(path + "." + k + ": missing, expected " + e.get(k));
                } else {
                    walk(path + "." + k, e.get(k), a.get(k), out);
                }
            }
        } else if (e.isArray() && a.isArray()) {
            if (e.size() != a.size()) {
                out.add(path + ": expected " + e.size() + " elements " + e + " but got " + a.size() + " " + a);
                return;
            }
            for (int i = 0; i < e.size(); i++) {
                walk(path + "[" + i + "]", e.get(i), a.get(i), out);
            }
        } else if (e.isNumber() && a.isNumber()) {
            if (Double.doubleToLongBits(e.doubleValue()) != Double.doubleToLongBits(a.doubleValue())) {
                out.add(path + ": expected " + e + " but got " + a);
            }
        } else if (!e.equals(a)) {
            out.add(path + ": expected " + e + " but got " + a);
        }
    }
}
