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
package org.apache.rocketmq.studio.cluster.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProducerController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProducerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProducerConnectionService producerConnectionService;

    @Test
    void listProducerGroupsShouldReturnSuggestions() throws Exception {
        when(producerConnectionService.listProducerGroups("instance-1", "order-topic", "pg", 20))
                .thenReturn(List.of("pg-order", "pg-payment"));

        mockMvc.perform(get("/api/producer/groups")
                        .param("instanceId", "instance-1")
                        .param("topic", "order-topic")
                        .param("query", "pg")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("pg-order"))
                .andExpect(jsonPath("$.data[1]").value("pg-payment"));

        verify(producerConnectionService).listProducerGroups("instance-1", "order-topic", "pg", 20);
    }

    @Test
    void listConnectionsShouldReturnLegacyConnectionSetPayload() throws Exception {
        ProducerConnectionVO connection = ProducerConnectionVO.builder()
                .clientId("producer-1")
                .clientAddr("10.0.0.1:38888")
                .language("Java")
                .versionDesc("5.1.0")
                .build();
        when(producerConnectionService.listConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(List.of(connection));

        mockMvc.perform(get("/api/producer/connection")
                        .param("instanceId", "instance-1")
                        .param("topic", "order-topic")
                        .param("producerGroup", "pg-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionSet").isArray())
                .andExpect(jsonPath("$.connectionSet[0].clientId").value("producer-1"))
                .andExpect(jsonPath("$.connectionSet[0].clientAddr").value("10.0.0.1:38888"))
                .andExpect(jsonPath("$.connectionSet[0].language").value("Java"))
                .andExpect(jsonPath("$.connectionSet[0].versionDesc").value("5.1.0"))
                .andExpect(jsonPath("$.summary.totalConnections").value(1))
                .andExpect(jsonPath("$.summary.uniqueClientCount").value(1))
                .andExpect(jsonPath("$.summary.uniqueAddressCount").value(1))
                .andExpect(jsonPath("$.summary.readiness").value("READY"));

        verify(producerConnectionService).listConnections("instance-1", "order-topic", "pg-order");
    }

    @Test
    void listConnectionsShouldRequireTopic() throws Exception {
        mockMvc.perform(get("/api/producer/connection")
                        .param("instanceId", "instance-1")
                        .param("producerGroup", "pg-order"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("topic is required"));

        verifyNoInteractions(producerConnectionService);
    }

    @Test
    void listConnectionsShouldRequireProducerGroup() throws Exception {
        mockMvc.perform(get("/api/producer/connection")
                        .param("instanceId", "instance-1")
                        .param("topic", "order-topic"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("producerGroup is required"));

        verifyNoInteractions(producerConnectionService);
    }

    @Test
    void listConnectionsShouldRejectBlankParameters() throws Exception {
        mockMvc.perform(get("/api/producer/connection")
                        .param("instanceId", "instance-1")
                        .param("topic", " ")
                        .param("producerGroup", "pg-order"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("topic is required"));

        verifyNoInteractions(producerConnectionService);
    }

    @Test
    void listConnectionsShouldRequireInstanceId() throws Exception {
        mockMvc.perform(get("/api/producer/connection")
                        .param("topic", "order-topic")
                        .param("producerGroup", "pg-order"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("instanceId is required"));

        verifyNoInteractions(producerConnectionService);
    }
}
