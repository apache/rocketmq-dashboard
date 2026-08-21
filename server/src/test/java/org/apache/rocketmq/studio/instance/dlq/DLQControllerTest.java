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

package org.apache.rocketmq.studio.instance.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DLQController.class)
@AutoConfigureMockMvc(addFilters = false)
class DLQControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DLQService dlqService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listDLQGroupsShouldReturnGroups() throws Exception {
        DLQGroupVO group = DLQGroupVO.builder()
                .groupName("test-group")
                .dlqTopic("%DLQ%test-group")
                .messageCount(10)
                .lastEnqueueTime(LocalDateTime.of(2026, 7, 8, 10, 0))
                .retryCount(3)
                .status("ACTIVE")
                .build();

        when(dlqService.listDLQGroups("instance-1", null, 1, 20))
                .thenReturn(PageResult.of(List.of(group), 1, 1, 20));

        mockMvc.perform(get("/api/dlq").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].groupName").value("test-group"))
                .andExpect(jsonPath("$.data.items[0].dlqTopic").value("%DLQ%test-group"))
                .andExpect(jsonPath("$.data.items[0].messageCount").value(10))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void listDLQGroupsShouldPassInstanceId() throws Exception {
        when(dlqService.listDLQGroups(eq("instance-1"), isNull(), eq(1), eq(20)))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/dlq")
                        .param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        verify(dlqService).listDLQGroups(eq("instance-1"), isNull(), eq(1), eq(20));
    }

    @Test
    void listDLQGroupsShouldPassSearchAndPaging() throws Exception {
        when(dlqService.listDLQGroups(eq("instance-1"), eq("order"), eq(2), eq(50)))
                .thenReturn(PageResult.empty(2, 50));

        mockMvc.perform(get("/api/dlq")
                        .param("instanceId", "instance-1")
                        .param("search", "order")
                        .param("page", "2")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(50));

        verify(dlqService).listDLQGroups(eq("instance-1"), eq("order"), eq(2), eq(50));
    }

    @Test
    void resendMessagesShouldReturnSuccess() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-1",
                "groupName", "test-group",
                "startTime", 1000,
                "endTime", 2000,
                "targetTopic", "target-topic"
        );

        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(dlqService).resendMessages(
                eq("instance-1"), eq("test-group"), eq(1000L), eq(2000L), eq("target-topic"));
    }

    @Test
    void resendMessagesShouldHandleNullTimeRange() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-1",
                "groupName", "test-group",
                "targetTopic", "target-topic"
        );

        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dlqService).resendMessages(
                eq("instance-1"), eq("test-group"), isNull(), isNull(), eq("target-topic"));
    }

    @Test
    void resendMessagesShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("DLQ resend request is required"));

        verifyNoInteractions(dlqService);
    }

    @Test
    void resendMessagesShouldRejectMissingGroupName() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-1",
                "startTime", 1000,
                "endTime", 2000,
                "targetTopic", "target-topic"
        );

        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("groupName is required"));

        verifyNoInteractions(dlqService);
    }

    @Test
    void resendMessagesShouldRejectInvalidTimeType() throws Exception {
        Map<String, Object> body = Map.of(
                "instanceId", "instance-1",
                "groupName", "test-group",
                "startTime", "invalid",
                "endTime", 2000,
                "targetTopic", "target-topic"
        );

        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(dlqService);
    }

    @Test
    void exportDLQMessagesShouldReturnJsonAttachment() throws Exception {
        when(dlqService.exportMessages(eq("instance-1"), eq("test-group"), isNull(), isNull(), isNull()))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of(
                                DLQMessageVO.builder()
                                        .msgId("msg-1")
                                        .topic("%DLQ%test-group")
                                        .queueId(0)
                                        .offset(5L)
                                        .storeTime(150L)
                                        .keys("key-a")
                                        .body("hello dlq")
                                        .bodyBase64("aGVsbG8gZGxx")
                                        .build()))
                        .truncated(false)
                        .failedQueueCount(0)
                        .limit(5000)
                        .build());

        mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", "test-group"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dlq-test-group.json\""))
                .andExpect(header().string("X-DLQ-Export-Truncated", "false"))
                .andExpect(header().string("X-DLQ-Export-FailedQueues", "0"))
                .andExpect(header().string("X-DLQ-Export-Limit", "5000"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].msgId").value("msg-1"))
                .andExpect(jsonPath("$[0].body").value("hello dlq"));

        verify(dlqService).exportMessages(eq("instance-1"), eq("test-group"), isNull(), isNull(), isNull());
    }

    @Test
    void exportDLQMessagesShouldPassTimeRangeTest() throws Exception {
        when(dlqService.exportMessages(eq("instance-1"), eq("test-group"), eq(1000L), eq(2000L), eq(100)))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of())
                        .truncated(false)
                        .failedQueueCount(0)
                        .limit(100)
                        .build());

        mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", "test-group")
                        .param("startTime", "1000")
                        .param("endTime", "2000")
                        .param("maxCount", "100"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dlq-test-group.json\""))
                .andExpect(header().string("X-DLQ-Export-Limit", "100"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(dlqService).exportMessages(eq("instance-1"), eq("test-group"), eq(1000L), eq(2000L), eq(100));
    }

    @Test
    void exportDLQMessagesShouldExposeIncompleteScanMetadata() throws Exception {
        when(dlqService.exportMessages(eq("instance-1"), eq("test-group"), isNull(), isNull(), isNull()))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of())
                        .truncated(true)
                        .failedQueueCount(2)
                        .limit(5000)
                        .build());

        mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", "test-group"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-DLQ-Export-Truncated", "true"))
                .andExpect(header().string("X-DLQ-Export-FailedQueues", "2"))
                .andExpect(header().string("X-DLQ-Export-Limit", "5000"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void exportDLQMessagesShouldSanitizeHeaderUnsafeGroupNameCharacters() throws Exception {
        when(dlqService.exportMessages(eq("instance-1"), eq("we\"ird\\group"), isNull(), isNull(), isNull()))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of())
                        .truncated(false)
                        .failedQueueCount(0)
                        .limit(5000)
                        .build());

        mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", "we\"ird\\group"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dlq-we_ird_group.json\""));
    }

    @Test
    void exportDLQMessagesShouldEmitRfc5987FilenameForNonAsciiGroupName() throws Exception {
        // CJK group name written as unicode escapes because checkstyle rejects raw chinese characters.
        String cjkGroup = "\u8BA2\u5355";
        when(dlqService.exportMessages(eq("instance-1"), eq(cjkGroup), isNull(), isNull(), isNull()))
                .thenReturn(DLQExportResultVO.builder()
                        .messages(List.of())
                        .truncated(false)
                        .failedQueueCount(0)
                        .limit(5000)
                        .build());

        mockMvc.perform(get("/api/dlq/export")
                        .param("instanceId", "instance-1")
                        .param("groupName", cjkGroup))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
                    Assertions.assertNotNull(disposition);
                    Assertions.assertTrue(
                            disposition.matches(
                                    "attachment; filename=\"[^\"]*\"; filename\\*=UTF-8''dlq-%E8%AE%A2%E5%8D%95\\.json"),
                            "unexpected content disposition: " + disposition);
                });
    }
}
