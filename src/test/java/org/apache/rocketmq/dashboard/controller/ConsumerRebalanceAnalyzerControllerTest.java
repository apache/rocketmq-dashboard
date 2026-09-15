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

import org.apache.rocketmq.dashboard.model.ConsumerRebalanceHistoryReport;
import org.apache.rocketmq.dashboard.service.ConsumerRebalanceAnalyzerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerRebalanceAnalyzerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ConsumerRebalanceAnalyzerService consumerRebalanceAnalyzerService;

    @InjectMocks
    private ConsumerRebalanceAnalyzerController consumerRebalanceAnalyzerController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(consumerRebalanceAnalyzerController).build();
    }

    @Test
    public void testQueryRebalanceHistory() throws Exception {
        ConsumerRebalanceHistoryReport report = new ConsumerRebalanceHistoryReport();
        report.setConsumerGroup("test-group");
        report.setTotalRebalanceEvents(5);
        report.setFlappingScore(45);
        report.setStabilityLevel("MODERATE");

        when(consumerRebalanceAnalyzerService.analyzeRebalanceHistory(anyString(), anyInt()))
            .thenReturn(report);

        mockMvc.perform(get("/consumer/rebalanceHistory.query")
            .param("consumerGroup", "test-group")
            .param("lookbackHours", "24")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerGroup").value("test-group"))
            .andExpect(jsonPath("$.totalRebalanceEvents").value(5))
            .andExpect(jsonPath("$.stabilityLevel").value("MODERATE"));
    }
}
