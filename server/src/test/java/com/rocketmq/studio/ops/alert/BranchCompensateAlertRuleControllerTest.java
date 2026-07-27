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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BranchCompensateAlertRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
class BranchCompensateAlertRuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BranchCompensateAlertRuleService branchCompensateAlertRuleService;

    @Test
    void listRulesShouldReturnAllRules() throws Exception {
        BranchCompensateAlertRuleVO rule1 = BranchCompensateAlertRuleVO.builder()
                .id("1").name("Broker-A Lag").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(1024)
                .lagThresholdUnit("MB").duration("5m").severity("critical")
                .enabled(true).createdAt("2024-01-01 00:00:00").updatedAt("2024-01-01 00:00:00").build();
        BranchCompensateAlertRuleVO rule2 = BranchCompensateAlertRuleVO.builder()
                .id("2").name("Broker-B Lag").brokerName("broker-b")
                .clusterName("DefaultCluster").lagThreshold(512)
                .lagThresholdUnit("MB").duration("10m").severity("warning")
                .enabled(false).createdAt("2024-01-02 00:00:00").updatedAt("2024-01-02 00:00:00").build();
        List<BranchCompensateAlertRuleVO> rules = Arrays.asList(rule1, rule2);
        when(branchCompensateAlertRuleService.listRules()).thenReturn(rules);

        mockMvc.perform(get("/api/branch-compensate-alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[0].name").value("Broker-A Lag"))
                .andExpect(jsonPath("$.data[0].brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data[0].lagThreshold").value(1024))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[1].id").value("2"))
                .andExpect(jsonPath("$.data[1].name").value("Broker-B Lag"))
                .andExpect(jsonPath("$.data[1].enabled").value(false));

        verify(branchCompensateAlertRuleService).listRules();
    }

    @Test
    void listRulesShouldReturnEmptyList() throws Exception {
        when(branchCompensateAlertRuleService.listRules()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/branch-compensate-alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void createRuleShouldReturnCreatedRule() throws Exception {
        BranchCompensateAlertRuleVO input = BranchCompensateAlertRuleVO.builder()
                .name("New Rule").brokerName("broker-c")
                .clusterName("TestCluster").lagThreshold(2048)
                .lagThresholdUnit("GB").duration("15m").severity("info").build();
        BranchCompensateAlertRuleVO created = BranchCompensateAlertRuleVO.builder()
                .id("uuid-1").name("New Rule").brokerName("broker-c")
                .clusterName("TestCluster").lagThreshold(2048)
                .lagThresholdUnit("GB").duration("15m").severity("info")
                .enabled(false).createdAt("2024-06-01 12:00:00").updatedAt("2024-06-01 12:00:00").build();
        when(branchCompensateAlertRuleService.createRule(any(BranchCompensateAlertRuleVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/branch-compensate-alert-rules/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("uuid-1"))
                .andExpect(jsonPath("$.data.name").value("New Rule"))
                .andExpect(jsonPath("$.data.brokerName").value("broker-c"))
                .andExpect(jsonPath("$.data.lagThreshold").value(2048))
                .andExpect(jsonPath("$.data.lagThresholdUnit").value("GB"))
                .andExpect(jsonPath("$.data.severity").value("info"));

        verify(branchCompensateAlertRuleService).createRule(any(BranchCompensateAlertRuleVO.class));
    }

    @Test
    void updateRuleShouldReturnUpdatedRule() throws Exception {
        BranchCompensateAlertRuleVO input = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Updated Rule").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(2048)
                .lagThresholdUnit("MB").duration("10m").severity("critical").build();
        BranchCompensateAlertRuleVO updated = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Updated Rule").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(2048)
                .lagThresholdUnit("MB").duration("10m").severity("critical")
                .enabled(true).createdAt("2024-01-01 00:00:00").updatedAt("2024-06-01 12:00:00").build();
        when(branchCompensateAlertRuleService.updateRule(any(BranchCompensateAlertRuleVO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/branch-compensate-alert-rules/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("rule-1"))
                .andExpect(jsonPath("$.data.name").value("Updated Rule"))
                .andExpect(jsonPath("$.data.lagThreshold").value(2048))
                .andExpect(jsonPath("$.data.createdAt").value("2024-01-01 00:00:00"));

        verify(branchCompensateAlertRuleService).updateRule(any(BranchCompensateAlertRuleVO.class));
    }

    @Test
    void toggleRuleShouldReturnToggledRule() throws Exception {
        Map<String, Object> toggleRequest = new HashMap<>();
        toggleRequest.put("id", "rule-1");
        toggleRequest.put("enabled", true);
        BranchCompensateAlertRuleVO toggled = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Lag Alert").enabled(true)
                .updatedAt("2024-06-01 12:00:00").build();
        when(branchCompensateAlertRuleService.toggleRule("rule-1", true)).thenReturn(toggled);

        mockMvc.perform(post("/api/branch-compensate-alert-rules/toggle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toggleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("rule-1"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(branchCompensateAlertRuleService).toggleRule("rule-1", true);
    }

    @Test
    void deleteRuleShouldReturnSuccess() throws Exception {
        Map<String, String> deleteRequest = new HashMap<>();
        deleteRequest.put("id", "rule-1");

        mockMvc.perform(post("/api/branch-compensate-alert-rules/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(branchCompensateAlertRuleService).deleteRule("rule-1");
    }
}