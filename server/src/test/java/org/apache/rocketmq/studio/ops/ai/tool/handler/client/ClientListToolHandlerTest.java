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
package org.apache.rocketmq.studio.ops.ai.tool.handler.client;

import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.client.ClientService;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientListToolHandlerTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ClientListToolHandler handler;

    @Test
    void executeShouldListConnectionsAndProjectItems() {
        assertThat(handler.name()).isEqualTo("rmq.client.list");
        ClientConnectionVO conn = ClientConnectionVO.builder()
                .clientId("client-1")
                .type(ClientType.Producer)
                .groupOrTopic("TopicA")
                .producerGroup("PG-1")
                .protocol(Protocol.Remoting)
                .address("127.0.0.1:12345")
                .version("5.0.0")
                .connectedAt(LocalDateTime.of(2024, 1, 1, 12, 0))
                .partial(false)
                .clusterName("cluster-1")
                .build();
        when(clientService.listConnections(eq("cluster-1"), isNull(), isNull()))
                .thenReturn(List.of(conn));

        ClusterListOutput<ClientConnectionVO> result = handler.execute(
                new ClientListInput("cluster-1", null), context("cluster-1"));

        assertThat(result.cluster()).isEqualTo("cluster-1");
        assertThat(result.items()).containsExactly(conn);
        assertThat(result.items().getFirst().getClientId()).isEqualTo("client-1");
        assertThat(result.items().getFirst().getType()).isEqualTo(ClientType.Producer);
        assertThat(result.items().getFirst().getAddress()).isEqualTo("127.0.0.1:12345");
        verify(clientService).listConnections(eq("cluster-1"), isNull(), isNull());
    }

    private static ToolExecutionContext context(String instanceId) {
        return ToolExecutionContext.of(
                instanceId,
                null,
                Map.of("cluster", "cluster-1"));
    }
}
