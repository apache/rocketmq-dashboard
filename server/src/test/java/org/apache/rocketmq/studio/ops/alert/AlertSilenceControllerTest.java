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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertSilenceController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertSilenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertSilenceService silenceService;

    @Test
    void listPageShouldReturnPageResultTest() throws Exception {
        AlertSilenceVO silence = AlertSilenceVO.builder()
                .id(12L)
                .domain(AlertDomain.CLUSTER)
                .startsAt(LocalDateTime.of(2026, 8, 22, 9, 0))
                .endsAt(LocalDateTime.of(2026, 8, 22, 10, 0))
                .createdBy("admin")
                .build();
        when(silenceService.listPage(2, 10)).thenReturn(PageResult.of(List.of(silence), 31, 2, 10));

        mockMvc.perform(get("/api/alert-silences/page")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].id").value(12))
                .andExpect(jsonPath("$.data.items[0].domain").value("CLUSTER"))
                .andExpect(jsonPath("$.data.total").value(31))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(silenceService).listPage(eq(2), eq(10));
    }

    @Test
    void createShouldBindAndReturnRecurringScheduleTest() throws Exception {
        AlertSilenceVO silence = AlertSilenceVO.builder().id(13L)
                .startsAt(LocalDateTime.of(2026, 9, 7, 1, 0))
                .endsAt(LocalDateTime.of(2026, 9, 7, 2, 0))
                .recurrence(AlertSilenceRecurrence.WEEKLY).timeZone("Asia/Shanghai")
                .recurrenceDays(Set.of(1, 3, 5))
                .recurrenceUntil(LocalDateTime.of(2026, 10, 1, 0, 0)).createdBy("admin").build();
        when(silenceService.create(any(CreateAlertSilenceDTO.class))).thenReturn(silence);

        mockMvc.perform(post("/api/alert-silences")
                        .contentType("application/json")
                        .content("""
                                {
                                  "startsAt": "2026-09-07T09:00:00+08:00",
                                  "endsAt": "2026-09-07T10:00:00+08:00",
                                  "recurrence": "WEEKLY",
                                  "timeZone": "Asia/Shanghai",
                                  "recurrenceDays": [1, 3, 5],
                                  "recurrenceUntil": "2026-10-01T08:00:00+08:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.data.id").value(13))
                .andExpect(jsonPath("$.data.recurrence").value("WEEKLY"))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data.recurrenceDays.length()").value(3))
                .andExpect(jsonPath("$.data.recurrenceUntil").value("2026-10-01T00:00:00"));

        verify(silenceService).create(org.mockito.ArgumentMatchers.argThat(request ->
                request.getRecurrence() == AlertSilenceRecurrence.WEEKLY
                        && request.getRecurrenceDays().equals(Set.of(1, 3, 5))
                        && "Asia/Shanghai".equals(request.getTimeZone())));
    }

    @Test
    void listShouldReturnAllSilencesTest() throws Exception {
        AlertSilenceVO silence = AlertSilenceVO.builder()
                .id(12L)
                .domain(AlertDomain.CLUSTER)
                .startsAt(LocalDateTime.of(2026, 8, 22, 9, 0))
                .endsAt(LocalDateTime.of(2026, 8, 22, 10, 0))
                .createdBy("admin")
                .build();
        when(silenceService.list()).thenReturn(List.of(silence));

        mockMvc.perform(get("/api/alert-silences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(12))
                .andExpect(jsonPath("$.data[0].domain").value("CLUSTER"));

        verify(silenceService).list();
    }

    @Test
    void deleteShouldDelegateTheIdTest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/alert-silences/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(silenceService).delete(7L);
    }
}
