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

import org.apache.rocketmq.dashboard.model.ConsumerThreadPoolSaturationReport;
import org.apache.rocketmq.dashboard.service.ConsumerThreadPoolMonitorService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerThreadPoolMonitorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConsumerThreadPoolMonitorService consumerThreadPoolMonitorService;

    @InjectMocks
    private ConsumerThreadPoolMonitorController consumerThreadPoolMonitorController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(consumerThreadPoolMonitorController).build();
    }

    @Test
    public void testInspectThreadPool() throws Exception {
        ConsumerThreadPoolSaturationReport report = new ConsumerThreadPoolSaturationReport();
        report.setConsumerGroup("test-group");
        report.setTotalClients(2);
        report.setOverallSaturationPercent(45.5);
        report.setHealthStatus("HEALTHY");

        when(consumerThreadPoolMonitorService.inspectThreadPoolSaturation(anyString(), anyString()))
            .thenReturn(report);

        mockMvc.perform(get("/consumer/threadPoolSaturation.query")
            .param("consumerGroup", "test-group")
            .param("clientId", "c-1")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerGroup").value("test-group"))
            .andExpect(jsonPath("$.totalClients").value(2))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));
    }
}
