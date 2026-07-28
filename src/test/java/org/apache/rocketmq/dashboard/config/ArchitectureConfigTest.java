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
package org.apache.rocketmq.dashboard.config;

import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.architecture.impl.GrpcAdminClient;
import org.apache.rocketmq.dashboard.architecture.impl.RemotingAdminClient;
import org.apache.rocketmq.dashboard.architecture.impl.V4ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V4MetadataProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V5ProxyClusterProvider;
import org.apache.rocketmq.dashboard.architecture.impl.V5ProxyMetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.service.client.GrpcClientCollector;
import org.apache.rocketmq.dashboard.service.client.MultiProxyAdminClient;
import org.apache.rocketmq.dashboard.service.client.ProxyAdminGrpcClient;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ArchitectureConfig and ArchitectureAdaptationManager.
 *
 * <p>Covers RIP-1 ARCH-01 requirements:
 * <ul>
 *   <li>Architecture switching between V4 and V5 at runtime</li>
 *   <li>Cluster capability detection</li>
 *   <li>Provider/Client/Metadata caching</li>
 *   <li>Graceful fallback on detection failure</li>
 * </ul>
 * </p>
 */
public class ArchitectureConfigTest {

    private MQAdminExt mqAdminExt;
    private AutoCloseConsumerWrapper consumerWrapper;
    private RMQConfigure rmqConfigure;
    private ArchitectureConfig.ClusterCapabilityDetector capabilityDetector;
    private ArchitectureConfig.ArchitectureAdaptationManager adaptationManager;

    @Before
    public void setUp() {
        mqAdminExt = mock(MQAdminExt.class);
        consumerWrapper = mock(AutoCloseConsumerWrapper.class);
        rmqConfigure = mock(RMQConfigure.class);
        ArchitectureConfig config = new ArchitectureConfig();
        capabilityDetector = config.clusterCapabilityDetector();
        adaptationManager = config.architectureAdaptationManager(mqAdminExt, consumerWrapper, rmqConfigure);
    }

    // ==================== ClusterCapabilityDetector Tests ====================

    @Test
    public void testDetectCapability_V4Cluster_ReturnsV4Defaults() {
        ClusterProvider v4Provider = new V4ClusterProvider(mqAdminExt);
        ClusterCapability capability = capabilityDetector.detectCapability(v4Provider);

        assertNotNull(capability);
        assertEquals("4.0", capability.getArchitectureVersion());
        assertFalse(capability.isNamespaceSupported());
        assertFalse(capability.isLiteTopicSupported());
        assertFalse(capability.isPopConsumeSupported());
        assertFalse(capability.isGrpcClientSupported());
        assertFalse(capability.isAclV2Supported());
    }

    @Test
    public void testDetectCapability_V5ProxyCluster_ReturnsV5Capabilities() {
        ClusterProvider v5Provider = new V5ProxyClusterProvider(
            new String[]{"localhost:8080"}, "localhost:9876");
        ClusterCapability capability = capabilityDetector.detectCapability(v5Provider);

        assertNotNull(capability);
        assertEquals("5.0", capability.getArchitectureVersion());
        assertTrue(capability.isNamespaceSupported());
        assertTrue(capability.isLiteTopicSupported());
        assertTrue(capability.isPopConsumeSupported());
        assertTrue(capability.isGrpcClientSupported());
        assertTrue(capability.isAclV2Supported());
    }

    @Test
    public void testDetectCapability_FallbackOnError_ReturnsV4Defaults() {
        ClusterProvider failingProvider = new ClusterProvider() {
            @Override
            public ClusterAccessType getAccessType() { return ClusterAccessType.V4_NAMESRV; }
            @Override
            public org.apache.rocketmq.dashboard.model.ClusterTopology getClusterTopology() throws Exception {
                return null;
            }
            @Override
            public ClusterCapability getClusterCapability() {
                throw new RuntimeException("Simulated failure");
            }
            @Override
            public java.util.List<String> getNodeList() throws Exception { return java.util.Collections.emptyList(); }
            @Override
            public boolean isClusterHealthy() throws Exception { return true; }
            @Override
            public void initialize() throws Exception {}
            @Override
            public void shutdown() {}
        };

        ClusterCapability fallback = capabilityDetector.detectCapability(failingProvider);
        assertNotNull(fallback);
        assertEquals("4.0", fallback.getArchitectureVersion());
        assertFalse(fallback.isNamespaceSupported());
    }

