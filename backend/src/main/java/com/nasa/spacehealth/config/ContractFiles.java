package com.nasa.spacehealth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Reads the shared files in /contract (rules, seed data, protocol, schemas).
public class ContractFiles {

    private final Path dir;
    private final ObjectMapper mapper;

    public ContractFiles(String configuredDir, ObjectMapper mapper) {
        this.dir = findContractDir(configuredDir);
        this.mapper = mapper;
    }

    // Lets the app start from either /backend or the repo root.
    static Path findContractDir(String configuredDir) {
        for (String candidate : List.of(configuredDir, "../contract", "contract")) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("rules.json"))) {
                return p;
            }
        }
        throw new IllegalStateException("Could not find the contract folder (looked for rules.json in '"
                + configuredDir + "', '../contract' and 'contract'). Set contract.dir or CONTRACT_DIR.");
    }

    public Path dir() {
        return dir;
    }

    public <T> T read(String name, Class<T> type) {
        return parse(name, mapper.readerFor(type));
    }

    public <T> T read(String name, TypeReference<T> type) {
        return parse(name, mapper.readerFor(type));
    }

    // Fails if a record field has no matching key, instead of quietly defaulting to 0/false/null.
    public <T> T readStrict(String name, Class<T> type) {
        return parse(name, mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES));
    }

    public JsonNode tree(String name) {
        return parse(name, mapper.readerFor(JsonNode.class));
    }

    private <T> T parse(String name, ObjectReader reader) {
        File file = dir.resolve(name).toFile();
        try {
            return reader.readValue(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + name + ": " + e.getMessage(), e);
        }
    }
}
