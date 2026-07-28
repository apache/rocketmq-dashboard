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
package org.apache.rocketmq.dashboard.task;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.dashboard.service.impl.DashboardCollectServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class HotTopicCollectTaskTest {

    private static final String TOPIC = "topic_test";

    @Mock
    private MQAdminExt mqAdminExt;

    private DashboardCollectServiceImpl dashboardCollectService;

    private HotTopicCollectTask task;

    @Before
    public void setUp() {
        dashboardCollectService = new DashboardCollectServiceImpl();
        task = new HotTopicCollectTask(mqAdminExt, dashboardCollectService);
    }

    private TopicList buildTopicList() {
        TopicList topicList = new TopicList();
        Set<String> topicSet = new HashSet<>();
        topicSet.add(TOPIC);
        topicSet.add("rmq_sys_xxx");
        topicSet.add("%RETRY%group_test");
        topicSet.add("%DLQ%group_test");
        topicList.setTopicList(topicSet);
        return topicList;
    }

    @Test
    public void testRunCollectSuccess() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList());
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString()))
                .thenReturn(MockObjectUtil.createBrokerStatsData());

        task.run();

        // only topic_test should be collected, filtered topics skipped
        Assert.assertEquals(1, dashboardCollectService.getHotTopicMap().asMap().size());
        List<String> list = dashboardCollectService.getHotTopicMap().asMap().get(TOPIC);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        // timestamp,putTps,putNumsToday,putSizeTps,putSizeToday
        Assert.assertEquals(5, list.get(0).split(",").length);
    }

    @Test
    public void testRunStatsNotAvailable() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList());
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        when(mqAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("stats not exist"));

        task.run();

        // all stats are zero -> no hot topic stored
        Assert.assertEquals(0, dashboardCollectService.getHotTopicMap().asMap().size());
    }

    @Test
    public void testRunNoMasterBroker() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList());
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        BrokerData brokerData = clusterInfo.getBrokerAddrTable().get("broker-a");
        HashMap<Long, String> brokerAddrs = new HashMap<>();
        brokerAddrs.put(MixAll.MASTER_ID + 1, "127.0.0.1:10912");
        brokerData.setBrokerAddrs(brokerAddrs);
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        task.run();

        Assert.assertEquals(0, dashboardCollectService.getHotTopicMap().asMap().size());
    }

    @Test
    public void testRunFetchAllTopicListFailed() throws Exception {
        when(mqAdminExt.fetchAllTopicList())
                .thenThrow(new RuntimeException("fetchAllTopicList exception"));

        // exception should be swallowed
        task.run();

        Assert.assertEquals(0, dashboardCollectService.getHotTopicMap().asMap().size());
    }
}
