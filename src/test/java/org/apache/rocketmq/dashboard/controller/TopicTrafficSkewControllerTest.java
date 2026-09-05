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

import org.apache.rocketmq.dashboard.model.TopicTrafficSkewReport;
import org.apache.rocketmq.dashboard.service.TopicTrafficSkewService;
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

public class TopicTrafficSkewControllerTest extends BaseControllerTest {

    @InjectMocks
    private TopicTrafficSkewController topicTrafficSkewController;

    @Mock
    private TopicTrafficSkewService topicTrafficSkewService;

    @Test
    public void testInspectSkew() throws Exception {
        final String url = "/topic/skewInspection.query";

        TopicTrafficSkewReport report = new TopicTrafficSkewReport();
        report.setTopic("TopicTest");
        report.setInspectTime(System.currentTimeMillis());
        report.setTotalQueues(4);
        report.setTotalMessages(20000L);
        report.setGiniCoefficient(0.62);
        report.setSkewLevel("SEVERE_SKEW");
        report.setSuggestion("Severe traffic skew detected");

        TopicTrafficSkewReport.QueueSkewDetail queueDetail = new TopicTrafficSkewReport.QueueSkewDetail();
        queueDetail.setBrokerName("broker-a");
        queueDetail.setQueueId(0);
        queueDetail.setMinOffset(0L);
        queueDetail.setMaxOffset(16000L);
        queueDetail.setMessageCount(16000L);
        queueDetail.setRatioPercent(80.0);
        queueDetail.setHotspot(true);

        List<TopicTrafficSkewReport.QueueSkewDetail> list = new ArrayList<>();
        list.add(queueDetail);
        report.setQueueDetails(list);

        when(topicTrafficSkewService.inspectTopicTrafficSkew(anyString())).thenReturn(report);

        requestBuilder = MockMvcRequestBuilders.get(url).param("topic", "TopicTest");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("TopicTest"))
                .andExpect(jsonPath("$.totalQueues").value(4))
                .andExpect(jsonPath("$.totalMessages").value(20000))
                .andExpect(jsonPath("$.skewLevel").value("SEVERE_SKEW"))
                .andExpect(jsonPath("$.queueDetails[0].hotspot").value(true));
    }
}
