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

import org.apache.rocketmq.dashboard.model.DlqAutoReplayReport;
import org.apache.rocketmq.dashboard.service.DlqAutoReplayService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DlqAutoReplayControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DlqAutoReplayService dlqAutoReplayService;

    @InjectMocks
    private DlqAutoReplayController dlqAutoReplayController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(dlqAutoReplayController).build();
    }

    @Test
    public void testGetReplayStatus() throws Exception {
        DlqAutoReplayReport report = new DlqAutoReplayReport();
        report.setConsumerGroup("test-group");
        report.setDlqTopic("%DLQ%test-group");
        report.setTotalDlqMessages(20);
        report.setStatus("IDLE");

        when(dlqAutoReplayService.getReplayStatus("test-group")).thenReturn(report);

        mockMvc.perform(get("/dlq/autoReplay/status.query")
            .param("consumerGroup", "test-group")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerGroup").value("test-group"))
            .andExpect(jsonPath("$.dlqTopic").value("%DLQ%test-group"))
            .andExpect(jsonPath("$.totalDlqMessages").value(20));
    }

    @Test
    public void testExecuteAutoReplay() throws Exception {
        DlqAutoReplayReport report = new DlqAutoReplayReport();
        report.setConsumerGroup("test-group");
        report.setReplayedMessages(50);
        report.setStatus("REPLAYING");

        when(dlqAutoReplayService.executeAutoReplay(anyString(), any())).thenReturn(report);

        mockMvc.perform(post("/dlq/autoReplay/execute.do")
            .param("consumerGroup", "test-group")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"rateLimitPerSecond\": 50, \"maxBatchSize\": 100}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerGroup").value("test-group"))
            .andExpect(jsonPath("$.replayedMessages").value(50));
    }
}
