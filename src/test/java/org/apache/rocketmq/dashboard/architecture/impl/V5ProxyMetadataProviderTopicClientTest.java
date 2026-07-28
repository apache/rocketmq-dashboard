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
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Additional unit tests for {@link V5ProxyMetadataProvider} covering topic CRUD,
 * LiteTopic prefix aggregation, consumer group CRUD success paths, client listing,
 * the route-based message query path and IP-whitelist ACL enforcement
 * (complements V5ProxyMetadataProviderTest which focuses on ACL basics,
 * namespaces and admin-query paths).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class V5ProxyMetadataProviderTopicClientTest {

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private AutoCloseConsumerWrapper consumerWrapper;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private DefaultMQPullConsumer pullConsumer;

    private V5ProxyMetadataProvider provider;

    @Before
    public void setUp() {
        provider = new V5ProxyMetadataProvider(mqAdminExt);
    }

    // ==================== Helpers ====================

    private TopicList topicListOf(String... names) {
        TopicList topicList = new TopicList();
        topicList.setTopicList(new LinkedHashSet<>(Arrays.asList(names)));
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
        brokerAddrs.put(1L, "127.0.0.1:10912");
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

    private ProducerConnection producerConnectionOf(Connection... connections) {
        ProducerConnection producerConnection = new ProducerConnection();
        producerConnection.setConnectionSet(new HashSet<>(Arrays.asList(connections)));
        return producerConnection;
    }

    private Connection connection(String clientId, String addr) {
        Connection connection = new Connection();
        connection.setClientId(clientId);
        connection.setClientAddr(addr);
        connection.setLanguage(LanguageCode.JAVA);
        connection.setVersion(355);
        return connection;
    }

    private ACLUser aclUser(String name) {
        ACLUser user = new ACLUser();
        user.setUserName(name);
        user.setAccessKey("ak-" + name);
        return user;
    }

    private ACLPolicy allowPolicy(String id, String user, Set<String> ipWhiteList) {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId(id);
        policy.setPolicyName(id);
        policy.setPolicyType("ALLOW");
        policy.setUsers(new HashSet<>(Collections.singletonList(user)));
        policy.setResources(new HashSet<>(Collections.singletonList("topicA")));
        policy.setActions(new HashSet<>(Collections.singletonList("PUB")));
        policy.setIpWhiteList(ipWhiteList);
        return policy;
    }

    // ==================== Topic operations ====================

    @Test
    public void testListTopicsSkipsRetryAndDlqAndFillsQueueNums() throws Exception {
        when(mqAdminExt.fetchAllTopicList())
            .thenReturn(topicListOf("topicA", "%RETRY%groupA", "%DLQ%groupA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 2));

        List<TopicInfo> topics = provider.listTopics(Optional.empty());
        assertEquals(1, topics.size());
        assertEquals("topicA", topics.get(0).getTopicName());
        assertEquals("", topics.get(0).getNamespace());
        assertEquals(Integer.valueOf(4), topics.get(0).getReadQueueNums());
        assertEquals(Integer.valueOf(2), topics.get(0).getWriteQueueNums());
    }

    @Test
    public void testListTopicsUsesCallerNamespace() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));

        List<TopicInfo> topics = provider.listTopics(Optional.of("nsX"));
        assertEquals(1, topics.size());
        assertEquals("nsX", topics.get(0).getNamespace());
    }

    @Test
    public void testGetTopicFound() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));

        Optional<TopicInfo> topic = provider.getTopic("topicA", Optional.empty());
        assertTrue(topic.isPresent());
        assertEquals(Integer.valueOf(4), topic.get().getReadQueueNums());
    }

    @Test
    public void testGetTopicNullRouteReturnsEmpty() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("ghost")).thenReturn(null);
        assertFalse(provider.getTopic("ghost", Optional.empty()).isPresent());
    }

    @Test
    public void testGetTopicExceptionReturnsEmpty() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("ghost")).thenThrow(new RuntimeException("not found"));
        assertFalse(provider.getTopic("ghost", Optional.empty()).isPresent());
    }

    @Test
    public void testCreateTopicInvalidNameThrows() throws Exception {
        try {
            provider.createTopic(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        TopicInfo blank = new TopicInfo();
        blank.setTopicName("   ");
        try {
            provider.createTopic(blank);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testCreateTopicOnMasterBrokersOnly() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("topicA");
        topic.setReadQueueNums(4);
        topic.setWriteQueueNums(4);

        provider.createTopic(topic);
        verify(mqAdminExt).createAndUpdateTopicConfig(eq("127.0.0.1:10911"), any(TopicConfig.class));
        verify(mqAdminExt, never()).createAndUpdateTopicConfig(eq("127.0.0.1:10912"), any(TopicConfig.class));
    }

    @Test
    public void testCreateTopicFifoSetsOrderFlag() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("fifoTopic")).thenReturn(routeData(1, 1));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("fifoTopic");
        topic.setTopicType(TopicType.FIFO);

        provider.createTopic(topic);
        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(mqAdminExt).createAndUpdateTopicConfig(eq("127.0.0.1:10911"), captor.capture());
        assertTrue(captor.getValue().isOrder());
        // Defaults applied when queue nums absent
        assertEquals(8, captor.getValue().getReadQueueNums());
        assertEquals(8, captor.getValue().getWriteQueueNums());
    }

    @Test
    public void testCreateTopicBrokerFailureIsTolerated() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        doThrow(new RuntimeException("broker down"))
            .when(mqAdminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("topicA");

        // Per-broker failures are logged, not propagated
        provider.createTopic(topic);
    }

    @Test
    public void testCreateTopicNoRouteFallback() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("newTopic")).thenReturn(null);
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("newTopic");

        provider.createTopic(topic);
        verify(mqAdminExt).createAndUpdateTopicConfig(isNull(), any(TopicConfig.class));
    }

    @Test
    public void testUpdateTopicMissingThrows() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("ghost")).thenThrow(new RuntimeException("not found"));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("ghost");
        try {
            provider.updateTopic(topic);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUpdateTopicDelegatesToCreate() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));
        TopicInfo topic = new TopicInfo();
        topic.setTopicName("topicA");

        provider.updateTopic(topic);
        verify(mqAdminExt).createAndUpdateTopicConfig(eq("127.0.0.1:10911"), any(TopicConfig.class));
    }

    @Test
    public void testDeleteTopicEmptyNameThrows() throws Exception {
        try {
            provider.deleteTopic("  ", Optional.empty());
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testDeleteTopicByCluster() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(4, 4));

        provider.deleteTopic("topicA", Optional.empty());
        verify(mqAdminExt).deleteTopic("topicA", "DefaultCluster");
    }

    @Test
    public void testValidateTopicType() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        assertTrue(provider.validateTopicType("topicA", TopicType.NORMAL));

        when(mqAdminExt.examineTopicRouteInfo("ghost")).thenReturn(null);
        assertFalse(provider.validateTopicType("ghost", TopicType.NORMAL));

        when(mqAdminExt.examineTopicRouteInfo("boom")).thenThrow(new RuntimeException("fail"));
        assertFalse(provider.validateTopicType("boom", TopicType.NORMAL));
    }

    // ==================== LiteTopic aggregation ====================

    @Test
    public void testListLiteTopicsAggregatesByPrefix() throws Exception {
        when(mqAdminExt.fetchAllTopicList())
            .thenReturn(topicListOf("order-a", "order-b", "standalone", "%RETRY%g"));

        List<LiteTopicSummary> summaries = provider.listLiteTopics(null, Optional.empty());
        // Without an explicit query only multi-member prefixes are surfaced
        assertEquals(1, summaries.size());
        assertEquals("order*", summaries.get(0).getTopicPattern());
        assertEquals(2, summaries.get(0).getTopicCount().intValue());
        assertEquals(Arrays.asList("order-a", "order-b"), summaries.get(0).getSessionIds());
    }

    @Test
    public void testListLiteTopicsExplicitQuerySurfacesSingleMembers() throws Exception {
        when(mqAdminExt.fetchAllTopicList())
            .thenReturn(topicListOf("order-a", "pay_x"));

        List<LiteTopicSummary> summaries = provider.listLiteTopics("pay", Optional.empty());
        assertEquals(1, summaries.size());
        assertEquals("pay*", summaries.get(0).getTopicPattern());
        assertEquals(1, summaries.get(0).getTopicCount().intValue());
    }

    @Test
    public void testListLiteTopicsSkipsSystemTopics() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf(
            "%RETRY%g", "%DLQ%g", "%SYS%x", "rmq_sys_TRACE", "SCHEDULE_TOPIC_XXXX",
            "SELF_TEST_TOPIC", "OFFSET_MOVED_EVENT", "TBW102", "BenchmarkTest", "foo_REPLY_TOPIC"));

        assertTrue(provider.listLiteTopics(null, Optional.empty()).isEmpty());
    }

    // ==================== Consumer group operations ====================

    @Test
    public void testListConsumerGroupsFromRetryTopics() throws Exception {
        when(mqAdminExt.fetchAllTopicList())
            .thenReturn(topicListOf("topicA", "%RETRY%groupA", "%RETRY%groupB", "%RETRY%"));

        List<ConsumerGroupInfo> groups = provider.listConsumerGroups(Optional.empty());
        assertEquals(2, groups.size());
        assertTrue(groups.stream().anyMatch(g -> "groupA".equals(g.getConsumerGroupName())));
        assertTrue(groups.stream().anyMatch(g -> "groupB".equals(g.getConsumerGroupName())));
        assertEquals("NORMAL", groups.get(0).getStatus());
    }

    @Test
    public void testGetConsumerGroupFoundAndMissing() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA"));

        assertTrue(provider.getConsumerGroup("groupA", Optional.empty()).isPresent());
        assertFalse(provider.getConsumerGroup("missing", Optional.empty()).isPresent());
    }

    @Test
    public void testCreateConsumerGroupOnMasterBrokers() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        provider.createConsumerGroup(group);
        ArgumentCaptor<SubscriptionGroupConfig> captor =
            ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig(eq("127.0.0.1:10911"), captor.capture());
        assertEquals("groupA", captor.getValue().getGroupName());
        assertEquals(16, captor.getValue().getRetryMaxTimes());
        assertTrue(captor.getValue().isConsumeFromMinEnable());
        assertFalse(captor.getValue().isConsumeBroadcastEnable());
    }

    @Test
    public void testCreateConsumerGroupRouteFailureIsTolerated() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenThrow(new RuntimeException("no route"));
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        // Per-topic failures are logged, not propagated
        provider.createConsumerGroup(group);
        verify(mqAdminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testUpdateConsumerGroupDelegatesToCreate() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName("groupA");

        provider.updateConsumerGroup(group);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig(eq("127.0.0.1:10911"), any(SubscriptionGroupConfig.class));
    }

    @Test
    public void testDeleteConsumerGroupRemovesRetryTopic() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("%RETRY%groupA")).thenReturn(routeData(1, 1));

        provider.deleteConsumerGroup("groupA", Optional.empty());
        verify(mqAdminExt).deleteTopic("%RETRY%groupA", "DefaultCluster");
    }

    @Test
    public void testDeleteConsumerGroupMissingRetryTopicIsTolerated() throws Exception {
        when(mqAdminExt.examineTopicRouteInfo("%RETRY%groupA")).thenThrow(new RuntimeException("no route"));

        provider.deleteConsumerGroup("groupA", Optional.empty());
        verify(mqAdminExt, never()).deleteTopic(anyString(), anyString());
    }

    // ==================== Client instance operations ====================

    @Test
    public void testListClientInstancesFromProducerConnections() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("CLIENT_PRODUCER", "topicA"))
            .thenReturn(producerConnectionOf(connection("client-1", "127.0.0.1:5000")));

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
        when(mqAdminExt.examineProducerConnectionInfo("CLIENT_PRODUCER", "topicB"))
            .thenReturn(producerConnectionOf(connection("client-b", "127.0.0.1:5001")));

        List<ClientInstance> clients = provider.listClientInstances(Optional.of("topicB"), Optional.empty());
        assertEquals(1, clients.size());
        verify(mqAdminExt, never()).examineProducerConnectionInfo("CLIENT_PRODUCER", "topicA");
    }

    @Test
    public void testListClientInstancesConnectionFailureIsTolerated() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo(anyString(), anyString()))
            .thenThrow(new RuntimeException("no connection"));

        assertTrue(provider.listClientInstances(Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    public void testGetClientInstanceFoundAndMissing() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("CLIENT_PRODUCER", "topicA"))
            .thenReturn(producerConnectionOf(connection("client-1", "127.0.0.1:5000")));

        assertTrue(provider.getClientInstance("client-1").isPresent());
        assertFalse(provider.getClientInstance("ghost").isPresent());
    }

    // ==================== Route-based message query ====================

    private V5ProxyMetadataProvider providerWithWrapper() {
        return new V5ProxyMetadataProvider(mqAdminExt, Optional.empty(), consumerWrapper, rmqConfigure);
    }

    @Test
    public void testQueryMessageByTopicViaRouteFiltersByTime() throws Exception {
        V5ProxyMetadataProvider routeProvider = providerWithWrapper();
        when(rmqConfigure.isACLEnabled()).thenReturn(false);
        when(rmqConfigure.isUseTLS()).thenReturn(false);
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        when(consumerWrapper.getConsumer(isNull(), eq(false))).thenReturn(pullConsumer);

        MessageQueue mq = new MessageQueue("topicA", "broker-a", 0);
        when(pullConsumer.searchOffset(mq, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(mq, 200L)).thenReturn(2L);
        PullResult pullResult = new PullResult(PullStatus.FOUND, 2L, 0L, 2L,
            Arrays.asList(message("topicA", 150L), message("topicA", 999L)));
        when(pullConsumer.pull(eq(mq), eq("*"), anyLong(), anyInt())).thenReturn(pullResult);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 32);
        // Only the message with bornTimestamp inside [100, 200] is kept
        assertEquals(1, messages.size());
        assertEquals(150L, messages.get(0).getBornTimestamp());
        verify(mqAdminExt, never()).queryMessage(anyString(), any(), anyInt(), anyLong(), anyLong());
    }

    @Test
    public void testQueryMessageByTopicViaRouteNullRouteReturnsEmpty() throws Exception {
        V5ProxyMetadataProvider routeProvider = providerWithWrapper();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(null);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 32);
        assertTrue(messages.isEmpty());
        // Empty route is not an error: no fallback to the admin query API
        verify(mqAdminExt, never()).queryMessage(anyString(), any(), anyInt(), anyLong(), anyLong());
    }

    @Test
    public void testQueryMessageByTopicViaRouteStartGteEndSkipsQueue() throws Exception {
        V5ProxyMetadataProvider routeProvider = providerWithWrapper();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData(1, 1));
        when(consumerWrapper.getConsumer(isNull(), eq(false))).thenReturn(pullConsumer);

        MessageQueue mq = new MessageQueue("topicA", "broker-a", 0);
        when(pullConsumer.searchOffset(mq, 100L)).thenReturn(5L);
        when(pullConsumer.searchOffset(mq, 200L)).thenReturn(5L);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 32);
        assertTrue(messages.isEmpty());
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
    }

    @Test
    public void testQueryMessageByTopicViaRouteFailureFallsBackToAdminQuery() throws Exception {
        V5ProxyMetadataProvider routeProvider = providerWithWrapper();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenThrow(new RuntimeException("route fail"));
        MessageExt msg = message("topicA", 150L);
        org.apache.rocketmq.client.QueryResult queryResult =
            new org.apache.rocketmq.client.QueryResult(0, Collections.singletonList(msg));
        when(mqAdminExt.queryMessage("topicA", null, 32, 100L, 200L)).thenReturn(queryResult);

        List<MessageInfo> messages = routeProvider.queryMessageByTopic("topicA", 100L, 200L, 32);
        assertEquals(1, messages.size());
        assertEquals("topicA", messages.get(0).getTopic());
    }

    // ==================== ACL IP whitelist enforcement ====================

    @Test
    public void testCheckACLPermissionIpExactMatch() throws Exception {
        provider.createACLUser(aclUser("alice"));
        provider.addACLPolicy(allowPolicy("p1", "alice",
            new HashSet<>(Collections.singletonList("192.168.1.100"))));

        assertTrue(provider.checkACLPermission("alice", "topicA", "PUB", "192.168.1.100"));
        assertFalse(provider.checkACLPermission("alice", "topicA", "PUB", "192.168.1.101"));
    }

    @Test
    public void testCheckACLPermissionIpCidrMatch() throws Exception {
        provider.createACLUser(aclUser("bob"));
        provider.addACLPolicy(allowPolicy("p2", "bob",
            new HashSet<>(Collections.singletonList("192.168.1.0/24"))));

        assertTrue(provider.checkACLPermission("bob", "topicA", "PUB", "192.168.1.55"));
        assertFalse(provider.checkACLPermission("bob", "topicA", "PUB", "192.168.2.55"));
    }

    @Test
    public void testCheckACLPermissionIpWildcardMatch() throws Exception {
        provider.createACLUser(aclUser("carol"));
        provider.addACLPolicy(allowPolicy("p3", "carol",
            new HashSet<>(Collections.singletonList("10.0.*.*"))));

        assertTrue(provider.checkACLPermission("carol", "topicA", "PUB", "10.0.3.4"));
        assertFalse(provider.checkACLPermission("carol", "topicA", "PUB", "10.1.3.4"));
    }

    @Test
    public void testCheckACLPermissionWhitelistWithoutSourceIpSkipsIpCheck() throws Exception {
        provider.createACLUser(aclUser("dave"));
        provider.addACLPolicy(allowPolicy("p4", "dave",
            new HashSet<>(Collections.singletonList("192.168.1.100"))));

        // No source IP provided: whitelist check is skipped, permission granted
        assertTrue(provider.checkACLPermission("dave", "topicA", "PUB", null));
        assertTrue(provider.checkACLPermission("dave", "topicA", "PUB"));
    }

    @Test
    public void testCheckACLPermissionInvalidSourceIpAgainstCidrDenied() throws Exception {
        provider.createACLUser(aclUser("erin"));
        provider.addACLPolicy(allowPolicy("p5", "erin",
            new HashSet<>(Collections.singletonList("192.168.1.0/24"))));

        // Malformed IP fails CIDR parsing and matches nothing in the whitelist
        assertFalse(provider.checkACLPermission("erin", "topicA", "PUB", "not-an-ip"));
    }
}
