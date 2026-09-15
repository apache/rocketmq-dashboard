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

import org.apache.rocketmq.dashboard.model.ProducerLatencyReport;
import org.apache.rocketmq.dashboard.service.ProducerLatencyProfilerService;
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

public class ProducerLatencyProfilerControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProducerLatencyProfilerService producerLatencyProfilerService;

    @InjectMocks
    private ProducerLatencyProfilerController producerLatencyProfilerController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(producerLatencyProfilerController).build();
    }

    @Test
    public void testProfileLatency() throws Exception {
        ProducerLatencyReport report = new ProducerLatencyReport();
        report.setTopic("test-topic");
        report.setProducerGroup("test-group");
        report.setAvgLatencyMs(12.5);
        report.setP95LatencyMs(28.0);
        report.setP99LatencyMs(45.0);
        report.setHealthStatus("HEALTHY");

        when(producerLatencyProfilerService.profileProducerLatency(anyString(), anyString(), anyInt()))
            .thenReturn(report);

        mockMvc.perform(get("/producer/latencyProfile.query")
            .param("topic", "test-topic")
            .param("producerGroup", "test-group")
            .param("timeWindowMinutes", "60")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("test-topic"))
            .andExpect(jsonPath("$.avgLatencyMs").value(12.5))
            .andExpect(jsonPath("$.healthStatus").value("HEALTHY"));
    }
}
