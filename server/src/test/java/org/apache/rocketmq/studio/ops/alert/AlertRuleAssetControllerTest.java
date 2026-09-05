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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertRuleAssetController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertRuleAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlertRuleAssetService alertRuleAssetService;

    @Test
    void listAssetsShouldReturnMetadatas() throws Exception {
        when(alertRuleAssetService.listAssets()).thenReturn(List.of(
                new AlertRuleAssetInfo("rocketmq-broker-down", "rocketmq-broker.rules", 1, List.of("critical")),
                new AlertRuleAssetInfo("rocketmq-consumer-lag-high", "rocketmq-consumer.rules", 2, List.of("warning", "critical"))
        ));

        mockMvc.perform(get("/api/alert-rules/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("rocketmq-broker-down"))
                .andExpect(jsonPath("$.data[0].group").value("rocketmq-broker.rules"))
                .andExpect(jsonPath("$.data[0].ruleCount").value(1))
                .andExpect(jsonPath("$.data[1].name").value("rocketmq-consumer-lag-high"))
                .andExpect(jsonPath("$.data[1].ruleCount").value(2))
                .andExpect(jsonPath("$.data[1].severities[1]").value("critical"));
    }

    @Test
    void getAssetShouldReturnRawYaml() throws Exception {
        String yaml = "groups:\n  - name: rocketmq-broker.rules\n    rules:\n      - alert: RocketMQBrokerDown\n";
        when(alertRuleAssetService.getAssetYaml("rocketmq-broker-down"))
                .thenReturn(yaml);

        mockMvc.perform(get("/api/alert-rules/assets/rocketmq-broker-down"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(yaml));
    }

    @Test
    void exportAssetShouldReturnAttachment() throws Exception {
        String yaml = "groups:\n  - name: rocketmq-broker.rules\n";
        when(alertRuleAssetService.getAssetYaml("rocketmq-broker-down"))
                .thenReturn(yaml);

        mockMvc.perform(get("/api/alert-rules/assets/rocketmq-broker-down/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(yaml))
                .andExpect(header().string("Content-Type", "application/x-yaml"))
                .andExpect(header().string("Content-Disposition",
                        "form-data; name=\"attachment\"; filename=\"rocketmq-broker-down.yaml\""));
    }

    @Test
    void unknownAssetShouldReturn404Envelope() throws Exception {
        when(alertRuleAssetService.getAssetYaml("missing-asset"))
                .thenThrow(new BusinessException(404, "Unknown alert rule asset: missing-asset"));

        mockMvc.perform(get("/api/alert-rules/assets/missing-asset"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Unknown alert rule asset: missing-asset"));

        mockMvc.perform(get("/api/alert-rules/assets/missing-asset/export"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
