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
package com.rocketmq.studio.ops.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemAlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertService alertService;

    @Test
    void listAlertsShouldReturnSystemAlerts() throws Exception {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id("alert-1")
                .level(AlertLevel.error)
                .title("Broker Down")
                .acknowledged(false)
                .build();
        when(alertService.listAlerts("error")).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/system-alerts").param("level", "error"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("alert-1"))
                .andExpect(jsonPath("$.data[0].level").value("error"))
                .andExpect(jsonPath("$.data[0].acknowledged").value(false));

        verify(alertService).listAlerts("error");
    }

    @Test
    void acknowledgeAlertShouldPassValidatedRequest() throws Exception {
        SystemAlertVO acknowledged = SystemAlertVO.builder()
                .id("alert-1")
                .level(AlertLevel.warning)
                .title("High Lag")
                .acknowledged(true)
                .build();
        when(alertService.acknowledgeAlert("alert-1")).thenReturn(acknowledged);

        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "alert-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("alert-1"))
                .andExpect(jsonPath("$.data.acknowledged").value(true));

        verify(alertService).acknowledgeAlert("alert-1");
    }

    @Test
    void acknowledgeAlertShouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void acknowledgeAlertShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/system-alerts/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void clearAcknowledgedShouldReturnClearedCount() throws Exception {
        when(alertService.clearAcknowledged()).thenReturn(3);

        mockMvc.perform(post("/api/system-alerts/clear-acknowledged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cleared").value(3));

        verify(alertService).clearAcknowledged();
    }
}
