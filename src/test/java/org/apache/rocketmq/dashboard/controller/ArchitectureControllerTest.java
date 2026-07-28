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

import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.config.ArchitectureConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.dashboard.model.request.ArchitectureSwitchRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ArchitectureControllerTest {

    @InjectMocks
    private ArchitectureController architectureController;

    @Mock
    private ArchitectureConfig.ArchitectureAdaptationManager adaptationManager;

    @Mock
    private ArchitectureConfig.ClusterCapabilityDetector capabilityDetector;

    @Mock
    private ClusterProvider clusterProvider;

    // ==================== getCapabilities ====================

    @Test
    public void testGetCapabilities() {
        ClusterCapability capability = mock(ClusterCapability.class);
        when(adaptationManager.getCurrentCapability()).thenReturn(capability);

        ResponseEntity<ClusterCapability> response = architectureController.getCapabilities();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(capability, response.getBody());
    }

    // ==================== getArchitectureInfo ====================

    @Test
    public void testGetArchitectureInfoWithoutProvider() {
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V4_NAMESRV);
        when(adaptationManager.getClusterProvider()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = architectureController.getArchitectureInfo();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(ClusterAccessType.V4_NAMESRV, response.getBody().get("accessType"));
        assertTrue(!response.getBody().containsKey("topology"));
    }

    @Test
    public void testGetArchitectureInfoWithProvider() throws Exception {
        ClusterTopology topology = mock(ClusterTopology.class);
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V5_PROXY_LOCAL);
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getClusterTopology()).thenReturn(topology);
        when(clusterProvider.isClusterHealthy()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = architectureController.getArchitectureInfo();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(topology, response.getBody().get("topology"));
        assertEquals(Boolean.TRUE, response.getBody().get("healthy"));
    }

    @Test
    public void testGetArchitectureInfoProviderThrows() throws Exception {
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V4_NAMESRV);
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getClusterTopology()).thenThrow(new RuntimeException("topo err"));
        when(clusterProvider.isClusterHealthy()).thenThrow(new RuntimeException("health err"));

        ResponseEntity<Map<String, Object>> response = architectureController.getArchitectureInfo();
        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().get("topology"));
        assertEquals(Boolean.FALSE, response.getBody().get("healthy"));
    }

    // ==================== getTopology ====================

    @Test
    public void testGetTopologyNoProvider() {
        when(adaptationManager.getClusterProvider()).thenReturn(null);

        ResponseEntity<ClusterTopology> response = architectureController.getTopology();
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void testGetTopology() throws Exception {
        ClusterTopology topology = mock(ClusterTopology.class);
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getClusterTopology()).thenReturn(topology);

        ResponseEntity<ClusterTopology> response = architectureController.getTopology();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(topology, response.getBody());
    }

    @Test
    public void testGetTopologyError() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getClusterTopology()).thenThrow(new RuntimeException("err"));

        ResponseEntity<ClusterTopology> response = architectureController.getTopology();
        assertEquals(500, response.getStatusCode().value());
    }

    // ==================== getNodes ====================

    @Test
    public void testGetNodesNoProvider() {
        when(adaptationManager.getClusterProvider()).thenReturn(null);

        ResponseEntity<List<?>> response = architectureController.getNodes();
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void testGetNodes() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getNodeList()).thenReturn(Collections.emptyList());

        ResponseEntity<List<?>> response = architectureController.getNodes();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    public void testGetNodesError() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.getNodeList()).thenThrow(new RuntimeException("err"));

        ResponseEntity<List<?>> response = architectureController.getNodes();
        assertEquals(500, response.getStatusCode().value());
    }

    // ==================== checkHealth ====================

    @Test
    public void testCheckHealthNoProvider() {
        when(adaptationManager.getClusterProvider()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = architectureController.checkHealth();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.FALSE, response.getBody().get("healthy"));
        assertEquals("No cluster provider available", response.getBody().get("reason"));
    }

    @Test
    public void testCheckHealthHealthy() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.isClusterHealthy()).thenReturn(true);
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V4_NAMESRV);

        ResponseEntity<Map<String, Object>> response = architectureController.checkHealth();
        assertEquals(Boolean.TRUE, response.getBody().get("healthy"));
        assertTrue(!response.getBody().containsKey("reason"));
    }

    @Test
    public void testCheckHealthUnhealthy() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.isClusterHealthy()).thenReturn(false);
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V4_NAMESRV);

        ResponseEntity<Map<String, Object>> response = architectureController.checkHealth();
        assertEquals(Boolean.FALSE, response.getBody().get("healthy"));
        assertEquals("Cluster health check failed", response.getBody().get("reason"));
    }

    @Test
    public void testCheckHealthException() throws Exception {
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(clusterProvider.isClusterHealthy()).thenThrow(new RuntimeException("conn refused"));

        ResponseEntity<Map<String, Object>> response = architectureController.checkHealth();
        assertEquals(Boolean.FALSE, response.getBody().get("healthy"));
        assertEquals("conn refused", response.getBody().get("reason"));
    }

    // ==================== switchArchitecture ====================

    @Test
    public void testSwitchArchitectureInvalidType() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("NOT_A_TYPE");

        ResponseEntity<Map<String, Object>> response = architectureController.switchArchitecture(request);
        assertEquals(400, response.getStatusCode().value());
        assertEquals(Boolean.FALSE, response.getBody().get("success"));
    }

    @Test
    public void testSwitchArchitectureV5WithoutProxyAddresses() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("V5_PROXY_LOCAL");

        ResponseEntity<Map<String, Object>> response = architectureController.switchArchitecture(request);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Proxy addresses are required for V5 architecture", response.getBody().get("error"));
    }

    @Test
    public void testSwitchArchitectureV5Success() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("V5_PROXY_CLUSTER");
        request.setProxyAddresses(new String[] {"127.0.0.1:8081"});
        request.setNameSrvAddress("127.0.0.1:9876");
        request.setNamespace("ns1");
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V5_PROXY_CLUSTER);
        when(adaptationManager.getCurrentCapability()).thenReturn(mock(ClusterCapability.class));

        ResponseEntity<Map<String, Object>> response = architectureController.switchArchitecture(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        verify(adaptationManager).switchToV5Proxy(
            ClusterAccessType.V5_PROXY_CLUSTER,
            request.getProxyAddresses(),
            "127.0.0.1:9876",
            Optional.of("ns1"));
    }

    @Test
    public void testSwitchArchitectureV4Success() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("V4_NAMESRV");
        when(adaptationManager.getCurrentAccessType()).thenReturn(ClusterAccessType.V4_NAMESRV);
        when(adaptationManager.getCurrentCapability()).thenReturn(mock(ClusterCapability.class));

        ResponseEntity<Map<String, Object>> response = architectureController.switchArchitecture(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        verify(adaptationManager).switchToArchitecture(ClusterAccessType.V4_NAMESRV);
    }

    @Test
    public void testSwitchArchitectureRuntimeError() {
        ArchitectureSwitchRequest request = new ArchitectureSwitchRequest();
        request.setAccessType("V4_NAMESRV");
        doThrow(new RuntimeException("switch failed"))
            .when(adaptationManager).switchToArchitecture(any(ClusterAccessType.class));

        ResponseEntity<Map<String, Object>> response = architectureController.switchArchitecture(request);
        assertEquals(500, response.getStatusCode().value());
        assertEquals(Boolean.FALSE, response.getBody().get("success"));
    }

    // ==================== listArchitectureTypes ====================

    @Test
    public void testListArchitectureTypes() {
        ResponseEntity<Map<String, Object>> response = architectureController.listArchitectureTypes();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(ClusterAccessType.values().length, response.getBody().size());
        assertTrue(response.getBody().containsKey("V4_NAMESRV"));
        assertTrue(response.getBody().containsKey("V5_PROXY_LOCAL"));
    }

    // ==================== detectCapabilities ====================

    @Test
    public void testDetectCapabilitiesNoProvider() {
        when(adaptationManager.getClusterProvider()).thenReturn(null);

        ResponseEntity<ClusterCapability> response = architectureController.detectCapabilities();
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void testDetectCapabilities() {
        ClusterCapability capability = mock(ClusterCapability.class);
        when(adaptationManager.getClusterProvider()).thenReturn(clusterProvider);
        when(capabilityDetector.detectCapability(clusterProvider)).thenReturn(capability);

        ResponseEntity<ClusterCapability> response = architectureController.detectCapabilities();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(capability, response.getBody());
    }
}
