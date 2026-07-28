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

import com.google.common.collect.Sets;
import org.apache.rocketmq.common.stats.Stats;
import org.apache.rocketmq.dashboard.service.impl.DashboardCollectServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class StorageLatencyCollectTaskTest {

    private static final String TOPIC = "topic_test";

    @Mock
    private MQAdminExt mqAdminExt;

    private DashboardCollectServiceImpl dashboardCollectService;

    private StorageLatencyCollectTask task;

    @Before
    public void setUp() {
        dashboardCollectService = new DashboardCollectServiceImpl();
        task = new StorageLatencyCollectTask(TOPIC, mqAdminExt, dashboardCollectService);
    }

    @Test
    public void testRunCollectSuccess() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo(TOPIC)).thenReturn(MockObjectUtil.createTopicRouteData());
        GroupList groupList = new GroupList();
        groupList.setGroupList(Sets.newHashSet("group_test"));
        when(mqAdminExt.queryTopicConsumeByWho(TOPIC)).thenReturn(groupList);
        when(mqAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString()))
                .thenReturn(MockObjectUtil.createBrokerStatsData());

        task.run();

        List<String> list = dashboardCollectService.getStorageLatencyMap().asMap().get(TOPIC);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        // timestamp,putLatencyAvg,getLatencyAvg,fallSizeTotal,fallTimeAvg
        Assert.assertEquals(5, list.get(0).split(",").length);
    }

    @Test
    public void testRunStatsNotAvailable() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo(TOPIC)).thenReturn(MockObjectUtil.createTopicRouteData());
        GroupList groupList = new GroupList();
        groupList.setGroupList(Sets.newHashSet("group_test"));
        when(mqAdminExt.queryTopicConsumeByWho(TOPIC)).thenReturn(groupList);
        when(mqAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("stats not exist"));

        task.run();

        // still stores zero values even if all stats are unavailable
        List<String> list = dashboardCollectService.getStorageLatencyMap().asMap().get(TOPIC);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        String[] parts = list.get(0).split(",");
        Assert.assertEquals("0.00000", parts[1]);
        Assert.assertEquals("0", parts[3]);
    }

    @Test
    public void testRunPartialStatsAvailable() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo(TOPIC)).thenReturn(MockObjectUtil.createTopicRouteData());
        GroupList groupList = new GroupList();
        groupList.setGroupList(Sets.newHashSet("group_test"));
        when(mqAdminExt.queryTopicConsumeByWho(TOPIC)).thenReturn(groupList);
        when(mqAdminExt.viewBrokerStatsData(anyString(), eq(Stats.TOPIC_PUT_LATENCY), anyString()))
                .thenReturn(MockObjectUtil.createBrokerStatsData());
        when(mqAdminExt.viewBrokerStatsData(anyString(), eq(Stats.GROUP_GET_LATENCY), anyString()))
                .thenThrow(new RuntimeException("GROUP_GET_LATENCY not exist"));
        when(mqAdminExt.viewBrokerStatsData(anyString(), eq(Stats.GROUP_GET_FALL_SIZE), anyString()))
                .thenReturn(MockObjectUtil.createBrokerStatsData());
        when(mqAdminExt.viewBrokerStatsData(anyString(), eq(Stats.GROUP_GET_FALL_TIME), anyString()))
                .thenReturn(MockObjectUtil.createBrokerStatsData());

        task.run();

        List<String> list = dashboardCollectService.getStorageLatencyMap().asMap().get(TOPIC);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
        String[] parts = list.get(0).split(",");
        // getLatencyAvg is zero because GROUP_GET_LATENCY is missing
        Assert.assertEquals("0.00000", parts[2]);
    }

    @Test
    public void testRunNoMasterBroker() throws Exception {
        TopicRouteData topicRouteData = MockObjectUtil.createTopicRouteData();
        // remove master address so no stats are collected
        topicRouteData.getBrokerDatas().get(0).setBrokerAddrs(new HashMap<>());
        when(mqAdminExt.examineTopicRouteInfo(TOPIC)).thenReturn(topicRouteData);
        GroupList groupList = new GroupList();
        groupList.setGroupList(Sets.newHashSet("group_test"));
        when(mqAdminExt.queryTopicConsumeByWho(TOPIC)).thenReturn(groupList);

        task.run();

        List<String> list = dashboardCollectService.getStorageLatencyMap().asMap().get(TOPIC);
        Assert.assertNotNull(list);
        Assert.assertEquals(1, list.size());
    }

    @Test
    public void testRunExamineTopicRouteInfoFailed() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo(TOPIC))
                .thenThrow(new RuntimeException("examineTopicRouteInfo exception"));

        // exception should be swallowed
        task.run();

        Assert.assertNull(dashboardCollectService.getStorageLatencyMap().asMap().get(TOPIC));
    }
}
