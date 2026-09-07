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

    @MockBean
    private NativeAlertRuleTestService nativeAlertRuleTestService;

    @MockBean
    private NativeAlertMetricCatalogService metricCatalogService;

    @MockBean
    private AlertRuleTransferService transferService;

    @Test
    void businessRulesEndpointShouldReturnBusinessRulesTest() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(1L)
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .enabled(true)
                .build();
        when(alertService.listRules(AlertDomain.BUSINESS)).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/business-alert-rules"))
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
    void businessRulesPageEndpointShouldForwardFiltersTest() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).name("High Lag")
                .domain(AlertDomain.BUSINESS).enabled(true).build();
        when(alertService.listRules(AlertDomain.BUSINESS, "lag", true, 2, 10))
                .thenReturn(PageResult.of(List.of(rule), 11, 2, 10));

        mockMvc.perform(get("/api/business-alert-rules/page")
                        .param("search", "lag").param("enabled", "true")
                        .param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(11))
                .andExpect(jsonPath("$.data.items[0].id").value(1));

        verify(alertService).listRules(AlertDomain.BUSINESS, "lag", true, 2, 10);
    }

    @Test
    void legacyRulesEndpointShouldContinueToReturnAllRulesTest() throws Exception {
        AlertRuleVO business = AlertRuleVO.builder().id(1L).name("High Lag")
                .domain(AlertDomain.BUSINESS).enabled(true).build();
        AlertRuleVO cluster = AlertRuleVO.builder().id(2L).name("Broker down")
                .domain(AlertDomain.CLUSTER).enabled(true).build();
        when(alertService.listRules()).thenReturn(List.of(business, cluster));

        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[1].domain").value("CLUSTER"));

        verify(alertService).listRules();
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
    void exportsAndImportsBusinessRuleTransferTest() throws Exception {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.BUSINESS);
        transfer.setRules(List.of());
        when(transferService.exportRules(AlertDomain.BUSINESS)).thenReturn(transfer);

        mockMvc.perform(get("/api/alert-rules/transfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.domain").value("BUSINESS"));

        AlertRuleRequestDTO rule = new AlertRuleRequestDTO();
        rule.setName("Consumer lag");
        transfer.setRules(List.of(rule));
        mockMvc.perform(post("/api/alert-rules/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isOk());
        verify(transferService).importRules(eq(AlertDomain.BUSINESS), any(AlertRuleTransferDTO.class));
    }

    @Test
    void createRuleShouldReturnCreatedRuleTest() throws Exception {
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
        when(alertService.createRule(eq(AlertDomain.BUSINESS), any(AlertRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("High Lag"));
    }

    @Test
    void createRuleShouldRejectNullRequestBodyTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Alert rule request is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidRuleFieldsTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"operator\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("operator is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectUnsupportedNotificationChannelsTest() throws Exception {
        AlertRuleVO request = AlertRuleVO.builder().name("High Lag").metric("consumer.lag.total")
                .channels(List.of("webhook")).enabled(true).build();

        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("channel is unsupported"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidDurationTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"duration\":\"later\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("duration is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void createRuleShouldRejectInvalidSeverityTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\",\"severity\":\"urgent\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("severity is invalid"));

        verifyNoInteractions(alertService);
    }

    @Test
    void updateRuleShouldRejectMissingIdTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"High Lag\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void updateRuleShouldRejectNullRequestBodyTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Alert rule request is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldPassValidatedRequestTest() throws Exception {
        AlertRuleVO toggled = AlertRuleVO.builder()
                .id(1L)
                .name("High Lag")
                .enabled(false)
                .build();
        when(alertService.toggleRule(AlertDomain.BUSINESS, 1L, false)).thenReturn(toggled);

        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1, "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(alertService).toggleRule(eq(AlertDomain.BUSINESS), eq(1L), eq(false));
    }

    @Test
    void toggleRuleShouldRejectMissingIdTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldRejectMissingEnabledTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("enabled is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void toggleRuleShouldRejectInvalidEnabledTypeTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1, "enabled", "invalid"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        verifyNoInteractions(alertService);
    }

    @Test
    void deleteRuleShouldPassValidatedRequestTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(alertService).deleteRule(AlertDomain.BUSINESS, 1L);
    }

    @Test
    void deleteRuleShouldRejectBlankIdTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Collections.singletonMap("id", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(alertService);
    }

    @Test
    void bulkToggleShouldReturnPartialResultsTest() throws Exception {
        AlertRuleVO updated = AlertRuleVO.builder().id(1L).enabled(false).build();
        when(alertService.bulkToggleRules(AlertDomain.BUSINESS, List.of(1L, 999L), false))
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
    void bulkDeleteShouldRejectEmptyIdsTest() throws Exception {
        mockMvc.perform(post("/api/alert-rules/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ids are required"));

        verifyNoInteractions(alertService);
    }
}
