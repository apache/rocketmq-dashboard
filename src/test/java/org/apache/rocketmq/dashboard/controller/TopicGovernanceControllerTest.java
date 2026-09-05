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

import org.apache.rocketmq.dashboard.model.TopicGovernanceReport;
import org.apache.rocketmq.dashboard.service.TopicGovernanceService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TopicGovernanceControllerTest extends BaseControllerTest {

    @InjectMocks
    private TopicGovernanceController topicGovernanceController;

    @Mock
    private TopicGovernanceService topicGovernanceService;

    @Test
    public void testAuditTopicLifecycle() throws Exception {
        final String url = "/topic-governance/audit.query";

        TopicGovernanceReport report = new TopicGovernanceReport();
        report.setAuditTime(System.currentTimeMillis());
        report.setTotalAuditedTopics(15);
        report.setZombieTopicCount(2);
        report.setOversizedQueueTopicCount(1);
        report.setHealthyTopicCount(12);

        TopicGovernanceReport.ZombieTopicInfo zombie = new TopicGovernanceReport.ZombieTopicInfo();
        zombie.setTopicName("ZombieTopic_A");
        zombie.setInboundMessageCount(0L);
        zombie.setSubscribedGroupCount(0);
        zombie.setRiskLevel("HIGH");
        zombie.setSuggestion("Delete or recycle unused topic to release broker metadata memory");

        List<TopicGovernanceReport.ZombieTopicInfo> zombieList = new ArrayList<>();
        zombieList.add(zombie);
        report.setZombieTopics(zombieList);

        when(topicGovernanceService.auditTopicLifecycle()).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAuditedTopics").value(15))
                .andExpect(jsonPath("$.zombieTopicCount").value(2))
                .andExpect(jsonPath("$.healthyTopicCount").value(12))
                .andExpect(jsonPath("$.zombieTopics[0].topicName").value("ZombieTopic_A"))
                .andExpect(jsonPath("$.zombieTopics[0].riskLevel").value("HIGH"));
    }
}
