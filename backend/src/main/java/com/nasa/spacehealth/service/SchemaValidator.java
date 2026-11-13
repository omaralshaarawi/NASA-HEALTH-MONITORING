package com.nasa.spacehealth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nasa.spacehealth.config.ContractFiles;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Checks JSON against contract/schemas/<name>.schema.json. Returns the list of problems (empty = valid).
@Component
public class SchemaValidator {

    private final ContractFiles files;
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    public SchemaValidator(ContractFiles files) {
        this.files = files;
    }

    public List<String> validate(String schemaName, JsonNode json) {
        JsonSchema schema = cache.computeIfAbsent(schemaName,
                name -> factory.getSchema(files.tree("schemas/" + name + ".schema.json")));
        return schema.validate(json).stream().map(ValidationMessage::getMessage).sorted().toList();
    }
}
