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

package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsumerGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
class ConsumerGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MetadataService metadataService;

    @MockBean
    private org.apache.rocketmq.studio.instance.InstanceService instanceService;

    @MockBean
    private ConsumerDiagnosticsService consumerDiagnosticsService;

    @Test
    void createConsumerGroupShouldPassValidatedRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", 7,
                "name", "cg-orders",
                "clusterId", "cluster-a",
                "retryMaxTimes", 8,
                "delaySeconds", 0
        );
        ConsumerGroupVO created = new ConsumerGroupVO();
        created.setName("cg-orders");
        created.setClusterId("cluster-a");
        created.setRetryMaxTimes(8);

        when(metadataService.createConsumerGroup(any(ConsumerGroupVO.class))).thenReturn(created);
        when(instanceService.normalizeIdentifier("7")).thenReturn("rocketmq1");

        mockMvc.perform(post("/api/groups/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("cg-orders"))
                .andExpect(jsonPath("$.data.retryMaxTimes").value(8));

        ArgumentCaptor<ConsumerGroupVO> captor = ArgumentCaptor.forClass(ConsumerGroupVO.class);
        verify(metadataService).createConsumerGroup(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("cg-orders");
        assertThat(captor.getValue().getClusterId()).isEqualTo("cluster-a");
        assertThat(captor.getValue().getInstanceId()).isEqualTo("rocketmq1");
        assertThat(captor.getValue().getRetryMaxTimes()).isEqualTo(8);
    }

    @Test
    void createConsumerGroupShouldRejectMissingName() throws Exception {
        Map<String, Object> body = Map.of(
                "clusterId", "cluster-a",
                "retryMaxTimes", 8
        );

        mockMvc.perform(post("/api/groups/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void createConsumerGroupShouldRejectNegativeRetryMaxTimes() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", 7,
                "name", "cg-orders",
                "retryMaxTimes", -1
        );

        mockMvc.perform(post("/api/groups/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("retryMaxTimes must be zero or positive"));

        verifyNoInteractions(metadataService);
    }

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

        when(consumerDiagnosticsService.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .thenReturn(stackTrace);

        mockMvc.perform(get("/api/groups/cg-orders/instances/client-1/stack")
                        .param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groupName").value("cg-orders"))
                .andExpect(jsonPath("$.data.clientId").value("client-1"))
                .andExpect(jsonPath("$.data.threadCount").value(1))
                .andExpect(jsonPath("$.data.threads[0].threadName").value("ConsumeMessageThread_1"))
                .andExpect(jsonPath("$.data.threads[0].stackTrace[0]")
                        .value("org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService.run"));
        verify(consumerDiagnosticsService).getConsumerStack("instance-a", "cg-orders", "client-1");
    }

    @Test
    void groupRuntimeDiagnosticsShouldPassSelectedInstance() throws Exception {
        when(metadataService.getGroupProgress("instance-a", "cg-orders")).thenReturn(List.of());
        when(metadataService.getGroupSubscriptions("instance-a", "cg-orders")).thenReturn(List.of());

        mockMvc.perform(get("/api/groups/cg-orders/progress").param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/groups/cg-orders/subscriptions").param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(metadataService).getGroupProgress("instance-a", "cg-orders");
        verify(metadataService).getGroupSubscriptions("instance-a", "cg-orders");
    }

    @Test
    void resetOffsetShouldPassValidatedRequest() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "name", "cg-orders",
                "topic", "orders",
                "timestamp", 1784246400000L
        );

        mockMvc.perform(post("/api/groups/reset-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(metadataService).resetOffset(eq("instance-a"), eq("cg-orders"), eq(1784246400000L), eq("orders"));
    }

    @Test
    void deleteConsumerGroupShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/groups/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "cg-orders"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(metadataService).deleteConsumerGroup(isNull(), eq("cg-orders"));
    }

    @Test
    void deleteConsumerGroupShouldRejectMissingName() throws Exception {
        mockMvc.perform(post("/api/groups/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void deleteConsumerGroupShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/api/groups/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void resetOffsetShouldRejectMissingName() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "topic", "orders",
                "timestamp", 1784246400000L
        );

        mockMvc.perform(post("/api/groups/reset-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void resetOffsetShouldRejectMissingTimestamp() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "name", "cg-orders",
                "topic", "orders"
        );

        mockMvc.perform(post("/api/groups/reset-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("timestamp is required"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void resetOffsetShouldRejectNonPositiveTimestamp() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "name", "cg-orders",
                "topic", "orders",
                "timestamp", 0L
        );

        mockMvc.perform(post("/api/groups/reset-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("timestamp must be positive"));

        verifyNoInteractions(metadataService);
    }

    @Test
    void resetOffsetShouldRejectInvalidTimestampType() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "name", "cg-orders",
                "topic", "orders",
                "timestamp", "invalid"
        );

        mockMvc.perform(post("/api/groups/reset-offset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(metadataService);
    }
}
