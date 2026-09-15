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

import org.apache.rocketmq.dashboard.model.TopicDeduplicationHealthReport;
import org.apache.rocketmq.dashboard.service.TopicDeduplicationHealthService;
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

public class TopicDeduplicationHealthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TopicDeduplicationHealthService topicDeduplicationHealthService;

    @InjectMocks
    private TopicDeduplicationHealthController topicDeduplicationHealthController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(topicDeduplicationHealthController).build();
    }

    @Test
    public void testQueryDeduplicationHealth() throws Exception {
        TopicDeduplicationHealthReport report = new TopicDeduplicationHealthReport();
        report.setTopic("order-topic");
        report.setSampledMessageCount(1000);
        report.setDuplicateRatioPercent(2.5);
        report.setIdempotencyHealthScore("MODERATE_RISK");

        when(topicDeduplicationHealthService.inspectDeduplicationHealth(anyString(), anyInt()))
            .thenReturn(report);

        mockMvc.perform(get("/topic/dedupHealth.query")
            .param("topic", "order-topic")
            .param("sampleSize", "1000")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("order-topic"))
            .andExpect(jsonPath("$.sampledMessageCount").value(1000))
            .andExpect(jsonPath("$.duplicateRatioPercent").value(2.5))
            .andExpect(jsonPath("$.idempotencyHealthScore").value("MODERATE_RISK"));
    }
}
