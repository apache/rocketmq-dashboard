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
package org.apache.rocketmq.studio.ops.ai;

import org.apache.rocketmq.studio.ops.ai.tool.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.ToolGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link McpServerImpl}, the {@link McpServerRegistry} implementation that
 * maps MCP registry calls onto the tool gateway and tool catalog: tool listing/execution
 * and the catalog metadata MCP clients rely on for capability negotiation.
 */
@ExtendWith(MockitoExtension.class)
class McpServerImplTest {

    @Mock
    private ToolGatewayService toolGatewayService;

    @Mock
    private ToolCatalog toolCatalog;

    @InjectMocks
    private McpServerImpl registry;

    @Test
    void listsToolsAcrossTheWholeDashboardWithoutAClusterScope() {
        List<AiToolVO> tools = List.of();
        when(toolGatewayService.discover(null)).thenReturn(tools);

        assertThat(registry.listTools()).isSameAs(tools);
        verify(toolGatewayService).discover(null);
    }

    @Test
    void listsToolsForASpecificCluster() {
        List<AiToolVO> tools = List.of();
        when(toolGatewayService.discover("c1")).thenReturn(tools);

        assertThat(registry.listTools("c1")).isSameAs(tools);
    }

    @Test
    void delegatesToolExecutionWithItsInput() {
        Map<String, Object> input = Map.of("cluster", "c1");
        when(toolGatewayService.execute("rmq.topic.list", input)).thenReturn("done");

        assertThat(registry.execute("rmq.topic.list", input)).isEqualTo("done");
        verify(toolGatewayService).execute("rmq.topic.list", input);
    }

    @Test
    void exposesTheCatalogMetadataForCapabilityNegotiation() {
        when(toolCatalog.getVersion()).thenReturn("1.0.0");
        when(toolCatalog.getDigest()).thenReturn("abc123");
        when(toolCatalog.getMinimumClientVersion()).thenReturn("1.0.0");

        assertThat(registry.catalogVersion()).isEqualTo("1.0.0");
        assertThat(registry.catalogDigest()).isEqualTo("abc123");
        assertThat(registry.minimumClientVersion()).isEqualTo("1.0.0");
    }
}
