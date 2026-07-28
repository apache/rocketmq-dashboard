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

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.QueueStatInfo;
import org.apache.rocketmq.dashboard.model.TopicConsumerInfo;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.dashboard.service.impl.ConsumerServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ConsumerControllerTest extends BaseControllerTest {

    @InjectMocks
    private ConsumerController consumerController;

    @Spy
    private ConsumerServiceImpl consumerService;

    @Mock
    private ClusterInfoService clusterInfoService;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private AdminClient adminClient;

    @Before
    public void init() throws Exception {
        // 2. mock ClusterInfo data
        ClusterInfo mockClusterInfo = getClusterInfo();
        when(clusterInfoService.get()).thenReturn(mockClusterInfo);
        super.mockRmqConfigure();
        // stub the architecture abstraction layer used by ConsumerServiceImpl
        List<ConsumerGroupInfo> consumerGroups = new ArrayList<>();
        ConsumerGroupInfo group1 = new ConsumerGroupInfo();
        group1.setConsumerGroupName("group_test");
        group1.setClusterName("DefaultCluster");
        consumerGroups.add(group1);
        ConsumerGroupInfo group2 = new ConsumerGroupInfo();
        group2.setConsumerGroupName("group_test2");
        group2.setClusterName("DefaultCluster");
        consumerGroups.add(group2);
        when(metadataProvider.listConsumerGroups(any())).thenReturn(consumerGroups);

        GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();
        groupConsumeInfo.setGroup("group_test");
        groupConsumeInfo.setConsumeType(ConsumeType.CONSUME_ACTIVELY);
        groupConsumeInfo.setMessageModel(MessageModel.CLUSTERING);
        when(adminClient.getGroupConsumeInfo(anyString())).thenReturn(groupConsumeInfo);
    }

    @Test
    public void testList() throws Exception {
        final String url = "/consumer/groupList.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].consumerGroupName").value("group_test"))
                .andExpect(jsonPath("$.data[0].clusterName").value("DefaultCluster"));
    }

    @Test
    public void testGroupQuery() throws Exception {
        final String url = "/consumer/group.query";

        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.group").value("group_test"))
                .andExpect(jsonPath("$.data.consumeType").value(ConsumeType.CONSUME_ACTIVELY.name()))
                .andExpect(jsonPath("$.data.messageModel").value(MessageModel.CLUSTERING.name()));
    }

    @Test
    public void testSkipAccumulate() throws Exception {
        final String url = "/consumer/skipAccumulate.do";
        resetOffsetOrSkipAccumulate(url, -1L);
    }

    @Test
    public void testResetOffset() throws Exception {
        final String url = "/consumer/resetOffset.do";
        resetOffsetOrSkipAccumulate(url, System.currentTimeMillis());
    }

    private void resetOffsetOrSkipAccumulate(String url, Long resetTime) throws Exception {
        RollbackStats rollbackStats = new RollbackStats();
        rollbackStats.setRollbackOffset(10L);
        rollbackStats.setQueueId(5L);
        rollbackStats.setBrokerName("broker-a");
        String groupId = "group_test";
        {
            // resetOffset is a controller-facing method resolved by the service layer;
            // stub the spy directly to verify the endpoint wiring and serialization
            Map<String, Object> groupRollbackStats = new HashMap<>();
            groupRollbackStats.put("rollbackStatsList", Lists.newArrayList(rollbackStats));
            Map<String, Object> resetResult = new HashMap<>();
            resetResult.put(groupId, groupRollbackStats);
            doReturn(resetResult).when(consumerService).resetOffset(any(ResetOffsetRequest.class));
        }
        ResetOffsetRequest request = new ResetOffsetRequest();
        request.setTopic("topic_test");
        request.setResetTime(resetTime);
        request.setConsumerGroupList(Lists.newArrayList(groupId));
        // 1、consumer not online
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data." + groupId + ".rollbackStatsList").isArray())
                .andExpect(jsonPath("$.data." + groupId + ".rollbackStatsList[0].rollbackOffset").value(10L));

        // 2、consumer not online
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk()).andExpect(jsonPath("$.data").isMap());
    }

    @Test
    public void testFetchBrokerNameList() throws Exception {
        final String url = "/consumer/fetchBrokerNameList.query";
        doReturn(new HashSet<>(Lists.newArrayList("broker-a")))
                .when(consumerService).fetchBrokerNameSetBySubscriptionGroup(anyString());
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0]").value("broker-a"));
    }

    @Test
    public void testExamineSubscriptionGroupConfig() throws Exception {
        ClusterInfo mockClusterInfo = getClusterInfo();
        {
            when(clusterInfoService.get()).thenReturn(mockClusterInfo);
        }
        final String url = "/consumer/examineSubscriptionGroupConfig.query";
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("group_test");
        doReturn(Lists.newArrayList(config))
                .when(consumerService).examineSubscriptionGroupConfig(anyString());
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    public void testDelete() throws Exception {
        final String url = "/consumer/deleteSubGroup.do";
        ClusterInfo mockClusterInfo = getClusterInfo();
        {
            when(clusterInfoService.get()).thenReturn(mockClusterInfo);
            doReturn(true).when(consumerService).deleteSubGroup(any(DeleteSubGroupRequest.class));
        }
        DeleteSubGroupRequest request = new DeleteSubGroupRequest();
        request.setBrokerNameList(Lists.newArrayList("broker-a"));
        request.setGroupName("group_test");
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testCreateOrUpdate() throws Exception {
        final String url = "/consumer/createOrUpdate.do";
        // 1、clusterName and brokerName all blank
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        ConsumerConfigInfo consumerConfigInfo = new ConsumerConfigInfo();
        requestBuilder.content(JSON.toJSONString(consumerConfigInfo));
        perform = mockMvc.perform(requestBuilder);
        performErrorExpect(perform);

        List<String> clusterNameList = Lists.newArrayList("DefaultCluster");
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("group_test");
        consumerConfigInfo.setClusterNameList(clusterNameList);
        consumerConfigInfo.setSubscriptionGroupConfig(config);
        // 2、create consumer
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(consumerConfigInfo));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }


    @Test
    public void testQueryConsumerByTopic() throws Exception {
        // Prepare test data
        List<TopicConsumerInfo> topicConsumerInfoList = new ArrayList<>();
        TopicConsumerInfo info = new TopicConsumerInfo("test-topic");

        // Add queue stats
        List<QueueStatInfo> queueStatInfoList = new ArrayList<>();
        QueueStatInfo queueStat1 = new QueueStatInfo();
        queueStat1.setBrokerName("broker-0");
        queueStat1.setQueueId(0);
        info.appendQueueStatInfo(queueStat1);

        QueueStatInfo queueStat2 = new QueueStatInfo();
        queueStat2.setBrokerName("broker-1");
        queueStat2.setQueueId(1);
        info.appendQueueStatInfo(queueStat2);

        topicConsumerInfoList.add(info);

        // Mock the service method directly
        doReturn(topicConsumerInfoList).when(consumerService).queryConsumeStatsListByGroupName(anyString(), any());

        // Perform request and verify response
        final String url = "/consumer/queryTopicByConsumer.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");

        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data[0].topic").value("test-topic"))
                .andExpect(jsonPath("$.data[0].queueStatInfoList", hasSize(2)))
                .andExpect(jsonPath("$.data[0].queueStatInfoList[0].brokerName").value("broker-0"))
                .andExpect(jsonPath("$.data[0].queueStatInfoList[1].brokerName").value("broker-1"));
    }

    @Test
    public void testConsumerConnection() throws Exception {
        // Prepare test data
        ConsumerConnection connection = new ConsumerConnection();
        connection.setConsumeType(ConsumeType.CONSUME_ACTIVELY);
        connection.setMessageModel(MessageModel.CLUSTERING);

        // Setup connection set
        HashSet<Connection> connections = new HashSet<>();
        Connection conn = new Connection();
        conn.setClientAddr("127.0.0.1");
        conn.setClientId("clientId");
        connections.add(conn);
        connection.setConnectionSet(connections);

        // Mock the service method
        doReturn(connection).when(consumerService).getConsumerConnection(anyString(), any());

        // Perform request and verify response
        final String url = "/consumer/consumerConnection.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");

        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.data.consumeType").value("CONSUME_ACTIVELY"))
                .andExpect(jsonPath("$.data.messageModel").value("CLUSTERING"))
                .andExpect(jsonPath("$.data.connectionSet[0].clientAddr").value("127.0.0.1"));
    }

    @Test
    public void testGetConsumerRunningInfo() throws Exception {
        final String url = "/consumer/consumerRunningInfo.query";
        ConsumerRunningInfo runningInfo = MockObjectUtil.createConsumerRunningInfo();
        doReturn(runningInfo).when(consumerService)
                .getConsumerRunningInfo(anyString(), anyString(), anyBoolean());
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");
        requestBuilder.param("clientId", "group_test");
        requestBuilder.param("jstack", "true");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jstack").value("test"));
    }

    @Override
    protected Object getTestController() {
        return consumerController;
    }
}