    // ==================== ArchitectureAdaptationManager Tests ====================

    @Test
    public void testInitialState_IsV4Namesrv() {
        assertEquals(ClusterAccessType.V4_NAMESRV, adaptationManager.getCurrentAccessType());
        assertNotNull(adaptationManager.getClusterProvider());
        assertNotNull(adaptationManager.getAdminClient());
        assertNotNull(adaptationManager.getMetadataProvider());
        assertTrue(adaptationManager.getClusterProvider() instanceof V4ClusterProvider);
        assertTrue(adaptationManager.getAdminClient() instanceof RemotingAdminClient);
        assertTrue(adaptationManager.getMetadataProvider() instanceof V4MetadataProvider);
    }

    @Test
    public void testGetCurrentCapability_ReturnsCapability() {
        ClusterCapability cap = adaptationManager.getCurrentCapability();
        assertNotNull(cap);
    }

    @Test
    public void testSwitchToSameArchitecture_NoOp() {
        adaptationManager.switchToArchitecture(ClusterAccessType.V4_NAMESRV);
        assertEquals(ClusterAccessType.V4_NAMESRV, adaptationManager.getCurrentAccessType());
    }

    @Test
    public void testSwitchToV5Proxy_RequiresV5AccessType() {
        try {
            adaptationManager.switchToV5Proxy(ClusterAccessType.V4_NAMESRV,
                new String[]{"localhost:8080"}, "localhost:9876", Optional.empty());
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("V5 architecture"));
        }
    }

    @Test
    public void testSetters_UpdateCurrentComponents() {
        ClusterProvider customProvider = new V4ClusterProvider(mqAdminExt);
        AdminClient customClient = new RemotingAdminClient(mqAdminExt);
        MetadataProvider customMetadata = new V4MetadataProvider(mqAdminExt);

        adaptationManager.setClusterProvider(customProvider);
        adaptationManager.setAdminClient(customClient);
        adaptationManager.setMetadataProvider(customMetadata);

        assertEquals(customProvider, adaptationManager.getClusterProvider());
        assertEquals(customClient, adaptationManager.getAdminClient());
        assertEquals(customMetadata, adaptationManager.getMetadataProvider());
    }

    // ==================== ProxyAdminGrpcClient Bean Tests ====================

    @Test
    public void testProxyAdminGrpcClient_Creation_WithDefaultPort() {
        ProxyAdminGrpcClient client = new ProxyAdminGrpcClient("localhost:8080");
        assertNotNull(client);
        assertFalse("Should not be available before connection", client.isAvailable());
    }

    @Test
    public void testProxyAdminGrpcClient_Creation_WithExplicitPort() {
        ProxyAdminGrpcClient client = new ProxyAdminGrpcClient("192.168.1.1:8080", 8086);
        assertNotNull(client);
        assertFalse(client.isAvailable());
    }

    @Test
    public void testProxyAdminGrpcClient_ExtractHost_ParsesCorrectly() {
        ProxyAdminGrpcClient client1 = new ProxyAdminGrpcClient("10.0.0.1:8080", 8086);
        assertNotNull(client1);

        ProxyAdminGrpcClient client2 = new ProxyAdminGrpcClient("myproxy.example.com:8080");
        assertNotNull(client2);

        ProxyAdminGrpcClient client3 = new ProxyAdminGrpcClient("localhost");
        assertNotNull(client3);
    }

    @Test
    public void testProxyAdminGrpcClient_Shutdown_DisablesAvailability() {
        ProxyAdminGrpcClient client = new ProxyAdminGrpcClient("localhost:8080");
        client.shutdown();
        assertFalse("Should not be available after shutdown", client.isAvailable());
    }

    // ==================== GrpcClientCollector Bean Tests ====================

    @Test
    public void testGrpcClientCollector_Creation() {
        ProxyAdminGrpcClient grpcClient = new ProxyAdminGrpcClient("localhost:8080");
        GrpcClientCollector collector = new GrpcClientCollector(grpcClient);
        assertNotNull(collector);
        assertFalse("Should report no data when gRPC is not available", collector.hasDataAvailable());
    }

    @Test
    public void testGrpcClientCollector_ListClients_WhenNotAvailable_ReturnsEmpty() {
        ProxyAdminGrpcClient grpcClient = new ProxyAdminGrpcClient("localhost:8080");
        GrpcClientCollector collector = new GrpcClientCollector(grpcClient);

        java.util.List<org.apache.rocketmq.dashboard.model.ClientInstance> clients =
            collector.listClientInstances(Optional.empty(), Optional.empty());
        assertTrue("Should return empty list when gRPC not available", clients.isEmpty());
    }

    @Test
    public void testGrpcClientCollector_GetClient_WhenNotAvailable_ReturnsEmpty() {
        ProxyAdminGrpcClient grpcClient = new ProxyAdminGrpcClient("localhost:8080");
        GrpcClientCollector collector = new GrpcClientCollector(grpcClient);

        Optional<org.apache.rocketmq.dashboard.model.ClientInstance> client =
            collector.getClientInstance("test-client");
        assertFalse(client.isPresent());
    }

    @Test
    public void testGrpcClientCollector_GetSubscriptions_WhenNotAvailable_ReturnsEmpty() {
        ProxyAdminGrpcClient grpcClient = new ProxyAdminGrpcClient("localhost:8080");
        GrpcClientCollector collector = new GrpcClientCollector(grpcClient);

        java.util.List<org.apache.rocketmq.dashboard.model.SubscriptionInfo> subs =
            collector.getClientSubscriptions("test-client");
        assertTrue(subs.isEmpty());
    }

    // ==================== GrpcAdminClient Constructor Tests ====================

    @Test
    public void testGrpcAdminClient_WithoutGrpc_grpcAvailableFalse() {
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt);
        assertFalse(client.isGrpcAvailable());
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, client.getClientType());
        assertEquals("localhost:8080", client.getProxyAddress());
    }

    @Test
    public void testGrpcAdminClient_WithGrpc_grpcAvailableTrue() {
        ProxyAdminGrpcClient grpcClient = new ProxyAdminGrpcClient("localhost:8080");
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt, grpcClient);
        assertTrue(client.isGrpcAvailable());
    }

    @Test
    public void testGrpcAdminClient_WithNullGrpc_grpcAvailableFalse() {
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt, null);
        assertFalse(client.isGrpcAvailable());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrpcAdminClient_NullProxyAddress_ThrowsException() {
        new GrpcAdminClient(null, mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrpcAdminClient_EmptyProxyAddress_ThrowsException() {
        new GrpcAdminClient("", mqAdminExt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGrpcAdminClient_NullMqAdminExt_ThrowsException() {
        new GrpcAdminClient("localhost:8080", null);
    }

    @Test
    public void testGrpcAdminClient_Shutdown_PreventsOperations() {
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt);
        client.shutdown();

        try {
            client.getClusterInfo();
            fail("Should throw IllegalStateException after shutdown");
        } catch (IllegalStateException e) {
            // Expected
        } catch (Exception e) {
            fail("Expected IllegalStateException, got " + e.getClass().getSimpleName());
        }
    }

    @Test
    public void testGrpcAdminClient_GetAccessControlList_ThrowsUnsupported() {
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt);
        try {
            client.getAccessControlList("localhost:10911");
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        } catch (Exception e) {
            fail("Expected UnsupportedOperationException");
        }
    }

    @Test
    public void testGrpcAdminClient_UpdateAccessControlList_ThrowsUnsupported() {
        GrpcAdminClient client = new GrpcAdminClient("localhost:8080", mqAdminExt);
        try {
            client.updateAccessControlList("localhost:10911", null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        } catch (Exception e) {
            fail("Expected UnsupportedOperationException");
        }
    }

    // ==================== V5ProxyClusterProvider Tests ====================

    @Test
    public void testV5ProxyClusterProvider_GetAccessType() {
        V5ProxyClusterProvider provider = new V5ProxyClusterProvider(
            new String[]{"localhost:8080"}, "localhost:9876");
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, provider.getAccessType());
    }

    @Test
    public void testV5ProxyClusterProvider_GetClusterCapability_AllV5Features() {
        V5ProxyClusterProvider provider = new V5ProxyClusterProvider(
            new String[]{"localhost:8080"}, "localhost:9876");
        ClusterCapability capability = provider.getClusterCapability();

        assertEquals("5.0", capability.getArchitectureVersion());
        assertTrue(capability.isNamespaceSupported());
        assertTrue(capability.isLiteTopicSupported());
        assertTrue(capability.isPopConsumeSupported());
        assertTrue(capability.isAclV2Supported());
        assertTrue(capability.isGrpcClientSupported());
        assertTrue(capability.isDelayMessageSupported());
        assertTrue(capability.isTransactionMessageSupported());
        assertTrue(capability.isFifoMessageSupported());
        assertEquals("5.x", capability.getRocketmqVersion());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testV5ProxyClusterProvider_EmptyProxyAddresses_ThrowsException() {
        new V5ProxyClusterProvider(new String[]{}, "localhost:9876");
    }

    @Test
    public void testV5ProxyClusterProvider_GetNodeList_ReturnsProxyAddresses() throws Exception {
        V5ProxyClusterProvider provider = new V5ProxyClusterProvider(
            new String[]{"proxy1:8080", "proxy2:8080"}, "localhost:9876");
        java.util.List<String> nodes = provider.getNodeList();
        assertEquals(2, nodes.size());
        assertTrue(nodes.contains("proxy1:8080"));
        assertTrue(nodes.contains("proxy2:8080"));
    }

    // ==================== Bean Factory Method Tests ====================

    @Test
    public void testBeanFactoryMethods_CreateExpectedTypes() {
        ArchitectureConfig config = new ArchitectureConfig();

        AdminClient adminClient = config.remotingAdminClient(mqAdminExt);
        assertTrue(adminClient instanceof RemotingAdminClient);

        ClusterProvider clusterProvider = config.v4ClusterProvider(mqAdminExt);
        assertTrue(clusterProvider instanceof V4ClusterProvider);

        MetadataProvider metadataProvider = config.v4MetadataProvider(mqAdminExt, consumerWrapper, rmqConfigure);
        assertTrue(metadataProvider instanceof V4MetadataProvider);
    }

    @Test
    public void testProxyAdminGrpcClientBean() {
        ArchitectureConfig config = new ArchitectureConfig();
        ProxyAdminGrpcClient client = config.proxyAdminGrpcClient("localhost:8080", 8086);
        assertNotNull(client);
        assertFalse(client.isAvailable());
    }

    @Test
    public void testMultiProxyAdminClientBean_WithProxyAddresses() {
        ArchitectureConfig config = new ArchitectureConfig();
        MultiProxyAdminClient client = config.multiProxyAdminClient(
            new String[]{"proxy1:8080", "proxy2:8080"}, "localhost:8080", 8086);
        assertNotNull(client);
    }

    @Test
    public void testMultiProxyAdminClientBean_FallbackToSingleAddress() {
        ArchitectureConfig config = new ArchitectureConfig();
        MultiProxyAdminClient fromNull = config.multiProxyAdminClient(null, "localhost:8080", 8086);
        assertNotNull(fromNull);

        MultiProxyAdminClient fromEmpty = config.multiProxyAdminClient(new String[]{}, "localhost:8080", 8086);
        assertNotNull(fromEmpty);
    }

    @Test
    public void testGrpcClientCollectorBean_WithMultiProxyAdminClient() {
        ArchitectureConfig config = new ArchitectureConfig();
        MultiProxyAdminClient multiProxyAdminClient = config.multiProxyAdminClient(
            new String[]{"localhost:8080"}, "localhost:8080", 8086);
        GrpcClientCollector collector = config.grpcClientCollector(multiProxyAdminClient);
        assertNotNull(collector);
    }

    // ==================== switchToArchitecture Failure Tests ====================

    @Test
    public void testSwitchToArchitecture_NullAccessType_Throws() {
        try {
            adaptationManager.switchToArchitecture(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("AccessType must not be null"));
        }
    }

    @Test
    public void testSwitchToArchitecture_V5WithoutProxyAddresses_Throws() {
        try {
            adaptationManager.switchToArchitecture(ClusterAccessType.V5_PROXY_CLUSTER);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("requires explicit proxy addresses"));
        }
    }

    // ==================== switchToArchitecture V5 -> V4 success path ====================

    @Test
    public void testSwitchToArchitecture_FromV5BackToV4_CreatesV4Components() {
        ClusterProvider oldProvider = mock(ClusterProvider.class);
        org.springframework.test.util.ReflectionTestUtils.setField(
            adaptationManager, "currentAccessType", ClusterAccessType.V5_PROXY_LOCAL);
        adaptationManager.setClusterProvider(oldProvider);

        adaptationManager.switchToArchitecture(ClusterAccessType.V4_NAMESRV);

        // old provider is not cached, so it must be shut down
        verify(oldProvider).shutdown();
        assertEquals(ClusterAccessType.V4_NAMESRV, adaptationManager.getCurrentAccessType());
        assertTrue(adaptationManager.getClusterProvider() instanceof V4ClusterProvider);
        assertTrue(adaptationManager.getAdminClient() instanceof RemotingAdminClient);
        assertTrue(adaptationManager.getMetadataProvider() instanceof V4MetadataProvider);
    }

    @Test
    public void testSwitchToArchitecture_OldProviderShutdownFailure_IsSwallowed() {
        ClusterProvider oldProvider = mock(ClusterProvider.class);
        doThrow(new RuntimeException("shutdown boom")).when(oldProvider).shutdown();
        org.springframework.test.util.ReflectionTestUtils.setField(
            adaptationManager, "currentAccessType", ClusterAccessType.V5_PROXY_LOCAL);
        adaptationManager.setClusterProvider(oldProvider);

        adaptationManager.switchToArchitecture(ClusterAccessType.V4_NAMESRV);

        verify(oldProvider).shutdown();
        assertEquals(ClusterAccessType.V4_NAMESRV, adaptationManager.getCurrentAccessType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSwitchToArchitecture_CachedOldProvider_IsNotShutDown() {
        ClusterProvider oldProvider = mock(ClusterProvider.class);
        java.util.concurrent.ConcurrentHashMap<ClusterAccessType, ClusterProvider> providerCache =
            (java.util.concurrent.ConcurrentHashMap<ClusterAccessType, ClusterProvider>)
                org.springframework.test.util.ReflectionTestUtils.getField(adaptationManager, "providerCache");
        providerCache.put(ClusterAccessType.V5_PROXY_LOCAL, oldProvider);
        org.springframework.test.util.ReflectionTestUtils.setField(
            adaptationManager, "currentAccessType", ClusterAccessType.V5_PROXY_LOCAL);
        adaptationManager.setClusterProvider(oldProvider);

        adaptationManager.switchToArchitecture(ClusterAccessType.V4_NAMESRV);

        // cached provider must be reusable, so no shutdown
        verify(oldProvider, never()).shutdown();
        assertEquals(ClusterAccessType.V4_NAMESRV, adaptationManager.getCurrentAccessType());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSwitchToArchitecture_ProviderInitializeFailure_ThrowsRuntimeException() throws Exception {
        ClusterProvider failingProvider = mock(ClusterProvider.class);
        doThrow(new RuntimeException("init boom")).when(failingProvider).initialize();
        java.util.concurrent.ConcurrentHashMap<ClusterAccessType, ClusterProvider> providerCache =
            (java.util.concurrent.ConcurrentHashMap<ClusterAccessType, ClusterProvider>)
                org.springframework.test.util.ReflectionTestUtils.getField(adaptationManager, "providerCache");
        providerCache.put(ClusterAccessType.V4_NAMESRV, failingProvider);
        org.springframework.test.util.ReflectionTestUtils.setField(
            adaptationManager, "currentAccessType", ClusterAccessType.V5_PROXY_LOCAL);

        try {
            adaptationManager.switchToArchitecture(ClusterAccessType.V4_NAMESRV);
            fail("Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Provider initialization failed"));
        }
    }

    @Test
    public void testSwitchToArchitecture_UnsupportedCloudType_Throws() {
        try {
            adaptationManager.switchToArchitecture(ClusterAccessType.CLOUD_ALIYUN);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Unsupported access type"));
        }
    }

    // ==================== getCurrentCapability failure branches ====================

    @Test
    public void testGetCurrentCapability_ProviderThrows_ReturnsEmptyCapability() throws Exception {
        ClusterProvider failingProvider = mock(ClusterProvider.class);
        when(failingProvider.getClusterCapability()).thenThrow(new RuntimeException("capability boom"));
        adaptationManager.setClusterProvider(failingProvider);

        ClusterCapability capability = adaptationManager.getCurrentCapability();
        assertNotNull("Should fall back to empty capability on failure", capability);
    }

    @Test
    public void testGetCurrentCapability_NullProvider_ReturnsEmptyCapability() {
        adaptationManager.setClusterProvider(null);

        ClusterCapability capability = adaptationManager.getCurrentCapability();
        assertNotNull("Should return empty capability when provider is null", capability);
    }
}
