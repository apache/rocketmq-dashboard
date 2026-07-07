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

package com.rocketmq.studio.instance.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        when(dlqService.listDLQGroups(isNull())).thenReturn(List.of(group));

        mockMvc.perform(get("/api/dlq"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].groupName").value("test-group"))
                .andExpect(jsonPath("$.data[0].dlqTopic").value("%DLQ%test-group"))
                .andExpect(jsonPath("$.data[0].messageCount").value(10));
    }

    @Test
    void listDLQGroupsShouldPassClusterId() throws Exception {
        when(dlqService.listDLQGroups(eq("cluster-1"))).thenReturn(List.of());

        mockMvc.perform(get("/api/dlq")
                        .param("clusterId", "cluster-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(dlqService).listDLQGroups(eq("cluster-1"));
    }

    @Test
    void resendMessagesShouldReturnSuccess() throws Exception {
        Map<String, Object> body = Map.of(
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
                eq("test-group"), eq(1000L), eq(2000L), eq("target-topic"));
    }

    @Test
    void resendMessagesShouldHandleNullTimeRange() throws Exception {
        Map<String, Object> body = Map.of(
                "groupName", "test-group",
                "targetTopic", "target-topic"
        );

        mockMvc.perform(post("/api/dlq/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(dlqService).resendMessages(
                eq("test-group"), isNull(), isNull(), eq("target-topic"));
    }
}
