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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolAuditFilter;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolMutationFilter;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolTokenService;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolRegistrarTest {

    private static final McpAuthentication AUTHENTICATION =
            new McpAuthentication("instance-id", "access-key");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void propagatesTransportAuthenticationToToolExecutor() {
        ToolDefinition definition = toolDefinition();
        ToolExecutionService toolExecutor = mock(ToolExecutionService.class);
        Map<String, Object> output = Map.of("instanceId", "cluster-001");
        when(toolExecutor.execute(
                same(definition.name()), any(), same(AUTHENTICATION)))
                .thenReturn(output);
        McpServerFeatures.SyncToolSpecification specification =
                McpToolRegistrar.toolSpecification(definition, toolExecutor, objectMapper);

        McpSchema.CallToolResult result = specification.callHandler().apply(
                exchange(AUTHENTICATION),
                new McpSchema.CallToolRequest(
                        definition.name(), Map.of(
                                "instanceId", "cluster-001",
                                "dry_run", true)));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutor).execute(
                same(definition.name()), captor.capture(), same(AUTHENTICATION));
        assertThat(captor.getValue()).containsEntry("dry_run", true);
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEqualTo(output);
        assertThat(specification.tool().inputSchema()).isEqualTo(definition.inputSchema());
        assertThat(specification.tool().description())
                .isEqualTo("Update a RocketMQ topic\nRequires all capabilities: TOPIC_MANAGEMENT.");
        assertThat(specification.tool().annotations().destructiveHint()).isFalse();
    }

    @Test
    void returnsSuccessfulMcpResultWhenMutationCompletesBeforeAuditPersistenceFailsTest() {
        ToolDefinition definition = toolDefinition();
        CountingMutationHandler handler = new CountingMutationHandler();

        AuditService audit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit database unavailable"))
                .when(audit).record(anyString(), anyString(), anyString(), anyString(), isNull(), eq("SUCCESS"));
        McpSchema.CallToolResult result = call(definition, toolExecutor(definition, handler, audit));

        assertThat(handler.executions).isEqualTo(1);
        assertThat(result.isError()).isFalse();
        MutationOutput<?> output = (MutationOutput<?>) result.structuredContent();
        assertThat(output.status()).isEqualTo(MutationOutput.Status.EXECUTED);
        assertThat(output.result()).isEqualTo(Map.of("topic", "orders"));
    }

    @Test
    void preservesOriginalMcpFailureWhenFailedAuditPersistenceAlsoFailsTest() {
        ToolDefinition definition = toolDefinition();
        CountingMutationHandler handler = new CountingMutationHandler();
        ToolExecutionException originalFailure = ToolError.TOOL_CAPABILITY_UNSUPPORTED.exception(definition.name());
        handler.failure = originalFailure;

        AuditService audit = mock(AuditService.class);
        doThrow(new IllegalStateException("audit database unavailable"))
                .when(audit).record(anyString(), anyString(), anyString(), anyString(), anyString(), eq("FAILED"));
        McpSchema.CallToolResult result = call(definition, toolExecutor(definition, handler, audit));

        assertThat(handler.executions).isEqualTo(1);
        assertThat(result.isError()).isTrue();
        assertThat(result.structuredContent()).isEqualTo(Map.of(
                "code", originalFailure.getErrorCode(),
                "message", originalFailure.getMessage(),
                "hint", originalFailure.getHint()));
    }

    private ToolExecutionService toolExecutor(
            ToolDefinition definition, CountingMutationHandler handler, AuditService audit) {
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.find(definition.name())).thenReturn(Optional.of(definition));
        when(catalog.getDefinition(definition.name())).thenReturn(definition);
        when(catalog.list()).thenReturn(List.of(definition));
        ToolFilterChain filters = new ToolFilterChain(List.of(
                new ToolAuditFilter(audit),
                new ToolMutationFilter(mock(ToolTokenService.class), true)));
        return new ToolExecutionService(catalog, List.of(handler), filters, mock(InstanceResolver.class));
    }

    private McpSchema.CallToolResult call(ToolDefinition definition, ToolExecutionService toolExecutor) {
        return McpToolRegistrar.toolSpecification(definition, toolExecutor, objectMapper)
                .callHandler().apply(exchange(AUTHENTICATION), new McpSchema.CallToolRequest(
                        definition.name(), Map.of(
                                "instanceId", AUTHENTICATION.instanceId(),
                                "topic", "orders",
                                "confirm_token", "confirmed")));
    }

    private static McpSyncServerExchange exchange(McpAuthentication authentication) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        McpTransportContext context = authentication == null
                ? McpTransportContext.EMPTY
                : McpTransportContext.create(Map.of(
                        McpAuthentication.ATTRIBUTE, authentication));
        when(exchange.transportContext()).thenReturn(context);
        return exchange;
    }

    private static ToolDefinition toolDefinition() {
        return new ToolDefinition(
                "rmq.topic.update",
                new ToolDefinition.Cli("topic", "update"),
                "Update a RocketMQ topic",
                ToolRiskLevel.L2,
                "topic:write",
                List.of("TOPIC_MANAGEMENT"),
                Map.of(
                        "type", "object",
                        "properties", Map.of("instanceId", Map.of("type", "string")),
                        "required", List.of("instanceId"),
                        "additionalProperties", false),
                Map.of("type", "object", "additionalProperties", true),
                "json",
                false,
                null);
    }

    private static final class CountingMutationHandler extends MutationToolHandler<Map, Map<String, Object>> {

        private int executions;
        private RuntimeException failure;

        private CountingMutationHandler() {
            super(Map.class);
        }

        @Override
        public String name() {
            return "rmq.topic.update";
        }

        @Override
        public ToolPlan preview(Map input, ToolExecutionContext context) {
            return ToolPlan.builder("Update topic")
                    .after(Map.of("topic", input.get("topic")))
                    .impact("Updates the topic configuration.")
                    .build();
        }

        @Override
        public Map<String, Object> execute(Map input, ToolExecutionContext context) {
            executions++;
            if (failure != null) {
                throw failure;
            }
            return Map.of("topic", input.get("topic"));
        }
    }
}
