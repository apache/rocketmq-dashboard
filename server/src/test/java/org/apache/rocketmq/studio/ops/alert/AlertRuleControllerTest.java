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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
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
                .id(1L)
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        when(alertService.listRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].enabled").value(true));
    }

    @Test
    void listRulesPageShouldPassFiltersAndReturnPageContract() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(1L)
                .name("High Lag")
                .enabled(true)
                .build();
        when(alertService.listRules("lag", true, 2, 20))
                .thenReturn(PageResult.of(List.of(rule), 21, 2, 20));

        mockMvc.perform(get("/api/alert-rules/page")
                        .param("search", "lag")
                        .param("enabled", "true")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(1));

        verify(alertService).listRules("lag", true, 2, 20);
    }

    @Test
    void exportRulesShouldReturnGeneratedYaml() throws Exception {
        when(alertService.exportPrometheusRulesYaml()).thenReturn("groups:\n");

        mockMvc.perform(get("/api/alert-rules/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.rules").value("groups:\n"));
    }

    @Test
    void createRuleShouldReturnCreatedRule() throws Exception {
        AlertRuleVO request = AlertRuleVO.builder()
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        AlertRuleVO created = AlertRuleVO.builder()
                .id(1L)
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        when(alertService.createRule(any(AlertRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("High Lag"));
    }

    @Test
    void createRuleShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Alert rule request is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidRuleFields() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"operator\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("operator is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidDuration() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"duration\":\"later\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("duration is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidSeverity() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"severity\":\"urgent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("severity is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void updateRuleShouldRejectMissingId() throws Exception {
        mockMvc.perform(post("/api/alert-rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void updateRuleShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/alert-rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Alert rule request is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldPassValidatedRequest() throws Exception {
        AlertRuleVO toggled = AlertRuleVO.builder()
                .id(1L)
                .name("High Lag")
                .enabled(false)
                .build();
        when(alertService.toggleRule(1L, false)).thenReturn(toggled);

        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1, "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(alertService).toggleRule(eq(1L), eq(false));
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
                        .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("enabled is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldRejectInvalidEnabledType() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1, "enabled", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(alertService);
    }

    @Test
    void deleteRuleShouldPassValidatedRequest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(alertService).deleteRule(1L);
    }

    @Test
    void deleteRuleShouldRejectBlankId() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("id", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void bulkToggleShouldReturnPartialResults() throws Exception {
        AlertRuleVO updated = AlertRuleVO.builder().id(1L).enabled(false).build();
        when(alertService.bulkToggleRules(List.of(1L, 999L), false))
                .thenReturn(AlertRuleBulkResultVO.builder()
                        .succeededIds(List.of(1L))
                        .failures(Map.of(999L, "Alert rule not found"))
                        .updatedRules(List.of(updated)).build());

        mockMvc.perform(post("/api/alert-rules/bulk-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,999],\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededIds[0]").value(1))
                .andExpect(jsonPath("$.data.failures['999']").value("Alert rule not found"))
                .andExpect(jsonPath("$.data.updatedRules[0].enabled").value(false));
    }

    @Test
    void bulkDeleteShouldRejectEmptyIds() throws Exception {
        mockMvc.perform(post("/api/alert-rules/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ids are required"));

        verifyNoInteractions(alertService);
    }
}
