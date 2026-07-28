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
package org.apache.rocketmq.dashboard.architecture.impl;

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemotingAdminClient} delegation behavior.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class RemotingAdminClientTest {

    @Mock
    private MQAdminExt mqAdminExt;

    private RemotingAdminClient client;

    @Before
    public void setUp() {
        client = new RemotingAdminClient(mqAdminExt);
    }

    private TopicList buildTopicList(String... topics) {
        TopicList topicList = new TopicList();
        topicList.setTopicList(new HashSet<>(Arrays.asList(topics)));
        return topicList;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullMqAdminExtThrows() {
        new RemotingAdminClient(null);
    }

    @Test
    public void testGetClientType() {
        assertEquals(ClusterAccessType.V4_NAMESRV, client.getClientType());
    }

    @Test
    public void testGetClusterInfo() throws Exception {
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        assertEquals(clusterInfo, client.getClusterInfo());
    }

    @Test
    public void testGetBrokerRuntimeStats() throws Exception {
        KVTable kvTable = new KVTable();
        when(mqAdminExt.fetchBrokerRuntimeStats(anyString())).thenReturn(kvTable);
        assertEquals(kvTable, client.getBrokerRuntimeStats("127.0.0.1:10911"));
    }

    @Test
    public void testUpdateBrokerConfig() throws Exception {
        Properties properties = new Properties();
        client.updateBrokerConfig("127.0.0.1:10911", properties);
        verify(mqAdminExt).updateBrokerConfig("127.0.0.1:10911", properties);
    }

    @Test
    public void testGetTopicList() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA", "topicB"));
        List<String> topics = client.getTopicList();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("topicA"));
        assertTrue(topics.contains("topicB"));
    }

    @Test
    public void testGetTopicRoute() throws Exception {
        TopicRouteData route = MockObjectUtil.createTopicRouteData();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(route);
        assertEquals(route, client.getTopicRoute("topicA"));
    }

    @Test
    public void testGetTopicStats() throws Exception {
        TopicStatsTable stats = MockObjectUtil.createTopicStatsTable();
        when(mqAdminExt.examineTopicStats("topicA")).thenReturn(stats);
        assertEquals(stats, client.getTopicStats("topicA"));
    }

    @Test
    public void testCreateOrUpdateTopicOnMasterBroker() throws Exception {
        TopicRouteData route = MockObjectUtil.createTopicRouteData();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(route);
        TopicConfig config = new TopicConfig("topicA");
        client.createOrUpdateTopic("topicA", config);
        verify(mqAdminExt).createAndUpdateTopicConfig("127.0.0.1:10911", config);
    }

    @Test
    public void testCreateOrUpdateTopicSkipsSlaveBroker() throws Exception {
        TopicRouteData route = MockObjectUtil.createTopicRouteData();
        BrokerData brokerData = route.getBrokerDatas().get(0);
        HashMap<Long, String> slaveOnly = new HashMap<>();
        slaveOnly.put(1L, "127.0.0.1:10912");
        brokerData.setBrokerAddrs(slaveOnly);
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(route);
        client.createOrUpdateTopic("topicA", new TopicConfig("topicA"));
        verify(mqAdminExt, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
    }

    @Test
    public void testCreateOrUpdateTopicNullRoute() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(null);
        client.createOrUpdateTopic("topicA", new TopicConfig("topicA"));
        verify(mqAdminExt, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
    }

    @Test
    public void testCreateOrUpdateTopicSwallowsBrokerException() throws Exception {
        TopicRouteData route = MockObjectUtil.createTopicRouteData();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(route);
        doThrow(new RuntimeException("broker down"))
            .when(mqAdminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        // Should not propagate the exception
        client.createOrUpdateTopic("topicA", new TopicConfig("topicA"));
        verify(mqAdminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
    }

    @Test
    public void testDeleteTopic() throws Exception {
        client.deleteTopic("topicA", "DefaultCluster");
        Set<String> clusters = new HashSet<>(Collections.singletonList("DefaultCluster"));
        verify(mqAdminExt).deleteTopicInBroker(clusters, "topicA");
        verify(mqAdminExt).deleteTopicInNameServer(clusters, "topicA");
    }

    @Test
    public void testGetTopicListFromBroker() throws Exception {
        TopicList topicList = buildTopicList("topicA");
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicList);
        assertEquals(topicList, client.getTopicListFromBroker("127.0.0.1:10911"));
    }

    @Test
    public void testGetConsumerGroupList() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA", "%RETRY%groupA"));
        List<String> groups = client.getConsumerGroupList();
        // Current implementation returns the raw topic list
        assertEquals(2, groups.size());
    }

    @Test
    public void testGetConsumerConnection() throws Exception {
        ConsumerConnection connection = MockObjectUtil.createConsumerConnection();
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(connection);
        assertEquals(connection, client.getConsumerConnection("groupA"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetGroupConsumeInfoUnsupported() throws Exception {
        client.getGroupConsumeInfo("groupA");
    }

    @Test
    public void testResetConsumeOffsetParamOrder() throws Exception {
        client.resetConsumeOffset("groupA", "topicA", 123L, true);
        // Note: topic goes first in the underlying API
        verify(mqAdminExt).resetOffsetByTimestamp("topicA", "groupA", 123L, true);
    }

    @Test
    public void testCreateOrUpdateConsumerGroup() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("groupA");
        client.createOrUpdateConsumerGroup("groupA", config);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig("127.0.0.1:10911", config);
    }

    @Test
    public void testCreateOrUpdateConsumerGroupSwallowsBrokerException() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());
        doThrow(new RuntimeException("broker down"))
            .when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any(SubscriptionGroupConfig.class));
        client.createOrUpdateConsumerGroup("groupA", new SubscriptionGroupConfig());
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testCreateOrUpdateConsumerGroupNullRoute() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(null);
        client.createOrUpdateConsumerGroup("groupA", new SubscriptionGroupConfig());
        verify(mqAdminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testDeleteConsumerGroup() throws Exception {
        client.deleteConsumerGroup("groupA", "127.0.0.1:10911");
        verify(mqAdminExt).deleteSubscriptionGroup("127.0.0.1:10911", "groupA");
    }

    @Test
    public void testGetProducerConnection() throws Exception {
        ProducerConnection connection = new ProducerConnection();
        when(mqAdminExt.examineProducerConnectionInfo("pg", "topicA")).thenReturn(connection);
        assertEquals(connection, client.getProducerConnection("pg", "topicA"));
    }

    @Test
    public void testQueryMessage() throws Exception {
        QueryResult queryResult = mock(QueryResult.class);
        when(mqAdminExt.queryMessage("topicA", "key", 32, 1L, 2L)).thenReturn(queryResult);
        assertEquals(queryResult, client.queryMessage("topicA", "key", 1L, 2L, 32));
    }

    @Test
    public void testViewMessage() throws Exception {
        MessageExt messageExt = new MessageExt();
        when(mqAdminExt.viewMessage("topicA", "msgId")).thenReturn(messageExt);
        assertEquals(messageExt, client.viewMessage("topicA", "msgId"));
    }

    @Test
    public void testConsumeMessageDirectly() throws Exception {
        ConsumeMessageDirectlyResult result = new ConsumeMessageDirectlyResult();
        when(mqAdminExt.consumeMessageDirectly("groupA", null, "topicA", "msgId")).thenReturn(result);
        assertEquals(result, client.consumeMessageDirectly("groupA", "topicA", "msgId"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testReplayMessageUnsupported() throws Exception {
        client.replayMessage("groupA", "topicA", "msgId");
    }

    @Test
    public void testGetNameServerConfigMergesProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("listenPort", "9876");
        props.setProperty("rocketmqHome", "/opt/rocketmq");
        Map<String, Properties> configMap = new HashMap<>();
        configMap.put("127.0.0.1:9876", props);
        when(mqAdminExt.getNameServerConfig(anyList())).thenReturn(configMap);

        KVTable kvTable = client.getNameServerConfig("127.0.0.1:9876");
        assertNotNull(kvTable);
        assertEquals("9876", kvTable.getTable().get("listenPort"));
        assertEquals("/opt/rocketmq", kvTable.getTable().get("rocketmqHome"));
    }

    @Test
    public void testGetNameServerConfigNullMap() throws Exception {
        when(mqAdminExt.getNameServerConfig(anyList())).thenReturn(null);
        KVTable kvTable = client.getNameServerConfig("127.0.0.1:9876");
        assertNotNull(kvTable);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlListUnsupported() throws Exception {
        client.getAccessControlList("127.0.0.1:10911");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateAccessControlListUnsupported() throws Exception {
        client.updateAccessControlList("127.0.0.1:10911", null);
    }

    @Test
    public void testShutdownDelegates() {
        client.shutdown();
        verify(mqAdminExt, times(1)).shutdown();
    }
}
