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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ClientServiceImplTest {

    @InjectMocks
    private ClientServiceImpl clientService;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ClusterProvider clusterProvider;

    @Before
    public void setUp() throws Exception {
        // The subclass shadows metadataProvider/clusterProvider; inject both levels
        setField(ClientServiceImpl.class, "metadataProvider", metadataProvider);
        setField(ClientServiceImpl.class, "clusterProvider", clusterProvider);
        setField(ArchitectureBasedService.class, "metadataProvider", metadataProvider);
        setField(ArchitectureBasedService.class, "clusterProvider", clusterProvider);
    }

    private void setField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(clientService, value);
    }

    private void givenCapabilities(String... capabilities) throws Exception {
        ClusterCapability capability = new ClusterCapability();
        capability.setExtendedCapabilities(new HashSet<>(Arrays.asList(capabilities)));
        capability.setArchitectureVersion("5.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
    }

    private ClientInstance buildClient(String clientId) {
        ClientInstance client = new ClientInstance();
        client.setClientId(clientId);
        client.setProtocolType(ClientInstance.ProtocolType.REMOTING);
        return client;
    }

    @Test
    public void testListClientsSupported() throws Exception {
        givenCapabilities("CLIENT_DISCOVERY");
        ClientInstance client = buildClient("c1");
        when(metadataProvider.listClients()).thenReturn(Collections.singletonList(client));

        List<ClientInstance> result = clientService.listClients();
        assertEquals(1, result.size());
        assertSame(client, result.get(0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsUnsupported() throws Exception {
        givenCapabilities();
        clientService.listClients();
    }

    @Test
    public void testListClientsByProtocolSupported() throws Exception {
        givenCapabilities("CLIENT_PROTOCOL_FILTER");
        ClientInstance client = buildClient("c1");
        when(metadataProvider.listClientsByProtocol("GRPC")).thenReturn(Collections.singletonList(client));

        List<ClientInstance> result = clientService.listClientsByProtocol("GRPC");
        assertEquals(1, result.size());
    }

    @Test
    public void testListClientsByProtocolFallback() throws Exception {
        givenCapabilities();
        when(metadataProvider.listClients()).thenReturn(Collections.singletonList(buildClient("c1")));

        // Fallback compares a String with the ProtocolType enum, so nothing matches
        List<ClientInstance> result = clientService.listClientsByProtocol("REMOTING");
        assertTrue(result.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByProtocolFailure() throws Exception {
        givenCapabilities("CLIENT_PROTOCOL_FILTER");
        when(metadataProvider.listClientsByProtocol(anyString())).thenThrow(new RuntimeException("boom"));
        clientService.listClientsByProtocol("GRPC");
    }

    @Test
    public void testListClientsByTypeSuccess() throws Exception {
        when(metadataProvider.listClientsByType("CONSUMER"))
            .thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.listClientsByType("CONSUMER");
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByTypeFailure() throws Exception {
        when(metadataProvider.listClientsByType(anyString()))
            .thenThrow(new UnsupportedOperationException("not supported"));
        clientService.listClientsByType("CONSUMER");
    }

    @Test
    public void testListClientsByClusterSupported() throws Exception {
        givenCapabilities("CLIENT_CLUSTER_FILTER");
        when(metadataProvider.listClientsByCluster("DefaultCluster"))
            .thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.listClientsByCluster("DefaultCluster");
        assertEquals(1, result.size());
    }

    @Test
    public void testListClientsByClusterFallback() throws Exception {
        givenCapabilities();
        when(metadataProvider.listClients()).thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.listClientsByCluster("DefaultCluster");
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByClusterFailure() throws Exception {
        givenCapabilities();
        when(metadataProvider.listClients()).thenThrow(new RuntimeException("boom"));
        clientService.listClientsByCluster("DefaultCluster");
    }

    @Test
    public void testGetClientFound() throws Exception {
        ClientInstance client = buildClient("c1");
        when(metadataProvider.getClient("c1")).thenReturn(Optional.of(client));
        assertSame(client, clientService.getClient("c1"));
    }

    @Test
    public void testGetClientNotFound() throws Exception {
        when(metadataProvider.getClient("c2")).thenReturn(Optional.empty());
        assertNull(clientService.getClient("c2"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientEmptyId() {
        clientService.getClient("  ");
    }

    @Test
    public void testKillClientSupported() throws Exception {
        givenCapabilities("CLIENT_KILL");
        assertTrue(clientService.killClient("c1", "test"));
        verify(metadataProvider).killClient("c1", "test");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testKillClientUnsupported() throws Exception {
        givenCapabilities();
        clientService.killClient("c1", "test");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testKillClientEmptyId() {
        clientService.killClient("", "test");
    }

    @Test
    public void testUpdateClientConfigSupported() throws Exception {
        givenCapabilities("CLIENT_CONFIG_UPDATE");
        assertTrue(clientService.updateClientConfig("c1", "key", "value"));
        verify(metadataProvider).updateClientConfig("c1", "key", "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateClientConfigUnsupported() throws Exception {
        givenCapabilities();
        clientService.updateClientConfig("c1", "key", "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateClientConfigEmptyKey() {
        clientService.updateClientConfig("c1", " ", "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateClientConfigEmptyClientId() {
        clientService.updateClientConfig(null, "key", "value");
    }

    @Test
    public void testGetConnectedClientsSupported() throws Exception {
        givenCapabilities("CLIENT_BROKER_CONNECTION");
        when(metadataProvider.getConnectedClients("127.0.0.1:10911"))
            .thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.getConnectedClients("127.0.0.1:10911");
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConnectedClientsUnsupported() throws Exception {
        givenCapabilities();
        clientService.getConnectedClients("127.0.0.1:10911");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConnectedClientsEmptyAddress() {
        clientService.getConnectedClients("");
    }

    @Test
    public void testGetIdleClientsSupported() throws Exception {
        givenCapabilities("CLIENT_IDLE_DETECTION");
        when(metadataProvider.getIdleClients(1000L))
            .thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.getIdleClients(1000L);
        assertEquals(1, result.size());
    }

    @Test
    public void testGetIdleClientsFallbackFiltersByHeartbeat() throws Exception {
        givenCapabilities();
        ClientInstance idle = buildClient("idle");
        idle.setLastHeartbeatTime(new Date(System.currentTimeMillis() - 60_000L));
        ClientInstance fresh = buildClient("fresh");
        fresh.setLastHeartbeatTime(new Date());
        ClientInstance noHeartbeat = buildClient("none");
        when(metadataProvider.listClients()).thenReturn(Arrays.asList(idle, fresh, noHeartbeat));

        List<ClientInstance> result = clientService.getIdleClients(30_000L);
        assertEquals(1, result.size());
        assertEquals("idle", result.get(0).getClientId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetIdleClientsFailure() throws Exception {
        givenCapabilities();
        when(metadataProvider.listClients()).thenThrow(new RuntimeException("boom"));
        clientService.getIdleClients(1000L);
    }

    @Test
    public void testGetClientsWithIssueSupported() throws Exception {
        givenCapabilities("CLIENT_ISSUE_DETECTION");
        when(metadataProvider.getClientsWithIssue("SLOW"))
            .thenReturn(Collections.singletonList(buildClient("c1")));

        List<ClientInstance> result = clientService.getClientsWithIssue("SLOW");
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientsWithIssueUnsupported() throws Exception {
        givenCapabilities();
        clientService.getClientsWithIssue("SLOW");
    }

    @Test
    public void testDiagnoseClientSupported() throws Exception {
        givenCapabilities("CLIENT_DIAGNOSIS");
        assertTrue(clientService.diagnoseClient("c1"));
        verify(metadataProvider).diagnoseClient("c1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDiagnoseClientUnsupported() throws Exception {
        givenCapabilities();
        clientService.diagnoseClient("c1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDiagnoseClientEmptyId() {
        clientService.diagnoseClient(null);
    }
}
