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
package org.apache.rocketmq.studio.ops.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.dialect.Dialects;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.ops.ai.AiToolVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ToolGatewayService {

    private final ToolCatalog catalog;
    private final CapabilityResolver capabilityResolver;
    private final ClusterService clusterService;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolHandler> handlers;
    private final Map<String, Schema> inputSchemas;
    private final Map<String, Schema> outputSchemas;

    public ToolGatewayService(
            ToolCatalog catalog,
            CapabilityResolver capabilityResolver,
            ClusterService clusterService,
            ObjectMapper objectMapper,
            List<ToolHandler> handlers) {
        this.catalog = catalog;
        this.capabilityResolver = capabilityResolver;
        this.clusterService = clusterService;
        this.objectMapper = objectMapper;
        this.handlers = registerHandlers(catalog, handlers);

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12);
        SchemaRegistry metaSchemaRegistry = SchemaRegistry.withDialect(
                Dialects.getDraft202012());
        Schema metaSchema = metaSchemaRegistry.getSchema(
                SchemaLocation.of(Dialects.getDraft202012().getId()));
        this.inputSchemas = compileSchemas(catalog, registry, metaSchema, true);
        this.outputSchemas = compileSchemas(catalog, registry, metaSchema, false);
    }

    public List<AiToolVO> discover(String clusterId) {
        boolean clusterSelected = clusterId != null && !clusterId.isBlank();
        Set<String> capabilities = clusterSelected
                ? Set.copyOf(capabilityResolver.resolve(clusterId))
                : Collections.emptySet();

        return catalog.list().stream()
                .filter(definition -> clusterSelected || !requiresCluster(definition))
                .filter(definition -> capabilities.containsAll(
                        definition.requiredCapabilities()))
                .map(definition -> toView(
                        definition,
                        handlers.containsKey(definition.name()),
                        catalog.getVersion()))
                .toList();
    }

    public Object execute(String name, Map<String, Object> input) {
        ToolDefinition definition = catalog.find(name)
                .orElseThrow(() -> new ToolExecutionException(
                        404,
                        ToolErrorCodes.TOOL_NOT_FOUND,
                        "Tool not found: " + name));
        Map<String, Object> normalizedInput = input == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(input);

        validateInput(definition, normalizedInput);
        normalizeClusterInput(normalizedInput);
        enforceRisk(definition, normalizedInput);
        enforceCapabilities(definition, normalizedInput);

        ToolHandler handler = handlers.get(name);
        if (handler == null) {
            if (definition.riskLevel().requiresConfirmation()
                    && Boolean.TRUE.equals(normalizedInput.get("dryRun"))) {
                Object output = dryRunPlaceholder(definition, normalizedInput);
                validateOutput(definition, output);
                return output;
            }
            throw new ToolExecutionException(
                    501,
                    ToolErrorCodes.TOOL_HANDLER_NOT_IMPLEMENTED,
                    "Tool handler is not implemented: " + name);
        }
        Object output = handler.execute(normalizedInput);
        validateOutput(definition, output);
        return output;
    }

    private void normalizeClusterInput(Map<String, Object> input) {
        Object cluster = input.get("cluster");
        if (!(cluster instanceof String clusterRef) || clusterRef.isBlank()) {
            return;
        }
        ClusterVO resolved = clusterService.getCluster(clusterRef);
        input.put("cluster", resolved.getId());
    }

    private void validateInput(ToolDefinition definition, Map<String, Object> input) {
        List<Error> errors = sortedErrors(
                inputSchemas.get(definition.name()).validate(objectMapper.valueToTree(input)));
        if (!errors.isEmpty()) {
            throw new ToolExecutionException(
                    400,
                    ToolErrorCodes.TOOL_INPUT_INVALID,
                    "Tool input validation failed for " + definition.name() + ": " + errors);
        }
    }

    private void enforceCapabilities(
            ToolDefinition definition,
            Map<String, Object> input) {
        if (definition.requiredCapabilities().isEmpty()) {
            return;
        }
        Object cluster = input.get("cluster");
        if (!(cluster instanceof String clusterId) || clusterId.isBlank()) {
            throw new ToolExecutionException(
                    400,
                    ToolErrorCodes.TOOL_CLUSTER_REQUIRED,
                    "Tool requires a cluster for capability checks: " + definition.name());
        }
        Set<String> capabilities = Set.copyOf(capabilityResolver.resolve(clusterId));
        if (!capabilities.containsAll(definition.requiredCapabilities())) {
            throw new ToolExecutionException(
                    400,
                    ToolErrorCodes.TOOL_CAPABILITY_UNSUPPORTED,
                    "Cluster does not support tool: " + definition.name());
        }
    }

    private void enforceRisk(ToolDefinition definition, Map<String, Object> input) {
        if (definition.riskLevel().readOnly()) {
            return;
        }
        if (Boolean.TRUE.equals(input.get("dryRun"))) {
            return;
        }
        if (!Boolean.TRUE.equals(input.get("confirmed"))) {
            throw new ToolExecutionException(
                    400,
                    ToolErrorCodes.TOOL_CONFIRMATION_REQUIRED,
                    "Tool requires dryRun=true or confirmed=true before execution: "
                            + definition.name());
        }
        if (definition.riskLevel().requiresReason()) {
            Object reason = input.get("reason");
            if (!(reason instanceof String text) || text.isBlank()) {
                throw new ToolExecutionException(
                        400,
                        ToolErrorCodes.TOOL_REASON_REQUIRED,
                        "Tool requires a non-empty reason before L3 execution: "
                                + definition.name());
            }
        }
    }

    private static Map<String, Object> dryRunPlaceholder(
            ToolDefinition definition,
            Map<String, Object> input) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tool", definition.name());
        output.put("dryRun", true);
        output.put("executed", false);
        output.put("status", "PENDING_HANDLER");
        output.put("riskLevel", definition.riskLevel().code());
        output.put("message", "Tool contract is declared but the handler is not implemented yet.");
        output.put("input", input);
        return output;
    }

    private void validateOutput(ToolDefinition definition, Object output) {
        JsonNode outputNode = objectMapper.valueToTree(output);
        List<Error> errors = sortedErrors(
                outputSchemas.get(definition.name()).validate(outputNode));
        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Tool output validation failed for " + definition.name() + ": " + errors);
        }
    }

    private static Map<String, ToolHandler> registerHandlers(
            ToolCatalog catalog,
            List<ToolHandler> handlers) {
        Map<String, ToolHandler> registered = new LinkedHashMap<>();
        for (ToolHandler handler : handlers) {
            if (registered.putIfAbsent(handler.name(), handler) != null) {
                throw new IllegalStateException(
                        "Tool gateway contains duplicate handler: " + handler.name());
            }
            if (catalog.find(handler.name()).isEmpty()) {
                throw new IllegalStateException(
                        "Tool handler is absent from catalog: " + handler.name());
            }
        }

        return Collections.unmodifiableMap(registered);
    }

    private Map<String, Schema> compileSchemas(
            ToolCatalog catalog,
            SchemaRegistry registry,
            Schema metaSchema,
            boolean input) {
        String schemaKind = input ? "input" : "output";
        Map<String, Schema> compiled = new LinkedHashMap<>();
        for (ToolDefinition definition : catalog.list()) {
            JsonNode schemaNode = objectMapper.valueToTree(
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

    private static boolean requiresCluster(ToolDefinition definition) {
        Object required = definition.inputSchema().get("required");
        return required instanceof List<?> fields && fields.contains("cluster");
    }

    private static AiToolVO toView(
            ToolDefinition definition,
            boolean implemented,
            String catalogVersion) {
        return AiToolVO.builder()
                .name(definition.name())
                .version(catalogVersion)
                .cli(definition.cli())
                .description(definition.description())
                .parameters(definition.inputSchema())
                .riskLevel(definition.riskLevel().code())
                .operationLevel(definition.riskLevel().operationLevel())
                .permission(definition.permission())
                .requiredCapabilities(definition.requiredCapabilities())
                .outputSchema(definition.outputSchema())
                .viewHint(definition.viewHint())
                .deprecated(definition.deprecated())
                .replacement(definition.replacement())
                .implemented(implemented)
                .build();
    }

    private static List<Error> sortedErrors(List<Error> errors) {
        List<Error> sorted = new ArrayList<>(errors);
        sorted.sort(Comparator.comparing(error -> error.getInstanceLocation().toString()));
        return sorted;
    }
}
