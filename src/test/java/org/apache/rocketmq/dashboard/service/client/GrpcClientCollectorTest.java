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

package org.apache.rocketmq.dashboard.service.client;

import apache.rocketmq.proxy.admin.v1.AuthStatus;
import apache.rocketmq.proxy.admin.v1.ClientDetail;
import apache.rocketmq.proxy.admin.v1.ClientLanguage;
import apache.rocketmq.proxy.admin.v1.ClientRole;
import apache.rocketmq.proxy.admin.v1.ClientSettings;
import apache.rocketmq.proxy.admin.v1.NetworkInfo;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class GrpcClientCollectorTest {

    @Mock
    private ProxyAdminGrpcClient mockGrpcClient;

    private GrpcClientCollector collector;

    private static final String TEST_CLIENT_ID = "testClientId";
    private static final String TEST_GROUP = "testGroup";
    private static final String TEST_TOPIC = "testTopic";

    @Before
    public void setUp() {
        collector = new GrpcClientCollector(mockGrpcClient);
    }

    // ==================== hasDataAvailable() delegation ====================

    @Test
    public void testHasDataAvailableDelegatesToGrpcClient() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        assertTrue("hasDataAvailable should return true when grpcClient is available",
            collector.hasDataAvailable());
    }

    @Test
    public void testHasDataAvailableReturnsFalseWhenGrpcClientNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        assertFalse("hasDataAvailable should return false when grpcClient is not available",
            collector.hasDataAvailable());
    }

    @Test
    public void testHasDataAvailableReturnsFalseForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector((ProxyAdminGrpcClient) null);

        assertFalse("hasDataAvailable should return false when grpcClient is null",
            nullCollector.hasDataAvailable());
    }

    // ==================== listClientInstances when not available ====================

    @Test
    public void testListClientInstancesReturnsEmptyWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc client is not available", result.isEmpty());
    }

    @Test
    public void testListClientInstancesReturnsEmptyWhenGrpcReturnsEmpty() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc returns empty list", result.isEmpty());
    }

    @Test
    public void testListClientInstancesWithEmptyOptionals() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when grpc returns empty", result.isEmpty());
    }

    @Test
    public void testListClientInstancesHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.of(TEST_TOPIC), Optional.empty());

        assertNotNull("Result should not be null after exception", result);
        assertTrue("Result should be empty when grpc throws exception", result.isEmpty());
    }

    // ==================== getClientInstance when not available ====================

    @Test
    public void testGetClientInstanceReturnsEmptyOptionalWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty when not available", result.isPresent());
    }

    @Test
    public void testGetClientInstanceWithNullClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(null);

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty for null clientId", result.isPresent());
    }

    @Test
    public void testGetClientInstanceWithEmptyClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance("");

        assertNotNull("Result should not be null", result);
        assertFalse("Result should be empty for empty clientId", result.isPresent());
    }

    @Test
    public void testGetClientInstanceHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);

        assertNotNull("Result should not be null after exception", result);
        assertFalse("Result should be empty when grpc throws exception", result.isPresent());
    }

    // ==================== getClientSubscriptions ====================

    @Test
    public void testGetClientSubscriptionsReturnsEmptyWhenNotAvailable() {
        when(mockGrpcClient.isAvailable()).thenReturn(false);

        List<SubscriptionInfo> result = collector.getClientSubscriptions(TEST_CLIENT_ID);

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty when not available", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsWithNullClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        List<SubscriptionInfo> result = collector.getClientSubscriptions(null);

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for null clientId", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsWithEmptyClientId() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);

        List<SubscriptionInfo> result = collector.getClientSubscriptions("");

        assertNotNull("Result should not be null", result);
        assertTrue("Result should be empty for empty clientId", result.isEmpty());
    }

    @Test
    public void testGetClientSubscriptionsHandlesException() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID))
            .thenThrow(new RuntimeException("gRPC connection failed"));

        List<SubscriptionInfo> result = collector.getClientSubscriptions(TEST_CLIENT_ID);

        assertNotNull("Result should not be null after exception", result);
        assertTrue("Result should be empty when grpc throws exception", result.isEmpty());
    }

    // ==================== Null grpcClient method behavior ====================

    @Test
    public void testListClientInstancesReturnsEmptyForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector((ProxyAdminGrpcClient) null);
        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            nullCollector.listClientInstances(Optional.of(TEST_TOPIC), Optional.of(TEST_GROUP));
        assertNotNull(result);
        assertTrue("Should return empty for null grpc client", result.isEmpty());
    }

    @Test
    public void testGetClientInstanceReturnsEmptyForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector((ProxyAdminGrpcClient) null);
        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            nullCollector.getClientInstance(TEST_CLIENT_ID);
        assertFalse("Should return empty for null grpc client", result.isPresent());
    }

    @Test
    public void testGetClientSubscriptionsReturnsEmptyForNullGrpcClient() {
        GrpcClientCollector nullCollector = new GrpcClientCollector((ProxyAdminGrpcClient) null);
        List<SubscriptionInfo> result = nullCollector.getClientSubscriptions(TEST_CLIENT_ID);
        assertNotNull(result);
        assertTrue("Should return empty for null grpc client", result.isEmpty());
    }

    // ==================== MultiProxy mode tests ====================

    @Test
    public void testHasDataAvailableReturnsTrueWhenMultiProxyAvailable() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.isAvailable()).thenReturn(true);
        when(mockMultiProxy.getClients()).thenReturn(Collections.emptyList());

        GrpcClientCollector multiCollector = new GrpcClientCollector(mockMultiProxy);
        assertTrue("hasDataAvailable should return true when multi-proxy available",
            multiCollector.hasDataAvailable());
    }

    @Test
    public void testSetMultiProxyClient() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.isAvailable()).thenReturn(true);

        collector.setMultiProxyClient(mockMultiProxy);
        assertNotNull("MultiProxyClient should be set", collector.getMultiProxyClient());
        assertTrue("hasDataAvailable should use multi-proxy", collector.hasDataAvailable());
    }

    // ==================== Proto conversion helpers ====================

    private apache.rocketmq.proxy.admin.v1.ClientInstance protoClient(String clientId, ClientRole role) {
        return apache.rocketmq.proxy.admin.v1.ClientInstance.newBuilder()
            .setClientId(clientId)
            .setLanguage(ClientLanguage.CLIENT_LANGUAGE_JAVA)
            .setClientVersion("5.0.0")
            .setAccessPoint("127.0.0.1:8081")
            .setConnectAt(1000L)
            .setLastActiveAt(2000L)
            .setRole(role)
            .setGroup("groupA")
            .addTopics("topicA")
            .build();
    }

    // ==================== convertToModel via listClientInstances ====================

    @Test
    public void testListClientInstancesConvertsProducerProto() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.singletonList(
                protoClient("client-1", ClientRole.CLIENT_ROLE_PRODUCER)));

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());

        assertEquals(1, result.size());
        org.apache.rocketmq.dashboard.model.ClientInstance model = result.get(0);
        assertEquals("client-1", model.getClientId());
        assertEquals(org.apache.rocketmq.dashboard.model.ClientInstance.ClientType.PRODUCER,
            model.getClientType());
        assertEquals(org.apache.rocketmq.dashboard.model.ClientInstance.ProtocolType.GRPC,
            model.getProtocolType());
        assertEquals("CLIENT_LANGUAGE_JAVA", model.getLanguage());
        assertEquals("5.0.0", model.getSdkVersion());
        assertEquals("127.0.0.1:8081", model.getClientAddress());
        assertEquals("127.0.0.1:8081", model.getEndpoint());
        assertEquals("groupA", model.getConsumerGroup());
        assertEquals(Collections.singletonList("topicA"), model.getTopics());
        assertTrue(model.isActive());
    }

    @Test
    public void testListClientInstancesConvertsConsumerRoles() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(java.util.Arrays.asList(
                protoClient("push", ClientRole.CLIENT_ROLE_PUSH_CONSUMER),
                protoClient("simple", ClientRole.CLIENT_ROLE_SIMPLE_CONSUMER),
                protoClient("unknown", ClientRole.CLIENT_ROLE_UNSPECIFIED)));

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());

        assertEquals(3, result.size());
        assertEquals(org.apache.rocketmq.dashboard.model.ClientInstance.ClientType.PUSH_CONSUMER,
            result.get(0).getClientType());
        assertEquals(org.apache.rocketmq.dashboard.model.ClientInstance.ClientType.SIMPLE_CONSUMER,
            result.get(1).getClientType());
        // Unspecified role falls back to PRODUCER
        assertEquals(org.apache.rocketmq.dashboard.model.ClientInstance.ClientType.PRODUCER,
            result.get(2).getClientType());
    }

    // ==================== convertDetailToModel via getClientInstance ====================

    @Test
    public void testGetClientInstanceConvertsDetailWithSettingsNetworkAuth() {
        ClientDetail detail = ClientDetail.newBuilder()
            .setClientInstance(protoClient(TEST_CLIENT_ID, ClientRole.CLIENT_ROLE_PUSH_CONSUMER))
            .setSettings(ClientSettings.newBuilder()
                .setFifo(false)
                .setReceiveBatchSize(32)
                .addSubscriptionTopics("topicA")
                .addSubscriptionTopics("topicB")
                .build())
            .setNetworkInfo(NetworkInfo.newBuilder()
                .setRemoteAddress("10.0.0.1:1234")
                .build())
            .setAuthStatus(AuthStatus.newBuilder()
                .setAuthenticated(false)
                .build())
            .build();
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID)).thenReturn(detail);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);

        assertTrue(result.isPresent());
        org.apache.rocketmq.dashboard.model.ClientInstance model = result.get();
        assertEquals(TEST_CLIENT_ID, model.getClientId());
        // fifo=false -> pop enabled
        assertEquals(Boolean.TRUE, model.getPopEnabled());
        assertEquals("32", model.getSettingsVersion());
        assertEquals(2, model.getSubscriptions().size());
        assertEquals("topicA", model.getSubscriptions().get(0).getTopic());
        assertEquals("10.0.0.1:1234", model.getEndpoint());
        // auth status overrides active flag
        assertFalse(model.isActive());
    }

    @Test
    public void testGetClientInstanceFifoDisablesPop() {
        ClientDetail detail = ClientDetail.newBuilder()
            .setClientInstance(protoClient(TEST_CLIENT_ID, ClientRole.CLIENT_ROLE_PRODUCER))
            .setSettings(ClientSettings.newBuilder().setFifo(true).build())
            .build();
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID)).thenReturn(detail);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.getClientInstance(TEST_CLIENT_ID);
        assertTrue(result.isPresent());
        assertEquals(Boolean.FALSE, result.get().getPopEnabled());
    }

    @Test
    public void testGetClientInstanceDetailWithoutClientInstanceReturnsEmpty() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID))
            .thenReturn(ClientDetail.newBuilder().build());

        assertFalse(collector.getClientInstance(TEST_CLIENT_ID).isPresent());
    }

    @Test
    public void testGetClientInstanceNullDetailReturnsEmpty() {
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID)).thenReturn(null);

        assertFalse(collector.getClientInstance(TEST_CLIENT_ID).isPresent());
    }

    // ==================== getClientSubscriptions success paths ====================

    @Test
    public void testGetClientSubscriptionsFromSettings() {
        ClientDetail detail = ClientDetail.newBuilder()
            .setClientInstance(protoClient(TEST_CLIENT_ID, ClientRole.CLIENT_ROLE_PUSH_CONSUMER))
            .setSettings(ClientSettings.newBuilder()
                .addSubscriptionTopics("topicA")
                .build())
            .build();
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID)).thenReturn(detail);

        List<SubscriptionInfo> subscriptions = collector.getClientSubscriptions(TEST_CLIENT_ID);
        assertEquals(1, subscriptions.size());
        assertEquals("topicA", subscriptions.get(0).getTopic());
        assertEquals("*", subscriptions.get(0).getSubExpression());
    }

    @Test
    public void testGetClientSubscriptionsWithoutSettingsReturnsEmpty() {
        ClientDetail detail = ClientDetail.newBuilder()
            .setClientInstance(protoClient(TEST_CLIENT_ID, ClientRole.CLIENT_ROLE_PUSH_CONSUMER))
            .build();
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.describeClient(TEST_CLIENT_ID)).thenReturn(detail);

        assertTrue(collector.getClientSubscriptions(TEST_CLIENT_ID).isEmpty());
    }

    // ==================== Multi-proxy success paths ====================

    @Test
    public void testListClientInstancesPrefersMultiProxy() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.isAvailable()).thenReturn(true);
        when(mockMultiProxy.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.singletonList(
                protoClient("multi-1", ClientRole.CLIENT_ROLE_PRODUCER)));
        collector.setMultiProxyClient(mockMultiProxy);

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());

        assertEquals(1, result.size());
        assertEquals("multi-1", result.get(0).getClientId());
        verify(mockGrpcClient, never()).listClients(isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    @Test
    public void testListClientInstancesMultiProxyExceptionReturnsEmpty() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.isAvailable()).thenReturn(true);
        when(mockMultiProxy.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenThrow(new RuntimeException("multi-proxy failed"));
        collector.setMultiProxyClient(mockMultiProxy);

        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            collector.listClientInstances(Optional.empty(), Optional.empty());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetClientInstancePrefersMultiProxy() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.isAvailable()).thenReturn(true);
        when(mockMultiProxy.describeClient(TEST_CLIENT_ID)).thenReturn(ClientDetail.newBuilder()
            .setClientInstance(protoClient(TEST_CLIENT_ID, ClientRole.CLIENT_ROLE_PRODUCER))
            .build());
        collector.setMultiProxyClient(mockMultiProxy);

        assertTrue(collector.getClientInstance(TEST_CLIENT_ID).isPresent());
        verify(mockGrpcClient, never()).describeClient(anyString());
    }

    @Test
    public void testMultiProxyConstructorUsesFirstClient() {
        MultiProxyAdminClient mockMultiProxy = org.mockito.Mockito.mock(MultiProxyAdminClient.class);
        when(mockMultiProxy.getClients())
            .thenReturn(Collections.singletonList(mockGrpcClient));
        when(mockMultiProxy.isAvailable()).thenReturn(false);
        when(mockGrpcClient.isAvailable()).thenReturn(true);
        when(mockGrpcClient.listClients(isNull(), isNull(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.emptyList());

        GrpcClientCollector multiCollector = new GrpcClientCollector(mockMultiProxy);
        // Multi-proxy unavailable -> falls back to first single client
        List<org.apache.rocketmq.dashboard.model.ClientInstance> result =
            multiCollector.listClientInstances(Optional.empty(), Optional.empty());
        assertNotNull(result);
        verify(mockGrpcClient).listClients(isNull(), isNull(), isNull(), anyInt(), anyInt());
    }
}
