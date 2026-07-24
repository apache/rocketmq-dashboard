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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
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
    void listConnectionsShouldReturnLegacyConnectionSetPayload() throws Exception {
        ProducerConnectionVO connection = ProducerConnectionVO.builder()
                .clientId("producer-1")
                .clientAddr("10.0.0.1:38888")
                .language("Java")
                .versionDesc("5.1.0")
                .build();
        when(producerConnectionService.listConnections("order-topic", "pg-order"))
                .thenReturn(List.of(connection));

        mockMvc.perform(get("/api/producer/connection")
                        .param("topic", "order-topic")
                        .param("producerGroup", "pg-order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionSet").isArray())
                .andExpect(jsonPath("$.connectionSet[0].clientId").value("producer-1"))
                .andExpect(jsonPath("$.connectionSet[0].clientAddr").value("10.0.0.1:38888"))
                .andExpect(jsonPath("$.connectionSet[0].language").value("Java"))
                .andExpect(jsonPath("$.connectionSet[0].versionDesc").value("5.1.0"));

        verify(producerConnectionService).listConnections("order-topic", "pg-order");
    }
}
