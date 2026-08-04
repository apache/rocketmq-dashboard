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
package org.apache.rocketmq.studio.ops.ai.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.rocketmq.studio.ops.ai.AiToolCallDTO;
import org.apache.rocketmq.studio.ops.ai.AiToolExecutionResultVO;
import org.apache.rocketmq.studio.ops.ai.tool.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.ToolInvocationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class McpToolRegistrar {

    private static final String SOURCE = "MCP";

    private McpToolRegistrar() {
    }

    public static List<McpServerFeatures.SyncToolSpecification> toToolSpecifications(
            ToolCatalog toolCatalog,
            ToolInvocationService toolInvocationService,
            ObjectMapper objectMapper) {
        return toolCatalog.list().stream()
                .map(definition -> toolSpecification(definition, toolInvocationService, objectMapper))
                .toList();
    }

    public static McpServerFeatures.SyncToolSpecification toolSpecification(
            ToolDefinition definition,
            ToolInvocationService toolInvocationService,
            ObjectMapper objectMapper) {
        BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
                (exchange, request) -> callTool(definition, request, toolInvocationService, objectMapper);
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool(definition))
                .callHandler(handler)
                .build();
    }

    public static McpSchema.CallToolResult callTool(
            ToolDefinition definition,
            McpSchema.CallToolRequest request,
            ToolInvocationService toolInvocationService,
            ObjectMapper objectMapper) {
        Map<String, Object> arguments = request.arguments() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.arguments());
        boolean apply = Boolean.TRUE.equals(arguments.remove("apply"))
                || Boolean.TRUE.equals(arguments.get("confirmed"));
        AiToolExecutionResultVO result = toolInvocationService.callTool(AiToolCallDTO.builder()
                .name(definition.name())
                .arguments(arguments)
                .dryRun(Boolean.TRUE.equals(arguments.get("dryRun")))
                .apply(apply)
                .source(SOURCE)
                .build());
        String text = serializeResult(result, objectMapper);
        boolean error = result.getErrorCode() != null;
        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(text)))
                .isError(error)
                .meta(meta(result));
        if (!error) {
            builder.structuredContent(result.getResult());
        }
        return builder.build();
    }

    public static McpSchema.Tool tool(ToolDefinition definition) {
        return McpSchema.Tool.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(jsonSchema(definition.inputSchema()))
                .outputSchema(definition.outputSchema())
                .annotations(toolAnnotations(definition))
                .build();
    }

    public static McpSchema.JsonSchema jsonSchema(Map<String, Object> schema) {
        SchemaFields fields = new SchemaFields(schema);
        return new McpSchema.JsonSchema(
                fields.type(),
                fields.properties(),
                fields.required(),
                fields.additionalProperties(),
                fields.defs(),
                fields.definitions());
    }

    public static McpSchema.ToolAnnotations toolAnnotations(ToolDefinition definition) {
        boolean readOnly = definition.riskLevel().readOnly();
        return new McpSchema.ToolAnnotations(
                definition.name(),
                readOnly,
                !readOnly,
                false,
                true,
                null);
    }

    private static String serializeResult(
            AiToolExecutionResultVO result,
            ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize MCP tool result", e);
        }
    }

    private static Map<String, Object> meta(AiToolExecutionResultVO result) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", result.getRequestId());
        meta.put("toolName", result.getToolName());
        meta.put("operationLevel", result.getOperationLevel());
        meta.put("policy", result.getPolicy() == null ? null : result.getPolicy().name());
        meta.put("errorCode", result.getErrorCode());
        meta.entrySet().removeIf(entry -> entry.getValue() == null);
        return meta;
    }

    private record SchemaFields(Map<String, Object> schema) {

        private String type() {
            Object value = schema.get("type");
            return value instanceof String type ? type : "object";
        }

        private Map<String, Object> properties() {
            return objectMap("properties");
        }

        private List<String> required() {
            return stringList("required");
        }

        private Boolean additionalProperties() {
            Object value = schema.get("additionalProperties");
            return value instanceof Boolean additionalProperties ? additionalProperties : null;
        }

        private Map<String, Object> defs() {
            return objectMap("$defs");
        }

        private Map<String, Object> definitions() {
            return objectMap("definitions");
        }

        private List<String> stringList(String key) {
            Object value = schema.get(key);
            if (!(value instanceof List<?> list)) {
                return null;
            }
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }

        private Map<String, Object> objectMap(String key) {
            Object value = schema.get(key);
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((entryKey, entryValue) -> {
                if (entryKey instanceof String stringKey) {
                    copy.put(stringKey, entryValue);
                }
            });
            return copy;
        }
    }
}
