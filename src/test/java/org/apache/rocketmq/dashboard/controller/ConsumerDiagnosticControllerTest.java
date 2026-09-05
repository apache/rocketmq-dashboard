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

import org.apache.rocketmq.dashboard.model.ConsumerDiagnosticReport;
import org.apache.rocketmq.dashboard.service.ConsumerDiagnosticService;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerDiagnosticControllerTest extends BaseControllerTest {

    @InjectMocks
    private ConsumerDiagnosticController consumerDiagnosticController;

    @Mock
    private ConsumerDiagnosticService consumerDiagnosticService;

    @Test
    public void testDiagnoseConsumerGroup() throws Exception {
        final String url = "/consumer-diagnostic/diagnose.query";

        ConsumerDiagnosticReport report = new ConsumerDiagnosticReport();
        report.setConsumerGroup("benchmark_consumer_group");
        report.setTopic("TopicTest");
        report.setTotalDiff(12500L);
        report.setHealthScore(65);
        report.setBottleneckLevel("HIGH");
        report.setOnlineClientCount(3);
        report.setAssignedQueueCount(16);

        ConsumerDiagnosticReport.QueueLagDetail queueDetail = new ConsumerDiagnosticReport.QueueLagDetail();
        queueDetail.setBrokerName("broker-a");
        queueDetail.setQueueId(1);
        queueDetail.setBrokerOffset(20000L);
        queueDetail.setConsumerOffset(10000L);
        queueDetail.setDiff(10000L);
        queueDetail.setClientAddress("192.168.1.100@3245");

        List<ConsumerDiagnosticReport.QueueLagDetail> queueList = new ArrayList<>();
        queueList.add(queueDetail);
        report.setBottleneckQueues(queueList);

        when(consumerDiagnosticService.diagnoseConsumerGroup(anyString(), any())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url)
                .param("consumerGroup", "benchmark_consumer_group")
                .param("topic", "TopicTest");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.consumerGroup").value("benchmark_consumer_group"))
                .andExpect(jsonPath("$.topic").value("TopicTest"))
                .andExpect(jsonPath("$.totalDiff").value(12500))
                .andExpect(jsonPath("$.healthScore").value(65))
                .andExpect(jsonPath("$.bottleneckLevel").value("HIGH"))
                .andExpect(jsonPath("$.bottleneckQueues[0].diff").value(10000));
    }
}
