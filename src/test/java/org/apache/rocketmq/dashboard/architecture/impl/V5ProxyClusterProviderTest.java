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

import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link V5ProxyClusterProvider}.
 *
 * <p>Network-touching initialization ({@code initialize()} creates and starts a real
 * DefaultMQAdminExt) is bypassed by presetting the {@code mqAdminExt} and
 * {@code initialized} fields via reflection.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class V5ProxyClusterProviderTest {

    @Mock
    private MQAdminExt mqAdminExt;

    private V5ProxyClusterProvider provider;

    @Before
    public void setUp() {
        provider = new V5ProxyClusterProvider(
            new String[] {"proxy1:8080", "proxy2:8080"}, "127.0.0.1:9876", Optional.of("ns-test"));
        // Bypass initialize() which would start a real DefaultMQAdminExt
        ReflectionTestUtils.setField(provider, "mqAdminExt", mqAdminExt);
        ReflectionTestUtils.setField(provider, "initialized", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorEmptyProxyAddressesThrows() {
        new V5ProxyClusterProvider(new String[] {}, "127.0.0.1:9876");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNullNameSrvThrows() {
        new V5ProxyClusterProvider(new String[] {"proxy1:8080"}, null);
    }

    @Test
    public void testGetAccessType() {
        assertEquals(ClusterAccessType.V5_PROXY_CLUSTER, provider.getAccessType());
    }

    @Test
    public void testGetClusterTopologyWithBrokers() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        ClusterTopology topology = provider.getClusterTopology();
        assertNotNull(topology);
        // Cluster name inferred from BrokerAddrTable keys
        assertEquals("broker-a", topology.getClusterName());
        assertTrue(topology.getNamesrvAddresses().contains("127.0.0.1:9876"));
        assertEquals(1, topology.getBrokerNodes().size());
        assertEquals("ONLINE", topology.getBrokerNodes().get(0).getStatus());
        // Two proxy nodes with ONLINE status
        assertEquals(2, topology.getProxyNodes().size());
        assertEquals("ONLINE", topology.getProxyNodes().get(0).getStatus());
        assertEquals("proxy1:8080", topology.getProxyNodes().get(0).getNodeAddress());
        assertEquals(3, topology.getTotalNodeCount());
    }

    @Test
    public void testGetClusterTopologyEmptyBrokerTable() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(new ClusterInfo());
        ClusterTopology topology = provider.getClusterTopology();
        assertEquals("v5-proxy-cluster", topology.getClusterName());
        assertTrue(topology.getBrokerNodes().isEmpty());
        assertEquals(2, topology.getProxyNodes().size());
    }

    @Test
    public void testGetClusterCapability() {
        assertEquals("5.0", provider.getClusterCapability().getArchitectureVersion());
        assertTrue(provider.getClusterCapability().isNamespaceSupported());
        assertTrue(provider.getClusterCapability().isLiteTopicSupported());
        assertTrue(provider.getClusterCapability().isPopConsumeSupported());
        assertTrue(provider.getClusterCapability().isAclV2Supported());
        assertTrue(provider.getClusterCapability().isGrpcClientSupported());
        assertTrue(provider.getClusterCapability().isRouteEventsSupported());
        assertEquals("5.x", provider.getClusterCapability().getRocketmqVersion());
        assertTrue(provider.getClusterCapability().getExtendedCapabilities().contains("liteTopic"));
        assertTrue(provider.getClusterCapability().getExtendedCapabilities().contains("popConsume"));
    }

    @Test
    public void testGetNodeList() throws Exception {
        List<String> nodes = provider.getNodeList();
        assertEquals(2, nodes.size());
        assertTrue(nodes.contains("proxy1:8080"));
        assertTrue(nodes.contains("proxy2:8080"));
    }

    @Test
    public void testIsClusterHealthyAllReachable() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        assertTrue(provider.isClusterHealthy());
    }

    @Test
    public void testIsClusterHealthyFalseWhenUnreachable() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("unreachable"));
        assertFalse(provider.isClusterHealthy());
    }

    @Test
    public void testGetMqAdminExtAccessor() {
        assertEquals(mqAdminExt, provider.getMqAdminExt());
    }

    @Test
    public void testShutdownReleasesResources() {
        provider.shutdown();
        verify(mqAdminExt).shutdown();
        assertNull(provider.getMqAdminExt());
    }

    @Test
    public void testShutdownSwallowsException() {
        doThrow(new RuntimeException("shutdown failed")).when(mqAdminExt).shutdown();
        provider.shutdown();
        assertNull(provider.getMqAdminExt());
    }

    @Test
    public void testShutdownWithoutAdminExt() {
        ReflectionTestUtils.setField(provider, "mqAdminExt", null);
        provider.shutdown();
        assertNull(provider.getMqAdminExt());
    }
}
