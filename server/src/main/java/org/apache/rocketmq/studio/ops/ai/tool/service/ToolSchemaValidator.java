/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolSchemaValidator {

    private final ObjectMapper objectMapper;
    private final JsonMapper jsonMapper;
    private final Map<String, Schema> inputSchemas;
    private final Map<String, Schema> outputSchemas;

    public ToolSchemaValidator(
            ToolCatalog catalog,
            ObjectMapper objectMapper,
            JsonMapper jsonMapper) {
        this.objectMapper = objectMapper;
        this.jsonMapper = jsonMapper;
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12);
        SchemaRegistry metaSchemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012());
        Schema metaSchema = metaSchemaRegistry.getSchema(
                SchemaLocation.of(Dialects.getDraft202012().getId()));
        this.inputSchemas = compileSchemas(catalog, registry, metaSchema, true);
        this.outputSchemas = compileSchemas(catalog, registry, metaSchema, false);
    }

    public void validateInput(ToolDefinition definition, Map<String, Object> input) {
        List<Error> errors = inputSchemas.get(definition.name()).validate(jsonMapper.valueToTree(input));
        errors = sortedErrors(errors);
        if (!errors.isEmpty()) {
            throw ToolError.INPUT_VALIDATION_FAILED.exception(definition.name(), errors);
        }
    }

    public void validateOutput(ToolDefinition definition, Object output) {
        // Tool DTOs are serialized by the application's configured Jackson 2
        // converter. Normalize through that mapper first so schema validation
        // observes the same @JsonProperty names as the HTTP/MCP response.
        Object serializedContract = objectMapper.convertValue(output, Object.class);
        JsonNode outputNode = jsonMapper.valueToTree(serializedContract);
        List<Error> errors = outputSchemas.get(definition.name()).validate(outputNode);
        errors = sortedErrors(errors);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Tool output validation failed for " + definition.name() + ": " + errors);
        }
    }

    private Map<String, Schema> compileSchemas(
            ToolCatalog catalog,
            SchemaRegistry registry,
            Schema metaSchema,
            boolean input) {
        String schemaKind = input ? "input" : "output";
        Map<String, Schema> compiled = new LinkedHashMap<>();
        for (ToolDefinition definition : catalog.list()) {
            JsonNode schemaNode = jsonMapper.valueToTree(
                    input ? definition.inputSchema() : definition.outputSchema());
            List<Error> metaSchemaErrors = sortedErrors(metaSchema.validate(schemaNode));
            if (!metaSchemaErrors.isEmpty()) {
                throw new IllegalStateException(
                        "Tool " + schemaKind + " schema is invalid for "
                                + definition.name() + ": " + metaSchemaErrors);
            }

            try {
                Schema schema = registry.getSchema(schemaNode);
                schema.initializeValidators();
                compiled.put(definition.name(), schema);
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "Tool " + schemaKind + " schema is invalid for "
                                + definition.name(), ex);
            }
        }
        return Collections.unmodifiableMap(compiled);
    }

    private List<Error> sortedErrors(List<Error> errors) {
        List<Error> sorted = new ArrayList<>(errors);
        sorted.sort(Comparator.comparing(error -> error.getInstanceLocation().toString()));
        return sorted;
    }
}
