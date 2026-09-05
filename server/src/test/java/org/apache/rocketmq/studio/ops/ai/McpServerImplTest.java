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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerImplTest {

    @Test
    void listToolsWithoutAClusterDelegatesToTheGlobalDiscovery() {
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(gateway.discover(null)).thenReturn(List.of());

        assertThat(new McpServerImpl(gateway, catalog).listTools()).isEmpty();
        verify(gateway).discover(null);
    }

    @Test
    void listToolsForwardsTheSelectedCluster() {
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        ToolCatalog catalog = mock(ToolCatalog.class);
        AiToolVO tool = mock(AiToolVO.class);
        when(gateway.discover("cluster-a")).thenReturn(List.of(tool));

        assertThat(new McpServerImpl(gateway, catalog).listTools("cluster-a"))
                .containsExactly(tool);
        verify(gateway).discover("cluster-a");
    }

    @Test
    void executeForwardsTheToolNameAndInput() {
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        ToolCatalog catalog = mock(ToolCatalog.class);
        Map<String, Object> input = Map.of("cluster", "cluster-a");
        when(gateway.execute("rmq.cluster.list", input)).thenReturn("output");

        assertThat(new McpServerImpl(gateway, catalog).execute("rmq.cluster.list", input))
                .isEqualTo("output");
        verify(gateway).execute("rmq.cluster.list", input);
    }

    @Test
    void catalogMetadataComesFromTheToolCatalog() {
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.getVersion()).thenReturn("1.0.0");
        when(catalog.getDigest()).thenReturn("abc123");
        when(catalog.getMinimumClientVersion()).thenReturn("1.0.0");
        McpServerImpl server = new McpServerImpl(gateway, catalog);

        assertThat(server.catalogVersion()).isEqualTo("1.0.0");
        assertThat(server.catalogDigest()).isEqualTo("abc123");
        assertThat(server.minimumClientVersion()).isEqualTo("1.0.0");
    }
}
