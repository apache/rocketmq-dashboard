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
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientListToolHandlerTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ClientListToolHandler handler;

    @Test
    void exposesTheCatalogedToolNameTest() {
        assertThat(handler.name()).isEqualTo("rmq.client.list");
        assertThat(handler.inputType()).isEqualTo(ClientListInput.class);
    }

    @Test
    void passesInstanceAndFiltersThroughToTheClientServiceTest() {
        when(clientService.listConnections("instance-a", "rmq-a", "Consumer")).thenReturn(List.of());

        handler.execute(new ClientListInput("rmq-a", "Consumer", null), context());

        verify(clientService).listConnections("instance-a", "rmq-a", "Consumer");
    }

    @Test
    void mapsConnectionsAndKeepsUnreportableMetadataAbsentTest() {
        ClientConnectionVO full = ClientConnectionVO.builder()
                .clientId("10.0.0.1@1234")
                .type(ClientType.Consumer)
                .groupOrTopic("group-a")
                .protocol(Protocol.Remoting)
                .address("10.0.0.1:5678")
                .language(ClientLanguage.Java)
                .version("442")
                .connectedAt(LocalDateTime.of(2026, 9, 21, 10, 0))
                .partial(false)
                .clusterName("rmq-a")
                .build();
        ClientConnectionVO sparse = ClientConnectionVO.builder()
                .clientId("10.0.0.2@5678")
                .type(ClientType.Producer)
                .groupOrTopic("orders")
                .producerGroup("pg-orders")
                .address("10.0.0.2:9999")
                .partial(true)
                .build();
        when(clientService.listConnections("instance-a", null, null)).thenReturn(List.of(full, sparse));

        ListOutput<ClientItem> result = handler.execute(new ClientListInput(null, null, null), context());

        assertThat(result.items()).containsExactly(
                new ClientItem("10.0.0.1@1234", "Consumer", "group-a", null, "Remoting",
                        "10.0.0.1:5678", "Java", "442", "2026-09-21T10:00", false, "rmq-a"),
                new ClientItem("10.0.0.2@5678", "Producer", "orders", "pg-orders", null,
                        "10.0.0.2:9999", null, null, null, true, null));
    }

    @Test
    void searchFiltersCaseInsensitivelyAcrossIdentityFieldsTest() {
        ClientConnectionVO byGroup = connection("client-1", "GROUP-A", null, "10.0.0.1:1");
        ClientConnectionVO byProducerGroup = connection("client-2", "orders", "pg-Orders", "10.0.0.2:2");
        ClientConnectionVO byAddress = connection("client-3", "topic-c", null, "10.9.9.9:1234");
        when(clientService.listConnections("instance-a", null, null))
                .thenReturn(List.of(byGroup, byProducerGroup, byAddress));

        assertThat(handler.execute(new ClientListInput(null, null, "group-a"), context()).items())
                .extracting(ClientItem::clientId).containsExactly("client-1");
        assertThat(handler.execute(new ClientListInput(null, null, "PG-"), context()).items())
                .extracting(ClientItem::clientId).containsExactly("client-2");
        assertThat(handler.execute(new ClientListInput(null, null, "10.9.9.9"), context()).items())
                .extracting(ClientItem::clientId).containsExactly("client-3");
        assertThat(handler.execute(new ClientListInput(null, null, "  "), context()).items())
                .hasSize(3);
    }

    private static ClientConnectionVO connection(
            String clientId, String groupOrTopic, String producerGroup, String address) {
        return ClientConnectionVO.builder()
                .clientId(clientId)
                .type(producerGroup == null ? ClientType.Consumer : ClientType.Producer)
                .groupOrTopic(groupOrTopic)
                .producerGroup(producerGroup)
                .address(address)
                .build();
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of("instance-a", null, Map.of("instanceId", "instance-a"));
    }
}
