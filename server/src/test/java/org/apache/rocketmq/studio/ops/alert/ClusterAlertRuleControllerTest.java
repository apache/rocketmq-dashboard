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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClusterAlertRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClusterAlertRuleControllerTest {

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
    void listRulesShouldUseClusterDomainTest() throws Exception {
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(
                AlertRuleVO.builder().id(7L).name("Broker unavailable").domain(AlertDomain.CLUSTER).build()));

        mockMvc.perform(get("/api/cluster-alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].domain").value("CLUSTER"));
    }

    @Test
    void listRulesPageShouldUseClusterDomainTest() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder().id(7L).name("Broker unavailable")
                .domain(AlertDomain.CLUSTER).build();
        when(alertService.listRules(AlertDomain.CLUSTER, "broker", false, 1, 20))
                .thenReturn(PageResult.of(List.of(rule), 1, 1, 20));

        mockMvc.perform(get("/api/cluster-alert-rules/page")
                        .param("search", "broker").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7));

        verify(alertService).listRules(AlertDomain.CLUSTER, "broker", false, 1, 20);
    }

    @Test
    void listRuntimeShouldUseClusterDomainTest() throws Exception {
        when(alertService.listRuleRuntime(AlertDomain.CLUSTER)).thenReturn(List.of(
                AlertRuleRuntimeVO.builder().ruleId(7L).fingerprint("broker-a")
                        .status(AlertStateStatus.FIRING).consecutiveHits(3).build()));

        mockMvc.perform(get("/api/cluster-alert-rules/runtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleId").value(7))
                .andExpect(jsonPath("$.data[0].status").value("FIRING"))
                .andExpect(jsonPath("$.data[0].consecutiveHits").value(3));

        verify(alertService).listRuleRuntime(AlertDomain.CLUSTER);
    }

    @Test
    void exportsAndImportsClusterRuleTransferTest() throws Exception {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(List.of());
        when(transferService.exportRules(AlertDomain.CLUSTER)).thenReturn(transfer);

        mockMvc.perform(get("/api/cluster-alert-rules/transfer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.domain").value("CLUSTER"));

        AlertRuleRequestDTO rule = new AlertRuleRequestDTO();
        rule.setName("Broker unavailable");
        rule.setEnabled(true);
        transfer.setRules(List.of(rule));
        mockMvc.perform(post("/api/cluster-alert-rules/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transfer)))
                .andExpect(status().isOk());
        verify(transferService).importRules(eq(AlertDomain.CLUSTER), any(AlertRuleTransferDTO.class));
    }

    @Test
    void createRuleShouldForceClusterDomainTest() throws Exception {
        AlertRuleVO created = AlertRuleVO.builder().id(7L).name("Broker unavailable")
                .domain(AlertDomain.CLUSTER).build();
        when(alertService.createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/cluster-alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Broker unavailable\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.domain").value("CLUSTER"));

        verify(alertService).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    @Test
    void toggleRuleShouldUseClusterDomainTest() throws Exception {
        AlertRuleVO toggled = AlertRuleVO.builder().id(7L).enabled(false)
                .domain(AlertDomain.CLUSTER).build();
        when(alertService.toggleRule(AlertDomain.CLUSTER, 7L, false)).thenReturn(toggled);

        mockMvc.perform(post("/api/cluster-alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("id", 7, "enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(alertService).toggleRule(AlertDomain.CLUSTER, 7L, false);
    }
    @Test
    void deleteRuleShouldDelegateWithClusterDomain() throws Exception {
        mockMvc.perform(post("/api/cluster-alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(alertService).deleteRule(AlertDomain.CLUSTER, 5L);
    }

    @Test
    void bulkToggleShouldDelegateWithClusterDomain() throws Exception {
        when(alertService.bulkToggleRules(AlertDomain.CLUSTER, List.of(1L, 2L), false))
                .thenReturn(AlertRuleBulkResultVO.builder().succeededIds(List.of(1L, 2L)).build());

        mockMvc.perform(post("/api/cluster-alert-rules/bulk-toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededIds[0]").value(1));

        verify(alertService).bulkToggleRules(AlertDomain.CLUSTER, List.of(1L, 2L), false);
    }

    @Test
    void bulkDeleteShouldDelegateWithClusterDomain() throws Exception {
        when(alertService.bulkDeleteRules(AlertDomain.CLUSTER, List.of(1L, 2L)))
                .thenReturn(AlertRuleBulkResultVO.builder().succeededIds(List.of(1L, 2L)).build());

        mockMvc.perform(post("/api/cluster-alert-rules/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeededIds.length()").value(2));

        verify(alertService).bulkDeleteRules(AlertDomain.CLUSTER, List.of(1L, 2L));
    }

}
