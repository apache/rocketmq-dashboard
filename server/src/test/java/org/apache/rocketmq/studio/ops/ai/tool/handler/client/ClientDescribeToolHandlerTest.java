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
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class ClientDescribeToolHandlerTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ClientDescribeToolHandler handler;

    @Test
    void executeShouldFilterByClientId() {
        assertThat(handler.name()).isEqualTo("rmq.client.describe");
        ClientConnectionVO conn1 = ClientConnectionVO.builder()
                .clientId("client-1")
                .type(ClientType.Producer)
                .address("127.0.0.1:12345")
                .build();
        ClientConnectionVO conn2 = ClientConnectionVO.builder()
                .clientId("client-2")
                .type(ClientType.Consumer)
                .address("127.0.0.1:12346")
                .build();
        when(clientService.listConnections(eq("cluster-1"), isNull(), isNull()))
                .thenReturn(List.of(conn1, conn2));

        ClusterListOutput<ClientConnectionVO> result = handler.execute(
                new ClientDescribeInput("cluster-1", "client-2", null), context("cluster-1"));

        assertThat(result.cluster()).isEqualTo("cluster-1");
        assertThat(result.items()).containsExactly(conn2);
        assertThat(result.items().getFirst().getClientId()).isEqualTo("client-2");
        assertThat(result.items().getFirst().getType()).isEqualTo(ClientType.Consumer);
    }
}
