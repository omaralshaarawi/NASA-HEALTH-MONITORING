package com.nasa.spacehealth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Spring does not read .env files, so we do: lines like KEY=value or export KEY=value from
// backend/.env become system properties. A real environment variable with the same name wins.
final class DotEnv {

    private static final Pattern LINE = Pattern.compile("^\\s*(?:export\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*?)\\s*$");

    private DotEnv() {
    }

    static void load() {
        for (Path file : List.of(Path.of(".env"), Path.of("backend", ".env"))) {
            if (Files.isRegularFile(file)) {
                try {
                    apply(Files.readAllLines(file), System.getenv());
                } catch (IOException e) {
                    System.err.println("Could not read " + file.toAbsolutePath() + ": " + e.getMessage());
                }
                return;
            }
        }
    }

    static void apply(List<String> lines, Map<String, String> environment) {
        for (String line : lines) {
            if (line.isBlank() || line.strip().startsWith("#")) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String name = m.group(1);
            String value = unquote(m.group(2));
            if (!environment.containsKey(name) && System.getProperty(name) == null) {
                System.setProperty(name, value);
            }
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
