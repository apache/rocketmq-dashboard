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
package com.rocketmq.studio.ops.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuditService auditService;

    @Test
    void queryLogsShouldReturnPageResult() throws Exception {
        AuditRecordVO record = AuditRecordVO.builder()
                .operator("admin")
                .operationType("DELETE")
                .target("topic-a")
                .result("SUCCESS")
                .build();
        when(auditService.queryLogs(eq(2), eq(10), eq("topic"), eq("DELETE"),
                eq("2026-07-01"), eq("2026-07-24"), eq("SUCCESS")))
                .thenReturn(PageResult.of(List.of(record), 1, 2, 10));

        mockMvc.perform(get("/api/audit-logs")
                        .param("page", "2")
                        .param("pageSize", "10")
                        .param("search", "topic")
                        .param("operationType", "DELETE")
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-24")
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].operator").value("admin"))
                .andExpect(jsonPath("$.data.items[0].operationType").value("DELETE"))
                .andExpect(jsonPath("$.data.total").value(1));

        verify(auditService).queryLogs(eq(2), eq(10), eq("topic"), eq("DELETE"),
                eq("2026-07-01"), eq("2026-07-24"), eq("SUCCESS"));
    }

    @Test
    void cleanupLogsShouldUseProvidedRetention() throws Exception {
        when(auditService.cleanupLogs(90)).thenReturn(7);

        mockMvc.perform(post("/api/audit-logs/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("beforeDays", 90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deleted").value(7));

        verify(auditService).cleanupLogs(90);
    }

    @Test
    void cleanupLogsShouldDefaultRetentionWhenBodyIsEmpty() throws Exception {
        when(auditService.cleanupLogs(30)).thenReturn(3);

        mockMvc.perform(post("/api/audit-logs/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(3));

        verify(auditService).cleanupLogs(30);
    }

    @Test
    void cleanupLogsShouldDefaultRetentionWhenBeforeDaysIsMissing() throws Exception {
        when(auditService.cleanupLogs(30)).thenReturn(3);

        mockMvc.perform(post("/api/audit-logs/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(3));

        verify(auditService).cleanupLogs(30);
    }

    @Test
    void cleanupLogsShouldRejectNonPositiveRetention() throws Exception {
        mockMvc.perform(post("/api/audit-logs/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("beforeDays", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("beforeDays must be greater than 0"));

        verifyNoInteractions(auditService);
    }

    @Test
    void cleanupLogsShouldRejectInvalidRetentionType() throws Exception {
        mockMvc.perform(post("/api/audit-logs/cleanup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("beforeDays", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(auditService);
    }

    @Test
    void queryLogsShouldUseDefaultPagination() throws Exception {
        when(auditService.queryLogs(eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(PageResult.of(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(auditService).queryLogs(eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), isNull());
    }
}
