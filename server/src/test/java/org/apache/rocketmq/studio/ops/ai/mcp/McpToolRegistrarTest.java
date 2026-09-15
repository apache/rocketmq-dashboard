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
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
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
        Map<String, Object> output = Map.of("cluster", "cluster-001");
        when(toolExecutor.execute(
                same(definition.name()), any(), same(AUTHENTICATION)))
                .thenReturn(output);
        McpServerFeatures.SyncToolSpecification specification =
                McpToolRegistrar.toolSpecification(definition, toolExecutor, objectMapper);

        McpSchema.CallToolResult result = specification.callHandler().apply(
                exchange(AUTHENTICATION),
                new McpSchema.CallToolRequest(
                        definition.name(), Map.of(
                                "cluster", "cluster-001",
                                "dry_run", true)));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(toolExecutor).execute(
                same(definition.name()), captor.capture(), same(AUTHENTICATION));
        assertThat(captor.getValue()).containsEntry("dry_run", true);
        assertThat(result.isError()).isFalse();
        assertThat(result.structuredContent()).isEqualTo(output);
        assertThat(specification.tool().inputSchema()).isEqualTo(definition.inputSchema());
        assertThat(specification.tool().description())
                .isEqualTo("Create a RocketMQ topic\nRequires all capabilities: TOPIC_MANAGEMENT.");
        assertThat(specification.tool().annotations().destructiveHint()).isFalse();
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
                "rmq.topic.create",
                new ToolDefinition.Cli("topic", "create"),
                "Create a RocketMQ topic",
                ToolRiskLevel.L2,
                "topic:write",
                List.of("TOPIC_MANAGEMENT"),
                Map.of(
                        "type", "object",
                        "properties", Map.of("cluster", Map.of("type", "string")),
                        "required", List.of("cluster"),
                        "additionalProperties", false),
                Map.of("type", "object", "additionalProperties", true),
                "json",
                false,
                null);
    }
}
