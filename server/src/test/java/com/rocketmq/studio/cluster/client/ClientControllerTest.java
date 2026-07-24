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
package com.rocketmq.studio.cluster.client;

import com.rocketmq.studio.common.domain.enums.ClientLanguage;
import com.rocketmq.studio.common.domain.enums.ClientType;
import com.rocketmq.studio.common.domain.enums.Protocol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @Test
    void listConnectionsShouldReturnProtocolAwareClientRows() throws Exception {
        ClientConnectionVO grpcClient = ClientConnectionVO.builder()
                .clientId("grpc-client-001")
                .type(ClientType.Consumer)
                .groupOrTopic("cg-order")
                .protocol(Protocol.gRPC)
                .address("10.0.0.1:8081")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .connectedAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .clusterName("production-cluster")
                .build();
        ClientConnectionVO remotingClient = ClientConnectionVO.builder()
                .clientId("remoting-client-001")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .protocol(Protocol.Remoting)
                .address("10.0.0.2:10911")
                .language(ClientLanguage.Go)
                .version("4.9.8")
                .connectedAt(LocalDateTime.of(2026, 1, 1, 12, 5))
                .clusterName("production-cluster")
                .build();
        when(clientService.listConnections(null, null)).thenReturn(List.of(grpcClient, remotingClient));

        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].clientId").value("grpc-client-001"))
                .andExpect(jsonPath("$.data[0].type").value("Consumer"))
                .andExpect(jsonPath("$.data[0].protocol").value("gRPC"))
                .andExpect(jsonPath("$.data[0].language").value("Java"))
                .andExpect(jsonPath("$.data[0].version").value("5.1.0"))
                .andExpect(jsonPath("$.data[1].clientId").value("remoting-client-001"))
                .andExpect(jsonPath("$.data[1].type").value("Producer"))
                .andExpect(jsonPath("$.data[1].protocol").value("Remoting"));

        verify(clientService).listConnections(null, null);
    }

    @Test
    void listConnectionsShouldPassClusterAndTypeFilters() throws Exception {
        when(clientService.listConnections("production-cluster", "Consumer")).thenReturn(List.of());

        mockMvc.perform(get("/api/clients")
                        .param("clusterId", "production-cluster")
                        .param("type", "Consumer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(clientService).listConnections("production-cluster", "Consumer");
    }
}
