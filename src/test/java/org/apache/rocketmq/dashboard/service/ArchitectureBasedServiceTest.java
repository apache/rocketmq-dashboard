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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.config.ArchitectureConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArchitectureBasedService}.
 *
 * <p>The abstract base is exercised through an anonymous subclass; protected
 * fields are assigned directly since the test lives in the same package.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ArchitectureBasedServiceTest {

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ArchitectureConfig.ArchitectureAdaptationManager adaptationManager;

    private ArchitectureBasedService service;

    private ClusterCapability capability(String version) {
        ClusterCapability capability = new ClusterCapability();
        capability.setArchitectureVersion(version);
        capability.setNamespaceSupported(true);
        capability.setLiteTopicSupported(true);
        capability.setPopConsumeSupported(true);
        capability.setGrpcClientSupported(true);
        capability.setAclV2Supported(true);
        capability.setExtendedCapabilities(new HashSet<>(Collections.singletonList("liteTopic")));
        return capability;
    }

    @Before
    public void setUp() {
        service = new ArchitectureBasedService() {
        };
        service.clusterProvider = clusterProvider;
        service.adminClient = adminClient;
        service.metadataProvider = metadataProvider;
        service.adaptationManager = null;
    }

    @Test
    public void testInitCachesCapability() throws Exception {
        ClusterCapability capability = capability("5.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);

        service.init();
        assertSame(capability, service.clusterCapability);
    }

    @Test
    public void testInitFallsBackToDefaultCapability() throws Exception {
        when(clusterProvider.getClusterCapability()).thenThrow(new RuntimeException("boom"));

        service.init();
        assertNotNull(service.clusterCapability);
        assertFalse(service.clusterCapability.isNamespaceSupported());
    }

    @Test
    public void testSupportsUsesAdaptationManagerFirst() {
        service.adaptationManager = adaptationManager;
        when(adaptationManager.getCurrentCapability()).thenReturn(capability("5.0"));

        assertTrue(service.supports("liteTopic"));
        assertFalse(service.supports("unknownCap"));
    }

    @Test
    public void testSupportsFallsBackToClusterProviderWhenManagerFails() throws Exception {
        service.adaptationManager = adaptationManager;
        when(adaptationManager.getCurrentCapability()).thenThrow(new RuntimeException("manager down"));
        when(clusterProvider.getClusterCapability()).thenReturn(capability("4.0"));

        assertTrue(service.supports("liteTopic"));
    }

    @Test
    public void testSupportsFallsBackToCachedCapability() throws Exception {
        when(clusterProvider.getClusterCapability()).thenThrow(new RuntimeException("provider down"));
        service.clusterCapability = capability("4.0");

        assertTrue(service.supports("liteTopic"));
    }

    @Test
    public void testSupportsReturnsFalseWhenNoCapabilityAvailable() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(null);
        service.clusterCapability = null;

        assertFalse(service.supports("liteTopic"));
    }

    @Test
    public void testCapabilityFlagAccessors() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capability("5.0"));

        assertTrue(service.supportsNamespace());
        assertTrue(service.supportsLiteTopic());
        assertTrue(service.supportsPopConsume());
        assertTrue(service.supportsGrpcClient());
        assertTrue(service.supportsAclV2());
    }

    @Test
    public void testCapabilityFlagAccessorsWithNullCapability() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(null);
        service.clusterCapability = null;

        assertFalse(service.supportsNamespace());
        assertFalse(service.supportsLiteTopic());
        assertFalse(service.supportsPopConsume());
        assertFalse(service.supportsGrpcClient());
        assertFalse(service.supportsAclV2());
    }

    @Test
    public void testArchitectureVersionChecks() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capability("4.0"));
        assertTrue(service.isV4Architecture());
        assertFalse(service.isV5Architecture());
    }

    @Test
    public void testIsV5Architecture() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capability("5.0"));
        assertTrue(service.isV5Architecture());
        assertFalse(service.isV4Architecture());
    }

    @Test
    public void testGetClusterTopologyDelegates() throws Exception {
        ClusterTopology topology = new ClusterTopology();
        when(clusterProvider.getClusterTopology()).thenReturn(topology);

        assertSame(topology, service.getClusterTopology());
    }

    @Test
    public void testGetClusterCapabilityResolvesLiveValue() throws Exception {
        ClusterCapability capability = capability("5.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);

        assertSame(capability, service.getClusterCapability());
        // Live capability also refreshes the cached field
        assertSame(capability, service.clusterCapability);
    }

    @Test
    public void testGetMetadataProviderPrefersAdaptationManager() {
        MetadataProvider managed = org.mockito.Mockito.mock(MetadataProvider.class);
        service.adaptationManager = adaptationManager;
        when(adaptationManager.getCurrentCapability()).thenReturn(capability("5.0"));
        when(adaptationManager.getMetadataProvider()).thenReturn(managed);

        assertSame(managed, service.getMetadataProvider());
    }

    @Test
    public void testGetMetadataProviderFallsBackToField() {
        service.adaptationManager = adaptationManager;
        when(adaptationManager.getCurrentCapability()).thenReturn(null);

        assertSame(metadataProvider, service.getMetadataProvider());
    }

    @Test
    public void testGetMetadataProviderWithoutManager() {
        assertSame(metadataProvider, service.getMetadataProvider());
    }

    @Test
    public void testGetClusterProviderPrefersAdaptationManager() {
        ClusterProvider managed = org.mockito.Mockito.mock(ClusterProvider.class);
        service.adaptationManager = adaptationManager;
        when(adaptationManager.getCurrentCapability()).thenReturn(capability("5.0"));
        when(adaptationManager.getClusterProvider()).thenReturn(managed);

        assertSame(managed, service.getClusterProvider());
    }

    @Test
    public void testGetClusterProviderWithoutManager() {
        assertSame(clusterProvider, service.getClusterProvider());
    }

    @Test
    public void testHandleUnsupportedOperationIncludesVersion() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(capability("4.0"));

        try {
            service.handleUnsupportedOperation("liteTopic");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("liteTopic"));
            assertTrue(e.getMessage().contains("4.0"));
        }
    }

    @Test
    public void testHandleUnsupportedOperationUnknownVersion() throws Exception {
        when(clusterProvider.getClusterCapability()).thenReturn(null);
        service.clusterCapability = null;

        try {
            service.handleUnsupportedOperation("liteTopic");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("unknown"));
        }
    }

    @Test
    public void testGetDefaultNamespace() {
        assertEquals("DEFAULT", service.getDefaultNamespace());
    }
}
