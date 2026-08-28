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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
