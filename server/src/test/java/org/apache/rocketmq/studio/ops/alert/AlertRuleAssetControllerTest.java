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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                new AlertRuleAssetInfo("rocketmq-consumer-lag-high", "rocketmq-consumer.rules", 1, List.of("warning"))
        ));

        mockMvc.perform(get("/api/alert-rules/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("rocketmq-broker-down"))
                .andExpect(jsonPath("$.data[0].ruleCount").value(1));
    }

    @Test
    void getAssetShouldReturnRawYaml() throws Exception {
        when(alertRuleAssetService.getAssetYaml("rocketmq-broker-down"))
                .thenReturn("groups:\n  - name: rocketmq-broker.rules\n    rules:\n      - alert: RocketMQBrokerDown\n");

        mockMvc.perform(get("/api/alert-rules/assets/rocketmq-broker-down"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void exportAssetShouldReturnAttachment() throws Exception {
        when(alertRuleAssetService.getAssetYaml("rocketmq-broker-down"))
                .thenReturn("groups:\n  - name: rocketmq-broker.rules\n");

        mockMvc.perform(get("/api/alert-rules/assets/rocketmq-broker-down/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/x-yaml"))
                .andExpect(header().string("Content-Disposition", startsWith("attachment;")))
                .andExpect(header().string("Content-Disposition", containsString("rocketmq-broker-down.yaml")));
    }
}
