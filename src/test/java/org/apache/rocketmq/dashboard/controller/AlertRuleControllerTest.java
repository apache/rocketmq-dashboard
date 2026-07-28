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

package org.apache.rocketmq.dashboard.controller;

import com.alibaba.fastjson.JSON;
import java.util.Collections;
import org.apache.rocketmq.dashboard.model.AlertRuleVO;
import org.apache.rocketmq.dashboard.service.AlertRuleService;
import org.apache.rocketmq.dashboard.service.impl.AlertRuleServiceImpl;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AlertRuleControllerTest extends BaseControllerTest {

    @InjectMocks
    private AlertRuleController alertRuleController;

    @Spy
    private AlertRuleService alertRuleService = new AlertRuleServiceImpl();

    @Override
    protected Object getTestController() {
        return alertRuleController;
    }

    private AlertRuleVO buildRule(String alert) {
        return AlertRuleVO.builder()
            .alert(alert)
            .group("broker")
            .expr("up{job=\"broker\"} == 0")
            .forDuration("5m")
            .severity("critical")
            .team("broker")
            .summary("Broker is down")
            .channels(Collections.singletonList("email"))
            .enabled(true)
            .build();
    }

    @Test
    public void testListRulesEmpty() throws Exception {
        requestBuilder = get("/api/alert/rules");
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    public void testCreateAndListRule() throws Exception {
        requestBuilder = post("/api/alert/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.toJSONString(buildRule("BrokerDownAlert")));
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.alert").value("BrokerDownAlert"))
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        requestBuilder = get("/api/alert/rules");
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
            .andExpect(jsonPath("$.data[0].alert").value("BrokerDownAlert"));
    }

    @Test
    public void testUpdateRule() throws Exception {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));

        AlertRuleVO update = buildRule("BrokerDownAlertRenamed");
        requestBuilder = put("/api/alert/rules/" + created.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.toJSONString(update));
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data.id").value(created.getId()))
            .andExpect(jsonPath("$.data.alert").value("BrokerDownAlertRenamed"))
            .andExpect(jsonPath("$.data.createdAt").value(created.getCreatedAt()));
    }

    @Test
    public void testUpdateRuleNotFound() throws Exception {
        requestBuilder = put("/api/alert/rules/no-such-id")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JSON.toJSONString(buildRule("NotExist")));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.errMsg").isNotEmpty());
    }

    @Test
    public void testToggleRule() throws Exception {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));

        requestBuilder = post("/api/alert/rules/" + created.getId() + "/enable")
            .param("enabled", "false");
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    public void testToggleRuleNotFound() throws Exception {
        requestBuilder = post("/api/alert/rules/no-such-id/enable")
            .param("enabled", "true");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.errMsg").isNotEmpty());
    }

    @Test
    public void testDeleteRule() throws Exception {
        AlertRuleVO created = alertRuleService.createRule(buildRule("BrokerDownAlert"));

        requestBuilder = delete("/api/alert/rules/" + created.getId());
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data").value(true));

        requestBuilder = get("/api/alert/rules");
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform)
            .andExpect(jsonPath("$.data").isEmpty());
    }
}
