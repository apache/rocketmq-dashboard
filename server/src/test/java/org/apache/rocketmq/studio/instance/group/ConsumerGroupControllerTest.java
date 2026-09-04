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
import org.apache.rocketmq.studio.common.domain.PageResult;
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
    void listConsumerGroupsShouldPassQueryParams() throws Exception {
        when(metadataService.listConsumerGroups("instance-a", "cluster-a", "orders"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/groups")
                        .param("instanceId", "instance-a")
                        .param("clusterId", "cluster-a")
                        .param("search", "orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(metadataService).listConsumerGroups("instance-a", "cluster-a", "orders");
    }

    @Test
    void listConsumerGroupsPageShouldPassSelectedInstanceFiltersAndPaging() throws Exception {
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(), 3, 2, 20);
        when(metadataService.listConsumerGroupsPage("instance-a", "cluster-a", "orders", 2, 20))
                .thenReturn(page);

        mockMvc.perform(get("/api/groups/page")
                        .param("instanceId", "instance-a")
                        .param("clusterId", "cluster-a")
                        .param("search", "orders")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(metadataService).listConsumerGroupsPage("instance-a", "cluster-a", "orders", 2, 20);
    }

    @Test
    void exportConsumerGroupsShouldPassViewFiltersAndSelectedNames() throws Exception {
        when(metadataService.exportConsumerGroups("instance-a", "orders", "Pop",
                List.of("cg-a", "cg-b"))).thenReturn("\"Name\"\n\"cg-a\"");

        mockMvc.perform(get("/api/groups/export")
                        .param("instanceId", "instance-a")
                        .param("search", "orders")
                        .param("subscriptionMode", "Pop")
                        .param("names", "cg-a, cg-b,cg-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("\"Name\"\n\"cg-a\""));

        verify(metadataService).exportConsumerGroups("instance-a", "orders", "Pop",
                List.of("cg-a", "cg-b"));
    }

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
    void importConsumerGroupsShouldNormalizeInstanceAndDelegateBatch() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "7",
                "groups", List.of(Map.of(
                        "name", "cg-orders",
                        "subscriptionMode", "Push",
                        "consumeType", "CLUSTERING",
                        "retryMaxTimes", 8
                ))
        );
        ConsumerGroupVO created = new ConsumerGroupVO();
        created.setName("cg-orders");
        when(instanceService.normalizeIdentifier("7")).thenReturn("rocketmq1");
        when(metadataService.importConsumerGroups(eq("rocketmq1"), any()))
                .thenReturn(ImportConsumerGroupsResultVO.builder()
                        .imported(1)
                        .failed(0)
                        .groups(List.of(created))
                        .failures(List.of())
                        .build());

        mockMvc.perform(post("/api/groups/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.groups[0].name").value("cg-orders"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CreateConsumerGroupDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(metadataService).importConsumerGroups(eq("rocketmq1"), captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getName()).isEqualTo("cg-orders");
        assertThat(captor.getValue().get(0).getRetryMaxTimes()).isEqualTo(8);
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
    void consumerGroupSettingsShouldUseTheSelectedInstance() throws Exception {
        ConsumerGroupSettingsVO settings = ConsumerGroupSettingsVO.builder().groupName("cg-orders")
                .retryQueueNums(2).retryMaxTimes(8).build();
        when(metadataService.getConsumerGroupSettings("instance-a", "cg-orders")).thenReturn(settings);

        mockMvc.perform(get("/api/groups/cg-orders/settings").param("instanceId", "instance-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retryQueueNums").value(2));

        verify(metadataService).getConsumerGroupSettings("instance-a", "cg-orders");
    }

    @Test
    void consumerGroupSettingsUpdateShouldValidateAndDelegate() throws Exception {
        Map<String, Object> body = Map.of("instanceId", "instance-a", "name", "cg-orders",
                "retryQueueNums", 2, "retryMaxTimes", 8);
        when(metadataService.updateConsumerGroupSettings("instance-a", "cg-orders", 2, 8))
                .thenReturn(ConsumerGroupSettingsVO.builder().groupName("cg-orders").retryQueueNums(2)
                        .retryMaxTimes(8).build());

        mockMvc.perform(post("/api/groups/settings").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retryMaxTimes").value(8));

        verify(metadataService).updateConsumerGroupSettings("instance-a", "cg-orders", 2, 8);
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
    void previewResetOffsetShouldPassValidatedRequestAndReturnQueueImpact() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-a",
                "name", "cg-orders",
                "topic", "orders",
                "timestamp", 1784246400000L
        );
        ResetConsumerOffsetQueuePreviewVO queue = ResetConsumerOffsetQueuePreviewVO.builder()
                .topic("orders")
                .broker("broker-a")
                .queueId(0)
                .minOffset(0L)
                .maxOffset(200L)
                .brokerOffset(120L)
                .consumerOffset(90L)
                .targetOffset(80L)
                .currentLag(30L)
                .projectedLag(40L)
                .offsetDelta(-10L)
                .riskLevel("WARNING")
                .message("Replays 10 message(s)")
                .build();
        ResetConsumerOffsetPreviewVO preview = ResetConsumerOffsetPreviewVO.builder()
                .instanceId("instance-a")
                .groupName("cg-orders")
                .topic("orders")
                .timestamp(1784246400000L)
                .complete(true)
                .allowReset(true)
                .queueCount(1)
                .warningCount(1)
                .rewindQueueCount(1)
                .fastForwardQueueCount(0)
                .currentTotalLag(30L)
                .projectedTotalLag(40L)
                .totalOffsetDelta(-10L)
                .warnings(List.of("1 queue(s) will move backward and may replay consumed messages"))
                .queues(List.of(queue))
                .build();
        when(metadataService.previewResetOffset("instance-a", "cg-orders", 1784246400000L, "orders"))
                .thenReturn(preview);

        mockMvc.perform(post("/api/groups/reset-offset/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groupName").value("cg-orders"))
                .andExpect(jsonPath("$.data.allowReset").value(true))
                .andExpect(jsonPath("$.data.queueCount").value(1))
                .andExpect(jsonPath("$.data.projectedTotalLag").value(40))
                .andExpect(jsonPath("$.data.queues[0].targetOffset").value(80))
                .andExpect(jsonPath("$.data.queues[0].riskLevel").value("WARNING"));

        verify(metadataService).previewResetOffset(eq("instance-a"), eq("cg-orders"),
                eq(1784246400000L), eq("orders"));
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
