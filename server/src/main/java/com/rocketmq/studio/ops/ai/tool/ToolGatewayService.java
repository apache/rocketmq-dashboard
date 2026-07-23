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
package com.rocketmq.studio.ops.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.rocketmq.studio.common.exception.BusinessException;
import com.rocketmq.studio.ops.ai.AiToolVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ToolGatewayService {

    private static final String L1 = "L1";

    private final ToolCatalog catalog;
    private final CapabilityResolver capabilityResolver;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolHandler> handlers;
    private final Map<String, Schema> inputSchemas;
    private final Map<String, Schema> outputSchemas;

    public ToolGatewayService(
            ToolCatalog catalog,
            CapabilityResolver capabilityResolver,
            ObjectMapper objectMapper,
            List<ToolHandler> handlers) {
        this.catalog = catalog;
        this.capabilityResolver = capabilityResolver;
        this.objectMapper = objectMapper;
        this.handlers = registerHandlers(catalog, handlers);

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12);
        this.inputSchemas = compileSchemas(catalog, registry, true);
        this.outputSchemas = compileSchemas(catalog, registry, false);
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
                .map(ToolGatewayService::toView)
                .toList();
    }

    public Object execute(String name, Map<String, Object> input) {
        ToolDefinition definition = catalog.find(name)
                .orElseThrow(() -> new BusinessException(404, "Tool not found: " + name));
        ToolHandler handler = handlers.get(name);
        Map<String, Object> normalizedInput = input == null
                ? Collections.emptyMap()
                : input;

        validateInput(definition, normalizedInput);
        if (!L1.equals(definition.riskLevel())) {
            throw new BusinessException(
                    400, "Execution rejected; only L1 tools are enabled: " + name);
        }
        enforceCapabilities(definition, normalizedInput);

        Object output = handler.execute(normalizedInput);
        validateOutput(definition, output);
        return output;
    }

    private void validateInput(ToolDefinition definition, Map<String, Object> input) {
        List<Error> errors = sortedErrors(
                inputSchemas.get(definition.name()).validate(objectMapper.valueToTree(input)));
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    400,
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
            throw new BusinessException(
                    400, "Tool requires a cluster for capability checks: " + definition.name());
        }
        Set<String> capabilities = Set.copyOf(capabilityResolver.resolve(clusterId));
        if (!capabilities.containsAll(definition.requiredCapabilities())) {
            throw new BusinessException(
                    400, "Cluster does not support tool: " + definition.name());
        }
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

        List<String> missing = catalog.list().stream()
                .map(ToolDefinition::name)
                .filter(name -> !registered.containsKey(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Tool catalog contains missing handler: " + missing);
        }
        return Collections.unmodifiableMap(registered);
    }

    private Map<String, Schema> compileSchemas(
            ToolCatalog catalog,
            SchemaRegistry registry,
            boolean input) {
        return catalog.list().stream().collect(Collectors.toUnmodifiableMap(
                ToolDefinition::name,
                definition -> registry.getSchema(objectMapper.valueToTree(
                        input ? definition.inputSchema() : definition.outputSchema()))));
    }

    private static boolean requiresCluster(ToolDefinition definition) {
        Object required = definition.inputSchema().get("required");
        return required instanceof List<?> fields && fields.contains("cluster");
    }

    private static AiToolVO toView(ToolDefinition definition) {
        return AiToolVO.builder()
                .name(definition.name())
                .description(definition.description())
                .parameters(definition.inputSchema())
                .build();
    }

    private static List<Error> sortedErrors(List<Error> errors) {
        List<Error> sorted = new ArrayList<>(errors);
        sorted.sort(Comparator.comparing(error -> error.getInstanceLocation().toString()));
        return sorted;
    }
}
