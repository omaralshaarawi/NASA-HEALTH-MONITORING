package com.nasa.spacehealth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.nasa.spacehealth.config.ContractFiles;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Validates JSON against contract/schemas/{name}.schema.json (JSON Schema draft-07, generated from contract.ts). */
@Component
public class SchemaValidator {

    private final ContractFiles files;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    public SchemaValidator(ContractFiles files) {
        this.files = files;
    }

    /** @return human-readable violations, empty when the document is valid */
    public List<String> validate(String schemaName, JsonNode document) {
        JsonSchema schema = cache.computeIfAbsent(schemaName,
                n -> factory.getSchema(files.tree("schemas/" + n + ".schema.json")));
        return schema.validate(document).stream().map(m -> m.getMessage()).sorted().toList();
    }
}
