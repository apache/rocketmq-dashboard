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
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link V4ClusterProvider}.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class V4ClusterProviderTest {

    @Mock
    private MQAdminExt mqAdminExt;

    private V4ClusterProvider provider;

    @Before
    public void setUp() {
        provider = new V4ClusterProvider(mqAdminExt);
    }

    @Test
    public void testGetAccessType() {
        assertEquals(ClusterAccessType.V4_NAMESRV, provider.getAccessType());
    }

    @Test
    public void testGetClusterTopology() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        ClusterTopology topology = provider.getClusterTopology();
        assertNotNull(topology);
        assertEquals("default-cluster", topology.getClusterName());
        assertEquals(1, topology.getBrokerNodes().size());
        ClusterTopology.NodeInfo node = topology.getBrokerNodes().get(0);
        assertEquals("broker-a", node.getNodeName());
        assertEquals(Long.valueOf(0L), node.getNodeId());
        assertEquals("127.0.0.1:10911", node.getNodeAddress());
        assertEquals("BROKER", node.getNodeType());
        assertTrue(node.isMaster());
        assertEquals(1, topology.getMasterBrokerCount());
        assertEquals(0, topology.getSlaveBrokerCount());
        assertFalse(topology.getNamesrvAddresses().isEmpty());
    }

    @Test
    public void testGetClusterCapability() throws Exception {
        ClusterCapability capability = provider.getClusterCapability();
        assertEquals("4.0", capability.getArchitectureVersion());
        assertFalse(capability.isNamespaceSupported());
        assertFalse(capability.isLiteTopicSupported());
        assertFalse(capability.isPopConsumeSupported());
        assertFalse(capability.isGrpcClientSupported());
        assertFalse(capability.isAclV2Supported());
        assertTrue(capability.isDelayMessageSupported());
        assertTrue(capability.isTransactionMessageSupported());
        assertTrue(capability.isFifoMessageSupported());
        assertTrue(capability.getExtendedCapabilities().contains("MESSAGE_QUERY"));
        assertTrue(capability.getExtendedCapabilities().contains("TOPIC_CREATE"));
        assertTrue(capability.getExtendedCapabilities().contains("METRICS_EXPORT"));
    }

    @Test
    public void testGetNodeList() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        List<String> nodes = provider.getNodeList();
        assertEquals(1, nodes.size());
        assertTrue(nodes.contains("broker-a"));
    }

    @Test
    public void testIsClusterHealthyTrue() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        assertTrue(provider.isClusterHealthy());
    }

    @Test
    public void testIsClusterHealthyFalseOnException() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("namesrv unreachable"));
        assertFalse(provider.isClusterHealthy());
    }

    @Test
    public void testInitializeAndShutdownAreNoOps() throws Exception {
        provider.initialize();
        provider.shutdown();
    }
}
