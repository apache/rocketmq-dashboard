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

import org.apache.rocketmq.dashboard.model.TopicCrossClusterSyncReport;
import org.apache.rocketmq.dashboard.service.TopicCrossClusterSyncService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TopicCrossClusterSyncControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TopicCrossClusterSyncService topicCrossClusterSyncService;

    @InjectMocks
    private TopicCrossClusterSyncController topicCrossClusterSyncController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(topicCrossClusterSyncController).build();
    }

    @Test
    public void testCompareTopicConfig() throws Exception {
        TopicCrossClusterSyncReport report = new TopicCrossClusterSyncReport();
        report.setTopic("order-topic");
        report.setSourceCluster("c-east");
        report.setTargetCluster("c-west");
        report.setConfigurationInSync(false);
        report.setTotalDiscrepancies(2);

        when(topicCrossClusterSyncService.compareTopicAcrossClusters(anyString(), anyString(), anyString()))
            .thenReturn(report);

        mockMvc.perform(get("/topic/crossClusterSync/compare.query")
            .param("topic", "order-topic")
            .param("sourceCluster", "c-east")
            .param("targetCluster", "c-west")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("order-topic"))
            .andExpect(jsonPath("$.totalDiscrepancies").value(2))
            .andExpect(jsonPath("$.configurationInSync").value(false));
    }

    @Test
    public void testSyncTopicConfig() throws Exception {
        TopicCrossClusterSyncReport report = new TopicCrossClusterSyncReport();
        report.setTopic("order-topic");
        report.setConfigurationInSync(true);
        report.setTotalDiscrepancies(0);
        report.setSyncStatus("SYNCHRONIZED");

        when(topicCrossClusterSyncService.synchronizeTopicConfig(anyString(), anyString(), anyString()))
            .thenReturn(report);

        mockMvc.perform(post("/topic/crossClusterSync/sync.do")
            .param("topic", "order-topic")
            .param("sourceCluster", "c-east")
            .param("targetCluster", "c-west")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.topic").value("order-topic"))
            .andExpect(jsonPath("$.configurationInSync").value(true))
            .andExpect(jsonPath("$.syncStatus").value("SYNCHRONIZED"));
    }
}
