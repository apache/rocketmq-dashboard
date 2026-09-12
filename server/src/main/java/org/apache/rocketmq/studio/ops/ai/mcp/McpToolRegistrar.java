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
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class McpToolRegistrar {

    private McpToolRegistrar() {
    }

    public static List<McpServerFeatures.SyncToolSpecification> toToolSpecifications(
            ToolCatalog toolCatalog,
            ToolExecutionService toolExecutor,
            ObjectMapper objectMapper) {
        return toolCatalog.list().stream()
                .map(definition -> toolSpecification(definition, toolExecutor, objectMapper))
                .toList();
    }

    public static McpServerFeatures.SyncToolSpecification toolSpecification(
            ToolDefinition definition,
            ToolExecutionService toolExecutor,
            ObjectMapper objectMapper) {
        BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler =
                (exchange, request) -> callTool(
                        definition, exchange, request, toolExecutor, objectMapper);
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool(definition))
                .callHandler(handler)
                .build();
    }

    public static McpSchema.CallToolResult callTool(
            ToolDefinition definition,
            McpSyncServerExchange exchange,
            McpSchema.CallToolRequest request,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper) {
        McpAuthentication authentication = authentication(exchange);
        if (authentication == null) {
            return errorResult(ToolError.MCP_AUTHENTICATION_CONTEXT_MISSING.exception(), objectMapper);
        }
        try {
            Object result = toolExecutionService.execute(
                    definition.name(), request.arguments(), authentication);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(McpSchema.TextContent.builder(serialize(result, objectMapper)).build()))
                    .structuredContent(result)
                    .isError(false)
                    .build();
        } catch (ToolExecutionException exception) {
            return errorResult(exception, objectMapper);
        }
    }

    public static McpSchema.Tool tool(ToolDefinition definition) {
        String description = definition.description();
        if (!definition.requiredCapabilities().isEmpty()) {
            description += "\nRequires all capabilities: "
                    + String.join(", ", definition.requiredCapabilities()) + ".";
        }
        return McpSchema.Tool.builder(definition.name(), definition.inputSchema())
                .description(description)
                .outputSchema(definition.outputSchema())
                .annotations(toolAnnotations(definition))
                .build();
    }

    public static McpSchema.ToolAnnotations toolAnnotations(ToolDefinition definition) {
        boolean readOnly = definition.isReadOnly();
        return McpSchema.ToolAnnotations.builder()
                .title(definition.name())
                .readOnlyHint(readOnly)
                .destructiveHint(definition.riskLevel() == ToolRiskLevel.L3)
                .idempotentHint(false)
                .openWorldHint(true)
                .build();
    }

    private static McpAuthentication authentication(McpSyncServerExchange exchange) {
        McpTransportContext context = exchange == null ? null : exchange.transportContext();
        Object authentication = context == null
                ? null
                : context.get(McpAuthentication.ATTRIBUTE);
        return authentication instanceof McpAuthentication mcpAuthentication ? mcpAuthentication : null;
    }

    private static McpSchema.CallToolResult errorResult(
            ToolExecutionException exception,
            ObjectMapper objectMapper) {
        Map<String, Object> error = Map.of(
                "code", exception.getErrorCode(),
                "message", exception.getMessage(),
                "hint", exception.getHint());
        return McpSchema.CallToolResult.builder()
                .content(List.of(McpSchema.TextContent.builder(serialize(error, objectMapper)).build()))
                .structuredContent(error)
                .isError(true)
                .build();
    }

    private static String serialize(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize MCP tool result", e);
        }
    }

}
