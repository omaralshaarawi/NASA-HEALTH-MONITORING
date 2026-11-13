package com.nasa.spacehealth.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads the locked files in /contract. Nothing from that folder is copied into the backend. */
public class ContractFiles {

    private final Path dir;
    private final ObjectMapper mapper;

    public ContractFiles(String configuredDir, ObjectMapper mapper) {
        this.dir = resolve(configuredDir);
        this.mapper = mapper;
    }

    /** Works whether the app is started from /backend (../contract) or from the repo root (contract). */
    static Path resolve(String configuredDir) {
        for (String candidate : List.of(configuredDir, "../contract", "contract")) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("rules.json"))) {
                return p;
            }
        }
        throw new IllegalStateException("Cannot find the contract folder (rules.json). Tried '" + configuredDir
                + "', '../contract' and 'contract' from " + Path.of("").toAbsolutePath()
                + ". Set contract.dir (or CONTRACT_DIR) to the folder that holds rules.json.");
    }

    public Path dir() {
        return dir;
    }

    public <T> T read(String name, Class<T> type) {
        try {
            return mapper.readValue(dir.resolve(name).toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract file " + name, e);
        }
    }

    /** Like read(), but a record component with no matching key in the file is an error instead of 0 / false / null. */
    public <T> T readStrict(String name, Class<T> type) {
        try {
            return mapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .readValue(dir.resolve(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract file " + name + ": " + e.getMessage(), e);
        }
    }

    public <T> T read(String name, TypeReference<T> type) {
        try {
            return mapper.readValue(dir.resolve(name).toFile(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract file " + name, e);
        }
    }

    public JsonNode tree(String name) {
        try {
            return mapper.readTree(dir.resolve(name).toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read contract file " + name, e);
        }
    }
}
