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

package com.rocketmq.studio.instance.group;

import com.rocketmq.studio.instance.topic.MetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConsumerGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MetadataService metadataService;

    @MockBean
    private ConsumerDiagnosticsService consumerDiagnosticsService;

    @Test
    void getConsumerStackShouldReturnStackTrace() throws Exception {
        ConsumerThreadStackVO thread = ConsumerThreadStackVO.builder()
                .threadName("ConsumeMessageThread_1")
                .threadId(42L)
                .state("RUNNABLE")
                .blockedTime(0L)
                .waitedTime(5L)
                .stackTrace(List.of("org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService.run"))
                .build();
        ConsumerStackTraceVO stackTrace = ConsumerStackTraceVO.builder()
                .groupName("cg-orders")
                .clientId("client-1")
                .capturedAt(LocalDateTime.of(2026, 7, 23, 12, 0))
                .threadCount(1)
                .threads(List.of(thread))
                .build();

        when(consumerDiagnosticsService.getConsumerStack("cg-orders", "client-1")).thenReturn(stackTrace);

        mockMvc.perform(get("/api/groups/cg-orders/instances/client-1/stack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groupName").value("cg-orders"))
                .andExpect(jsonPath("$.data.clientId").value("client-1"))
                .andExpect(jsonPath("$.data.threadCount").value(1))
                .andExpect(jsonPath("$.data.threads[0].threadName").value("ConsumeMessageThread_1"))
                .andExpect(jsonPath("$.data.threads[0].stackTrace[0]")
                        .value("org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService.run"));
    }
}
