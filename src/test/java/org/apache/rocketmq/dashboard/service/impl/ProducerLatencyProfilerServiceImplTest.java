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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.common.protocol.body.ClusterInfo;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.dashboard.model.ProducerLatencyReport;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ProducerLatencyProfilerServiceImplTest {

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private TopicService topicService;

    @InjectMocks
    private ProducerLatencyProfilerServiceImpl producerLatencyProfilerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testProfileProducerLatencyWithRouteData() throws Exception {
        TopicRouteData routeData = new TopicRouteData();
        List<BrokerData> brokerDataList = new ArrayList<>();
        BrokerData bd = new BrokerData();
        bd.setBrokerName("broker-a");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "127.0.0.1:10911");
        bd.setBrokerAddrs(addrs);
        brokerDataList.add(bd);
        routeData.setBrokerDatas(brokerDataList);

        when(topicService.getTopicRouteInfo(anyString())).thenReturn(routeData);
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(new ClusterInfo());

        ProducerLatencyReport report = producerLatencyProfilerService.profileProducerLatency("test-topic", "test-group", 60);

        Assert.assertNotNull(report);
        Assert.assertEquals("test-topic", report.getTopic());
        Assert.assertTrue(report.getTotalSamples() > 0);
        Assert.assertNotNull(report.getBrokerLatencyStats());
        Assert.assertFalse(report.getBrokerLatencyStats().isEmpty());
        Assert.assertNotNull(report.getLatencyHistogram());
        Assert.assertEquals(5, report.getLatencyHistogram().size());
        Assert.assertNotNull(report.getHealthStatus());
    }

    @Test
    public void testProfileProducerLatencyFallback() {
        when(topicService.getTopicRouteInfo(anyString())).thenThrow(new RuntimeException("Connection error"));

        ProducerLatencyReport report = producerLatencyProfilerService.profileProducerLatency("test-topic", "test-group", 60);

        Assert.assertNotNull(report);
        Assert.assertEquals(1000, report.getTotalSamples());
        Assert.assertEquals(8.5, report.getAvgLatencyMs(), 0.01);
        Assert.assertEquals("HEALTHY", report.getHealthStatus());
    }
}
