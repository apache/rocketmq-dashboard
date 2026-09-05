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
import org.apache.rocketmq.common.protocol.body.TopicList;
import org.apache.rocketmq.common.protocol.route.BrokerData;
import org.apache.rocketmq.common.protocol.route.QueueData;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.dashboard.model.BrokerFailoverImpactReport;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.service.TopicService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BrokerFailoverSimulationServiceImplTest {

    @InjectMocks
    private BrokerFailoverSimulationServiceImpl brokerFailoverSimulationService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private ClusterService clusterService;

    @Mock
    private TopicService topicService;

    @Test
    public void testSimulateBrokerFailoverCritical() throws Exception {
        String brokerName = "broker-a";

        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData bd = new BrokerData();
        bd.setBrokerName(brokerName);
        bd.setCluster("DefaultCluster");
        brokerAddrTable.put(brokerName, bd);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        TopicList topicList = new TopicList();
        Set<String> topics = new HashSet<>();
        topics.add("SinglePointTopic");
        topicList.setTopicList(topics);

        when(topicService.fetchAllTopicList()).thenReturn(topicList);

        TopicRouteData routeData = new TopicRouteData();
        List<QueueData> queueDatas = new ArrayList<>();
        QueueData qd = new QueueData();
        qd.setBrokerName(brokerName);
        qd.setWriteQueueNums(4);
        qd.setReadQueueNums(4);
        queueDatas.add(qd);
        routeData.setQueueDatas(queueDatas);

        when(topicService.examineTopicRouteData("SinglePointTopic")).thenReturn(routeData);

        BrokerFailoverImpactReport report = brokerFailoverSimulationService.simulateBrokerFailover(brokerName);
        Assert.assertNotNull(report);
        Assert.assertEquals(brokerName, report.getTargetBrokerName());
        Assert.assertEquals(1, report.getTotalLossTopicCount());
        Assert.assertEquals("CRITICAL", report.getHazardLevel());
        Assert.assertTrue(report.getAvailabilityScore() < 100.0);
    }

    @Test
    public void testSimulateBrokerFailoverDegraded() throws Exception {
        String brokerName = "broker-a";

        TopicList topicList = new TopicList();
        Set<String> topics = new HashSet<>();
        topics.add("DistributedTopic");
        topicList.setTopicList(topics);

        when(topicService.fetchAllTopicList()).thenReturn(topicList);

        TopicRouteData routeData = new TopicRouteData();
        List<QueueData> queueDatas = new ArrayList<>();

        QueueData qd1 = new QueueData();
        qd1.setBrokerName(brokerName);
        qd1.setWriteQueueNums(4);
        queueDatas.add(qd1);

        QueueData qd2 = new QueueData();
        qd2.setBrokerName("broker-b");
        qd2.setWriteQueueNums(4);
        queueDatas.add(qd2);

        routeData.setQueueDatas(queueDatas);

        when(topicService.examineTopicRouteData("DistributedTopic")).thenReturn(routeData);

        BrokerFailoverImpactReport report = brokerFailoverSimulationService.simulateBrokerFailover(brokerName);
        Assert.assertNotNull(report);
        Assert.assertEquals(0, report.getTotalLossTopicCount());
        Assert.assertEquals(1, report.getDegradedTopicCount());
        Assert.assertEquals("MEDIUM", report.getHazardLevel());
    }
}
