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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GrpcAdminClientTest {

    private static final String PROXY_ADDRESS = "192.168.1.100:10911";
    private static final String DIFFERENT_PROXY = "10.0.0.1:9876";

    private MQAdminExt mqAdminExt;
    private Object grpcClient;

    @Before
    public void setUp() {
        mqAdminExt = mock(MQAdminExt.class);
        grpcClient = mock(Object.class, "grpcClientStub");
    }

    @After
    public void tearDown() {
        // No shared static state to clean up
    }

    // ==================== Constructor: two-arg (without grpcClient) ====================

    @Test
    public void testConstructorWithoutGrpcClient() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
        assertFalse("grpcAvailable should be false when constructed without grpcClient",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
    }

    @Test
    public void testConstructorWithoutGrpcClientReturnsV5ProxyCluster() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertSame(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
    }

    // ==================== Constructor: three-arg with grpcClient ====================

    @Test
    public void testConstructorWithGrpcClientEnablesGrpc() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertTrue("grpcAvailable should be true when constructed with non-null grpcClient",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
    }

    // ==================== Constructor: three-arg with null grpcClient ====================

    @Test
    public void testConstructorWithNullGrpcClientDisablesGrpc() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, null);

        assertFalse("grpcAvailable should be false when grpcClient is null",
                client.isGrpcAvailable());
        assertEquals(PROXY_ADDRESS, client.getProxyAddress());
    }

    // ==================== Constructor validation: null proxyAddress ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullProxyAddressThrowsException() {
        new GrpcAdminClient(null, mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullProxyAddressAndGrpcClientThrowsException() {
        new GrpcAdminClient(null, mqAdminExt, grpcClient);
    }

    // ==================== Constructor validation: empty proxyAddress ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyProxyAddressThrowsException() {
        new GrpcAdminClient("", mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithBlankProxyAddressThrowsException() {
        new GrpcAdminClient("   ", mqAdminExt);
    }

    // ==================== Constructor validation: null mqAdminExt ====================

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullMqAdminExtThrowsException() {
        new GrpcAdminClient(PROXY_ADDRESS, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullMqAdminExtAndGrpcClientThrowsException() {
        new GrpcAdminClient(PROXY_ADDRESS, null, grpcClient);
    }

    // ==================== getClientType ====================

    @Test
    public void testGetClientTypeAlwaysReturnsV5ProxyCluster() {
        GrpcAdminClient clientWithoutGrpc = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        GrpcAdminClient clientWithGrpc = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, clientWithoutGrpc.getClientType());
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, clientWithGrpc.getClientType());
    }

    // ==================== getProxyAddress ====================

    @Test
    public void testGetProxyAddressReturnsCorrectValue() {
        GrpcAdminClient client = new GrpcAdminClient(DIFFERENT_PROXY, mqAdminExt);

        assertEquals(DIFFERENT_PROXY, client.getProxyAddress());
    }

    @Test
    public void testGetProxyAddressPreservesOriginalValue() {
        String specialAddress = "proxy-host.internal:8080";
        GrpcAdminClient client = new GrpcAdminClient(specialAddress, mqAdminExt);

        assertEquals(specialAddress, client.getProxyAddress());
    }

    // ==================== isGrpcAvailable ====================

    @Test
    public void testIsGrpcAvailableReflectsStateWhenDisabled() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        assertFalse(client.isGrpcAvailable());
    }

    @Test
    public void testIsGrpcAvailableReflectsStateWhenEnabled() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        assertTrue(client.isGrpcAvailable());
    }

    @Test
    public void testIsGrpcAvailableWithNullGrpcClient() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, null);

        assertFalse(client.isGrpcAvailable());
    }

    // ==================== shutdown prevents subsequent operations ====================

    @Test
    public void testShutdownPreventsGetClusterInfo() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getClusterInfo();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicList() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicList();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetConsumerGroupList() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getConsumerGroupList();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicRoute() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicRoute("test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetBrokerRuntimeStats() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getBrokerRuntimeStats("127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicStats() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicStats("test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsCreateOrUpdateTopic() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.createOrUpdateTopic("test-topic", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsDeleteTopic() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.deleteTopic("test-topic", "DefaultCluster");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetTopicListFromBroker() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getTopicListFromBroker("127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetConsumerConnection() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getConsumerConnection("test-group");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetGroupConsumeInfo() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getGroupConsumeInfo("test-group");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsResetConsumeOffset() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.resetConsumeOffset("test-group", "test-topic", 0L, false);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsCreateOrUpdateConsumerGroup() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.createOrUpdateConsumerGroup("test-group", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsDeleteConsumerGroup() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.deleteConsumerGroup("test-group", "127.0.0.1:10911");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetProducerConnection() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getProducerConnection("test-group", "test-topic");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsQueryMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.queryMessage("test-topic", "key", 0L, System.currentTimeMillis(), 10);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsViewMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.viewMessage("test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsConsumeMessageDirectly() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.consumeMessageDirectly("test-group", "test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsReplayMessage() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.replayMessage("test-group", "test-topic", "msgId");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsGetNameServerConfig() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.getNameServerConfig("127.0.0.1:9876");
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    @Test
    public void testShutdownPreventsUpdateBrokerConfig() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.shutdown();

        try {
            client.updateBrokerConfig("127.0.0.1:10911", null);
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        }
    }

    // ==================== getAccessControlList throws UnsupportedOperationException ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlListThrowsUnsupportedOperationException() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.getAccessControlList("127.0.0.1:10911");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAccessControlListThrowsEvenWithGrpcEnabled() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        client.getAccessControlList("127.0.0.1:10911");
    }

    // ==================== updateAccessControlList throws UnsupportedOperationException ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateAccessControlListThrowsUnsupportedOperationException() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.updateAccessControlList("127.0.0.1:10911", null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateAccessControlListThrowsEvenWithGrpcEnabled() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);

        client.updateAccessControlList("127.0.0.1:10911", null);
    }

    // ==================== shutdown idempotency ====================

    @Test
    public void testShutdownIsIdempotent() {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);

        client.shutdown();
        // Second shutdown should not throw
        client.shutdown();

        // After shutdown, operations should still be blocked
        try {
            client.getClusterInfo();
            fail("Expected IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            assertEquals("GrpcAdminClient has been shut down", e.getMessage());
        } catch (Exception e) {
            fail("Expected IllegalStateException but got " + e.getClass().getSimpleName());
        }
    }

    // ==================== Remoting fallback delegation ====================

    private TopicList buildTopicList(String... names) {
        TopicList topicList = new TopicList();
        topicList.setTopicList(new HashSet<>(java.util.Arrays.asList(names)));
        return topicList;
    }

    @Test
    public void testGetClusterInfoDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);
        ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        assertSame(clusterInfo, client.getClusterInfo());
    }

    @Test
    public void testGetBrokerRuntimeStatsDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);
        KVTable kvTable = new KVTable();
        when(mqAdminExt.fetchBrokerRuntimeStats("127.0.0.1:10911")).thenReturn(kvTable);

        assertSame(kvTable, client.getBrokerRuntimeStats("127.0.0.1:10911"));
    }

    @Test
    public void testUpdateBrokerConfigDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt, grpcClient);
        Properties properties = new Properties();
        client.updateBrokerConfig("127.0.0.1:10911", properties);

        verify(mqAdminExt).updateBrokerConfig("127.0.0.1:10911", properties);
    }

    @Test
    public void testGetTopicListDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA", "topicB"));

        List<String> topics = client.getTopicList();
        assertEquals(2, topics.size());
        assertTrue(topics.contains("topicA"));
    }

    @Test
    public void testGetTopicRouteDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        TopicRouteData routeData = MockObjectUtil.createTopicRouteData();
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(routeData);

        assertSame(routeData, client.getTopicRoute("topicA"));
    }

    @Test
    public void testGetTopicStatsDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        TopicStatsTable statsTable = MockObjectUtil.createTopicStatsTable();
        when(mqAdminExt.examineTopicStats("topicA")).thenReturn(statsTable);

        assertSame(statsTable, client.getTopicStats("topicA"));
    }

    @Test
    public void testCreateOrUpdateTopicOnMasterBrokers() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        TopicConfig topicConfig = new TopicConfig("topicA");
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());

        client.createOrUpdateTopic("topicA", topicConfig);
        verify(mqAdminExt).createAndUpdateTopicConfig("127.0.0.1:10911", topicConfig);
    }

    @Test
    public void testCreateOrUpdateTopicNullRouteSkips() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(null);

        client.createOrUpdateTopic("topicA", new TopicConfig("topicA"));
        verify(mqAdminExt, never()).createAndUpdateTopicConfig(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(TopicConfig.class));
    }

    @Test
    public void testCreateOrUpdateTopicSwallowsBrokerFailure() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(mqAdminExt).createAndUpdateTopicConfig(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(TopicConfig.class));

        // Failure on a single broker must not propagate
        client.createOrUpdateTopic("topicA", new TopicConfig("topicA"));
    }

    @Test
    public void testDeleteTopicDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.deleteTopic("topicA", "DefaultCluster");

        java.util.Set<String> clusters = new HashSet<>(Collections.singletonList("DefaultCluster"));
        verify(mqAdminExt).deleteTopicInBroker(clusters, "topicA");
        verify(mqAdminExt).deleteTopicInNameServer(clusters, "topicA");
    }

    @Test
    public void testGetTopicListFromBrokerUsesNameServerList() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        TopicList topicList = buildTopicList("topicA");
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicList);

        assertSame(topicList, client.getTopicListFromBroker("127.0.0.1:10911"));
    }

    @Test
    public void testGetConsumerGroupListExtractsRetryTopics() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA", "%RETRY%groupA", "%RETRY%groupB"));

        List<String> groups = client.getConsumerGroupList();
        assertEquals(2, groups.size());
        assertTrue(groups.contains("groupA"));
        assertTrue(groups.contains("groupB"));
    }

    @Test
    public void testGetConsumerConnectionDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        ConsumerConnection connection = MockObjectUtil.createConsumerConnection();
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(connection);

        assertSame(connection, client.getConsumerConnection("groupA"));
    }

    @Test
    public void testGetGroupConsumeInfoWithRetryTopic() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("%RETRY%groupA"));

        GroupConsumeInfo info = client.getGroupConsumeInfo("groupA");
        assertEquals("groupA", info.getGroup());
        assertEquals(0L, info.getDiffTotal());
        assertEquals(0, info.getCount());
        verify(mqAdminExt, never()).examineConsumeStats("groupA");
    }

    @Test
    public void testGetGroupConsumeInfoFromConsumeStats() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineConsumeStats("groupA")).thenReturn(MockObjectUtil.createConsumeStats());

        GroupConsumeInfo info = client.getGroupConsumeInfo("groupA");
        // Two queues, each brokerOffset(10) - consumerOffset(7) = 3
        assertEquals(6L, info.getDiffTotal());
    }

    @Test
    public void testGetGroupConsumeInfoStatsFailureDefaultsToZero() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenThrow(new RuntimeException("namesrv down"));
        when(mqAdminExt.examineConsumeStats("groupA")).thenThrow(new RuntimeException("broker down"));

        GroupConsumeInfo info = client.getGroupConsumeInfo("groupA");
        assertEquals(0L, info.getDiffTotal());
    }

    @Test
    public void testResetConsumeOffsetParameterOrder() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.resetConsumeOffset("groupA", "topicA", 123L, true);

        // consumerGroup comes before topic for the gRPC client (unlike RemotingAdminClient)
        verify(mqAdminExt).resetOffsetByTimestamp("groupA", "topicA", 123L, true);
    }

    @Test
    public void testCreateOrUpdateConsumerGroupOnMasterBrokers() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());

        client.createOrUpdateConsumerGroup("groupA", config);
        verify(mqAdminExt).createAndUpdateSubscriptionGroupConfig("127.0.0.1:10911", config);
    }

    @Test
    public void testCreateOrUpdateConsumerGroupSwallowsBrokerFailure() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(buildTopicList("topicA"));
        when(mqAdminExt.examineTopicRouteInfo("topicA")).thenReturn(MockObjectUtil.createTopicRouteData());
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(SubscriptionGroupConfig.class));

        client.createOrUpdateConsumerGroup("groupA", new SubscriptionGroupConfig());
    }

    @Test
    public void testDeleteConsumerGroupDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.deleteConsumerGroup("groupA", "127.0.0.1:10911");

        verify(mqAdminExt).deleteSubscriptionGroup("127.0.0.1:10911", "groupA");
    }

    @Test
    public void testGetProducerConnectionDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        ProducerConnection connection = new ProducerConnection();
        when(mqAdminExt.examineProducerConnectionInfo("pg", "topicA")).thenReturn(connection);

        assertSame(connection, client.getProducerConnection("pg", "topicA"));
    }

    @Test
    public void testQueryMessageParameterOrder() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        QueryResult queryResult = new QueryResult(0, Collections.emptyList());
        when(mqAdminExt.queryMessage("topicA", "key", 32, 1L, 2L)).thenReturn(queryResult);

        assertSame(queryResult, client.queryMessage("topicA", "key", 1L, 2L, 32));
    }

    @Test
    public void testViewMessageDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        MessageExt messageExt = MockObjectUtil.createMessageExt();
        when(mqAdminExt.viewMessage("topicA", "msgId")).thenReturn(messageExt);

        assertSame(messageExt, client.viewMessage("topicA", "msgId"));
    }

    @Test
    public void testConsumeMessageDirectlyDelegates() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.consumeMessageDirectly("groupA", "topicA", "msgId");

        verify(mqAdminExt).consumeMessageDirectly("groupA", "topicA", "msgId", null);
    }

    @Test
    public void testReplayMessageDelegatesToConsumeMessageDirectly() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        client.replayMessage("groupA", "topicA", "msgId");

        verify(mqAdminExt).consumeMessageDirectly("groupA", "topicA", "msgId", null);
    }

    @Test
    public void testGetNameServerConfigMergesProperties() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        Properties props = new Properties();
        props.setProperty("listenPort", "9876");
        Map<String, Properties> configMap = new HashMap<>();
        configMap.put("127.0.0.1:9876", props);
        when(mqAdminExt.getNameServerConfig(Collections.singletonList("127.0.0.1:9876"))).thenReturn(configMap);

        KVTable kvTable = client.getNameServerConfig("127.0.0.1:9876");
        assertEquals("9876", kvTable.getTable().get("listenPort"));
    }

    @Test
    public void testGetNameServerConfigNullMapReturnsEmptyTable() throws Exception {
        GrpcAdminClient client = new GrpcAdminClient(PROXY_ADDRESS, mqAdminExt);
        when(mqAdminExt.getNameServerConfig(Collections.singletonList("127.0.0.1:9876"))).thenReturn(null);

        KVTable kvTable = client.getNameServerConfig("127.0.0.1:9876");
        assertNotNull(kvTable);
    }

    // ==================== Helper methods ====================

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
