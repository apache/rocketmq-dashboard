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

import org.apache.rocketmq.dashboard.model.BrokerFailoverImpactReport;
import org.apache.rocketmq.dashboard.service.BrokerFailoverSimulationService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class BrokerFailoverSimulationControllerTest extends BaseControllerTest {

    @InjectMocks
    private BrokerFailoverSimulationController brokerFailoverSimulationController;

    @Mock
    private BrokerFailoverSimulationService brokerFailoverSimulationService;

    @Test
    public void testSimulateBrokerDown() throws Exception {
        final String url = "/cluster/simulateBrokerDown.query";

        BrokerFailoverImpactReport report = new BrokerFailoverImpactReport();
        report.setTargetBrokerName("broker-a");
        report.setClusterName("DefaultCluster");
        report.setSimulationTime(System.currentTimeMillis());
        report.setTotalClusterBrokers(2);
        report.setTotalClusterTopics(10);
        report.setImpactedTopicCount(1);
        report.setTotalLossTopicCount(1);
        report.setAvailabilityScore(75.0);
        report.setHazardLevel("CRITICAL");
        report.setActionPlan("HALT MAINTENANCE: 1 single-point topic will completely fail.");

        BrokerFailoverImpactReport.ImpactedTopicDetail detail =
                new BrokerFailoverImpactReport.ImpactedTopicDetail();
        detail.setTopic("SpofTopic");
        detail.setOriginalQueueCount(4);
        detail.setLostQueueCount(4);
        detail.setRemainingQueueCount(0);
        detail.setCapacityLossRatio(100.0);
        detail.setCompleteLoss(true);

        List<BrokerFailoverImpactReport.ImpactedTopicDetail> list = new ArrayList<>();
        list.add(detail);
        report.setImpactedTopics(list);

        when(brokerFailoverSimulationService.simulateBrokerFailover(anyString())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url).param("brokerName", "broker-a");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.targetBrokerName").value("broker-a"))
                .andExpect(jsonPath("$.totalLossTopicCount").value(1))
                .andExpect(jsonPath("$.availabilityScore").value(75.0))
                .andExpect(jsonPath("$.hazardLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.impactedTopics[0].topic").value("SpofTopic"));
    }
}
