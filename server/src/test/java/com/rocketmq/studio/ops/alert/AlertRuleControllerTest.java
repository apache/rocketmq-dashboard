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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AlertService alertService;

    @Test
    void listRulesShouldReturnRules() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id("rule-1")
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        when(alertService.listRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value("rule-1"))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void createRuleShouldReturnCreatedRule() throws Exception {
        AlertRuleVO request = AlertRuleVO.builder()
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        AlertRuleVO created = AlertRuleVO.builder()
                .id("rule-1")
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        when(alertService.createRule(any(AlertRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("rule-1"))
                .andExpect(jsonPath("$.data.name").value("High Lag"));
    }

    @Test
    void toggleRuleShouldPassValidatedRequest() throws Exception {
        AlertRuleVO toggled = AlertRuleVO.builder()
                .id("rule-1")
                .name("High Lag")
                .enabled(false)
                .build();
        when(alertService.toggleRule("rule-1", false)).thenReturn(toggled);

        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "rule-1", "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("rule-1"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(alertService).toggleRule(eq("rule-1"), eq(false));
    }

    @Test
    void toggleRuleShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldRejectMissingEnabled() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "rule-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("enabled is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldRejectInvalidEnabledType() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "rule-1", "enabled", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(alertService);
    }

    @Test
    void deleteRuleShouldPassValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", "rule-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(alertService).deleteRule("rule-1");
    }

    @Test
    void deleteRuleShouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }
}
