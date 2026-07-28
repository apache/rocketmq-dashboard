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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Additional unit tests for {@link V4MetadataProvider} covering topic CRUD,
 * consumer group CRUD, subscriptions, client listing and the route-based
 * message query path (complements V4MetadataProviderTest which focuses on
 * ACL and admin-query paths).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class V4MetadataProviderTopicClientTest {

    @Mock
    private org.apache.rocketmq.tools.admin.MQAdminExt mqAdminExt;

    @Mock
    private AutoCloseConsumerWrapper consumerWrapper;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private DefaultMQPullConsumer pullConsumer;

    private V4MetadataProvider provider;

    @Before
    public void setUp() {
        provider = new V4MetadataProvider(mqAdminExt);
    }

    // ==================== Helpers ====================

    private org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo() {
        org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo =
            new org.apache.rocketmq.remoting.protocol.body.ClusterInfo();
        HashMap<String, java.util.Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", new HashSet<>(Collections.singletonList("broker-a")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        HashMap<Long, String> brokerAddrs = new HashMap<>();
        brokerAddrs.put(0L, "127.0.0.1:10911");
        brokerAddrTable.put("broker-a", new BrokerData("DefaultCluster", "broker-a", brokerAddrs));
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    private TopicList topicListOf(String... names) {
        TopicList topicList = new TopicList();
        topicList.setTopicList(new HashSet<>(Arrays.asList(names)));
        return topicList;
    }

    private TopicRouteData routeData(int readQueues, int writeQueues) {
        TopicRouteData routeData = new TopicRouteData();
        QueueData queueData = new QueueData();
        queueData.setBrokerName("broker-a");
        queueData.setReadQueueNums(readQueues);
        queueData.setWriteQueueNums(writeQueues);
        routeData.setQueueDatas(new ArrayList<>(Collections.singletonList(queueData)));
        HashMap<Long, String> brokerAddrs = new HashMap<>();
        brokerAddrs.put(0L, "127.0.0.1:10911");
        BrokerData brokerData = new BrokerData("DefaultCluster", "broker-a", brokerAddrs);
        routeData.setBrokerDatas(new ArrayList<>(Collections.singletonList(brokerData)));
        return routeData;
    }

    private MessageExt message(String topic, long bornTimestamp) {
        MessageExt msg = new MessageExt();
        msg.setTopic(topic);
        msg.setBody("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        msg.setBornTimestamp(bornTimestamp);
        msg.setStoreTimestamp(bornTimestamp + 5);
        msg.setKeys("key");
        return msg;
    }

    // ==================== Topic operations ====================

    @Test
    public void testListTopicsSkipsRetryTopicsAndFillsQueueNums() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA", "%RETRY%groupA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 2));

        List<TopicInfo> topics = provider.listTopics(Optional.empty());
        assertEquals(1, topics.size());
        assertEquals("topicA", topics.get(0).getTopicName());
        assertEquals("DEFAULT", topics.get(0).getNamespace());
        assertEquals(Integer.valueOf(4), topics.get(0).getReadQueueNums());
        assertEquals(Integer.valueOf(2), topics.get(0).getWriteQueueNums());
    }

    @Test
    public void testListTopicsRouteFailureIsTolerated() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenThrow(new RuntimeException("no route"));

        List<TopicInfo> topics = provider.listTopics(Optional.empty());
        assertEquals(1, topics.size());
    }

    @Test
    public void testListTopicsNonDefaultNamespaceReturnsEmpty() throws Exception {
        assertTrue(provider.listTopics(Optional.of("other-ns")).isEmpty());
        verifyNoInteractions(mqAdminExt);
    }

    @Test
    public void testGetTopicFound() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));

        Optional<TopicInfo> topic = provider.getTopic("topicA", Optional.empty());
        assertTrue(topic.isPresent());
        assertEquals("topicA", topic.get().getTopicName());
    }

    @Test
    public void testGetTopicMissingRouteReturnsEmpty() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("ghost")).thenThrow(new RuntimeException("not found"));

        assertFalse(provider.getTopic("ghost", Optional.empty()).isPresent());
    }

    @Test
    public void testGetTopicNonDefaultNamespaceReturnsEmpty() throws Exception {
        assertFalse(provider.getTopic("topicA", Optional.of("other-ns")).isPresent());
        verifyNoInteractions(mqAdminExt);
    }

    @Test
    public void testCreateTopicOnAllMasterBrokers() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("newTopic");
        topic.setReadQueueNums(4);
        topic.setWriteQueueNums(4);

        provider.createTopic(topic);
        verify(mqAdminExt).createAndUpdateTopicConfig(eq("127.0.0.1:10911"), any(TopicConfig.class));
    }

    @Test
    public void testCreateTopicBrokerFailureIsTolerated() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        doThrow(new RuntimeException("broker down"))
            .when(mqAdminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("newTopic");
        topic.setReadQueueNums(4);
        topic.setWriteQueueNums(4);

        // Per-broker failures are logged, not propagated
        provider.createTopic(topic);
    }

    @Test
    public void testUpdateTopicDelegatesToCreate() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("topicA");
        topic.setReadQueueNums(4);
        topic.setWriteQueueNums(4);

        provider.updateTopic(topic);
        verify(mqAdminExt).createAndUpdateTopicConfig(eq("127.0.0.1:10911"), any(TopicConfig.class));
    }

    @Test
    public void testDeleteTopicUsesClustersFromRoute() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));

        provider.deleteTopic("topicA", Optional.empty());
        verify(mqAdminExt).deleteTopic("topicA", "DefaultCluster");
    }

    @Test
    public void testDeleteTopicNonDefaultNamespaceIsNoOp() throws Exception {
        provider.deleteTopic("topicA", Optional.of("other-ns"));
        verifyNoInteractions(mqAdminExt);
    }

    // ==================== Consumer group operations ====================

    @Test
    public void testListConsumerGroupsDerivedFromRetryTopics() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA", "%RETRY%groupA", "%RETRY%groupB"));

        List<ConsumerGroupInfo> groups = provider.listConsumerGroups(Optional.empty());
        assertEquals(2, groups.size());
        assertTrue(groups.stream().anyMatch(g -> "groupA".equals(g.getConsumerGroupName())));
        assertTrue(groups.stream().anyMatch(g -> "groupB".equals(g.getConsumerGroupName())));
    }

    @Test
    public void testListConsumerGroupsNonDefaultNamespaceReturnsEmpty() throws Exception {
        assertTrue(provider.listConsumerGroups(Optional.of("other-ns")).isEmpty());
    }

    @Test
    public void testGetConsumerGroupFoundAndMissing() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA"));

        assertTrue(provider.getConsumerGroup("groupA", Optional.empty()).isPresent());
        assertFalse(provider.getConsumerGroup("missing", Optional.empty()).isPresent());
        assertFalse(provider.getConsumerGroup("groupA", Optional.of("other-ns")).isPresent());
    }

    @Test
    public void testCreateConsumerGroupOnMasterBrokers() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        provider.createConsumerGroup(group);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig(eq("127.0.0.1:10911"), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testCreateConsumerGroupBrokerFailureIsTolerated() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        doThrow(new RuntimeException("broker down"))
            .when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any(SubscriptionGroupConfig.class));
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        provider.createConsumerGroup(group);
    }

    @Test
    public void testUpdateConsumerGroupDelegatesToCreate() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        provider.updateConsumerGroup(group);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig(eq("127.0.0.1:10911"), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testDeleteConsumerGroupRemovesConfigAndRetryTopic() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(mqAdminExt.examineTopicRouteInfo("%RETRY%groupA")).thenReturn(routeData(1, 1));

        provider.deleteConsumerGroup("groupA", Optional.empty());
        verify(mqAdminExt).deleteSubscriptionGroup("127.0.0.1:10911", "groupA", true);
        verify(mqAdminExt).deleteTopic("%RETRY%groupA", "DefaultCluster");
    }

    @Test
    public void testDeleteConsumerGroupMissingRetryTopicIsTolerated() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(mqAdminExt.examineTopicRouteInfo("%RETRY%groupA")).thenThrow(new RuntimeException("no route"));

        provider.deleteConsumerGroup("groupA", Optional.empty());
        verify(mqAdminExt).deleteSubscriptionGroup("127.0.0.1:10911", "groupA", true);
        verify(mqAdminExt, never()).deleteTopic(anyString(), anyString());
    }

    @Test
    public void testDeleteConsumerGroupNonDefaultNamespaceIsNoOp() throws Exception {
        provider.deleteConsumerGroup("groupA", Optional.of("other-ns"));
        verifyNoInteractions(mqAdminExt);
    }

    // ==================== Subscriptions ====================

    @Test
    public void testListSubscriptionsFromConsumerConnection() throws Exception {
        ConsumerConnection connection = new ConsumerConnection();
        ConcurrentHashMap<String, SubscriptionData> table = new ConcurrentHashMap<>();
        SubscriptionData withExpr = new SubscriptionData();
        withExpr.setTopic("topicA");
        withExpr.setSubString("tagA");
        table.put("topicA", withExpr);
        SubscriptionData withoutExpr = new SubscriptionData();
        withoutExpr.setTopic("topicB");
        withoutExpr.setSubString(null);
        table.put("topicB", withoutExpr);
        connection.setSubscriptionTable(table);
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(connection);

        List<SubscriptionInfo> subscriptions = provider.listSubscriptions("groupA");
        assertEquals(2, subscriptions.size());
        for (SubscriptionInfo info : subscriptions) {
            if ("topicA".equals(info.getTopic())) {
                assertEquals("tagA", info.getSubExpression());
            } else {
                // null sub expression falls back to "*"
                assertEquals("*", info.getSubExpression());
            }
        }
    }

    @Test
    public void testListSubscriptionsFailureReturnsEmpty() throws Exception {
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenThrow(new RuntimeException("offline"));

        assertTrue(provider.listSubscriptions("groupA").isEmpty());
    }

    // ==================== Client listing ====================

    private ProducerConnection producerConnection(String clientId) {
        ProducerConnection connection = new ProducerConnection();
        Connection conn = new Connection();
        conn.setClientId(clientId);
        conn.setClientAddr("127.0.0.1:5001");
        conn.setLanguage(LanguageCode.JAVA);
        conn.setVersion(0);
        connection.setConnectionSet(new HashSet<>(Collections.singletonList(conn)));
        return connection;
    }

    @Test
    public void testListClientInstancesCollectsProducers() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenReturn(producerConnection("client-1"));

        List<ClientInstance> clients = provider.listClientInstances(Optional.empty(), Optional.empty());
        assertEquals(1, clients.size());
        assertEquals("client-1", clients.get(0).getClientId());
        assertEquals(ClientInstance.ClientType.PRODUCER, clients.get(0).getClientType());
        assertEquals("JAVA", clients.get(0).getLanguage());
        assertEquals(ClientInstance.ProtocolType.REMOTING, clients.get(0).getProtocolType());
    }

    @Test
    public void testListClientInstancesTopicFilter() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA", "topicB"));
        when(mqAdminExt.examineProducerConnectionInfo(eq("DEFAULT_PRODUCER"), anyString()))
            .thenReturn(producerConnection("client-1"));

        List<ClientInstance> clients = provider.listClientInstances(Optional.of("topicA"), Optional.empty());
        assertEquals(1, clients.size());
        verify(mqAdminExt, never()).examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicB");
    }

    @Test
    public void testListClientInstancesConnectionFailureIsTolerated() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenThrow(new RuntimeException("no producer"));

        assertTrue(provider.listClientInstances(Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    public void testGetClientInstanceByClientId() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenReturn(producerConnection("client-1"));

        assertTrue(provider.getClientInstance("client-1").isPresent());
        assertFalse(provider.getClientInstance("missing").isPresent());
    }

    @Test
    public void testGetClientSubscriptionsReturnsEmpty() throws Exception {
        assertTrue(provider.getClientSubscriptions("client-1").isEmpty());
    }

    @Test
    public void testClientConvenienceFilters() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenReturn(producerConnection("client-1"));

        assertEquals(1, provider.listClientsByProtocol("REMOTING").size());
        assertTrue(provider.listClientsByProtocol("GRPC").isEmpty());
        assertEquals(1, provider.listClientsByType("PRODUCER").size());
        assertTrue(provider.listClientsByType("PUSH_CONSUMER").isEmpty());
        assertEquals(1, provider.listClientsByCluster("DefaultCluster").size());
        assertEquals(1, provider.getConnectedClients("127.0.0.1").size());
        assertTrue(provider.getConnectedClients("10.0.0.9").isEmpty());
        // Producers collected via Remoting carry no heartbeat timestamp
        assertTrue(provider.getIdleClients(1000L).isEmpty());
        assertTrue(provider.getClientsWithIssue("SLOW_CONSUMER").isEmpty());
    }

    @Test
    public void testKillClientAndUpdateClientConfigAreNoOps() throws Exception {
        provider.killClient("client-1", "test");
        provider.updateClientConfig("client-1", "key", "value");
        verifyNoInteractions(mqAdminExt);
    }

    // ==================== Route-based message query ====================

    private V4MetadataProvider routeQueryProvider() {
        return new V4MetadataProvider(mqAdminExt, consumerWrapper, rmqConfigure);
    }

    @Test
    public void testQueryMessageByTopicViaRoutePullsMessages() throws Exception {
        V4MetadataProvider routeProvider = routeQueryProvider();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        when(rmqConfigure.isACLEnabled()).thenReturn(false);
        when(consumerWrapper.getConsumer(isNull(), eq(false))).thenReturn(pullConsumer);
        MessageQueue mq = new MessageQueue("topicA", "broker-a", 0);
        when(pullConsumer.searchOffset(mq, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(mq, 200L)).thenReturn(2L);
        PullResult pullResult = new PullResult(PullStatus.FOUND, 2L, 0L, 2L,
            Arrays.asList(message("topicA", 150L), message("topicA", 999L)));
        when(pullConsumer.pull(eq(mq), eq("*"), anyLong(), anyInt())).thenReturn(pullResult);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 10);
        // Only the message within [beginTime, endTime] is kept
        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getBody());
        assertEquals(150L, messages.get(0).getBornTimestamp());
    }

    @Test
    public void testQueryMessageByTopicViaRouteNoRouteReturnsEmpty() throws Exception {
        V4MetadataProvider routeProvider = routeQueryProvider();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(null);

        assertTrue(routeProvider.queryMessageByTopic("topicA", 100L, 200L, 10).isEmpty());
    }

    @Test
    public void testQueryMessageByTopicViaRouteEmptyRangeSkipsQueue() throws Exception {
        V4MetadataProvider routeProvider = routeQueryProvider();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        when(consumerWrapper.getConsumer(isNull(), eq(false))).thenReturn(pullConsumer);
        MessageQueue mq = new MessageQueue("topicA", "broker-a", 0);
        // start >= end means no messages in the time range
        when(pullConsumer.searchOffset(mq, 100L)).thenReturn(5L);
        when(pullConsumer.searchOffset(mq, 200L)).thenReturn(5L);

        assertTrue(routeProvider.queryMessageByTopic("topicA", 100L, 200L, 10).isEmpty());
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
    }

    @Test
    public void testQueryMessageByTopicFallsBackToAdminQueryOnRouteFailure() throws Exception {
        V4MetadataProvider routeProvider = routeQueryProvider();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenThrow(new RuntimeException("route down"));
        org.apache.rocketmq.client.QueryResult queryResult =
            new org.apache.rocketmq.client.QueryResult(0, Collections.singletonList(message("topicA", 150L)));
        when(mqAdminExt.queryMessage("topicA", null, 10, 100L, 200L)).thenReturn(queryResult);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 10);
        assertEquals(1, messages.size());
    }
}
