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

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.service.client.GrpcClientCollector;
import org.apache.rocketmq.dashboard.service.client.RemotingClientCollector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UnifiedClientServiceImplTest {

    @InjectMocks
    private UnifiedClientServiceImpl unifiedClientService;

    @Mock
    private RemotingClientCollector remotingClientCollector;

    @Mock
    private GrpcClientCollector grpcClientCollector;

    private ClientInstance buildClient(String clientId) {
        ClientInstance client = new ClientInstance();
        client.setClientId(clientId);
        return client;
    }

    @Test
    public void testListAllClientsRemotingOnly() {
        ClientInstance remoting = buildClient("c1");
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(remoting));
        when(grpcClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.emptyList());

        List<ClientInstance> result = unifiedClientService.listAllClients(Optional.empty(), Optional.empty());
        assertEquals(1, result.size());
        assertSame(remoting, result.get(0));
    }

    @Test
    public void testListAllClientsMergeAndDedup() {
        ClientInstance remoting = buildClient("shared");
        remoting.setEndpoint(null);
        remoting.setSettingsVersion(null);

        ClientInstance grpcShared = buildClient("shared");
        grpcShared.setEndpoint("proxy:8081");
        grpcShared.setSettingsVersion("v2");
        grpcShared.setPopEnabled(Boolean.TRUE);
        grpcShared.setLongConnectionActive(Boolean.TRUE);
        ClientInstance.ConsumerProgress progress = new ClientInstance.ConsumerProgress();
        progress.setTotalBacklog(10L);
        grpcShared.setConsumerProgress(progress);
        Date grpcHeartbeat = new Date();
        grpcShared.setLastHeartbeatTime(grpcHeartbeat);

        ClientInstance grpcOnly = buildClient("grpc-only");

        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(remoting));
        when(grpcClientCollector.listClientInstances(any(), any()))
            .thenReturn(Arrays.asList(grpcShared, grpcOnly));

        List<ClientInstance> result = unifiedClientService.listAllClients(Optional.empty(), Optional.empty());
        assertEquals(2, result.size());

        // Remoting entry kept and enriched with gRPC-only fields
        ClientInstance merged = result.stream()
            .filter(c -> "shared".equals(c.getClientId())).findFirst().orElseThrow();
        assertSame(remoting, merged);
        assertEquals("proxy:8081", merged.getEndpoint());
        assertEquals("v2", merged.getSettingsVersion());
        assertEquals(Boolean.TRUE, merged.getPopEnabled());
        assertEquals(Boolean.TRUE, merged.getLongConnectionActive());
        assertSame(progress, merged.getConsumerProgress());
        assertEquals(grpcHeartbeat, merged.getLastHeartbeatTime());

        assertTrue(result.stream().anyMatch(c -> "grpc-only".equals(c.getClientId())));
    }

    @Test
    public void testListAllClientsEnrichDoesNotOverrideExisting() {
        ClientInstance remoting = buildClient("shared");
        remoting.setEndpoint("remoting-endpoint");
        remoting.setSettingsVersion("v1");
        remoting.setPopEnabled(Boolean.FALSE);
        remoting.setLongConnectionActive(Boolean.FALSE);
        ClientInstance.ConsumerProgress remotingProgress = new ClientInstance.ConsumerProgress();
        remoting.setConsumerProgress(remotingProgress);

        ClientInstance grpcShared = buildClient("shared");
        grpcShared.setEndpoint("grpc-endpoint");
        grpcShared.setSettingsVersion("v2");
        grpcShared.setPopEnabled(Boolean.TRUE);
        grpcShared.setLongConnectionActive(Boolean.TRUE);
        grpcShared.setConsumerProgress(new ClientInstance.ConsumerProgress());

        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(remoting));
        when(grpcClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(grpcShared));

        List<ClientInstance> result = unifiedClientService.listAllClients(Optional.empty(), Optional.empty());
        assertEquals(1, result.size());
        ClientInstance merged = result.get(0);
        assertEquals("remoting-endpoint", merged.getEndpoint());
        assertEquals("v1", merged.getSettingsVersion());
        assertEquals(Boolean.FALSE, merged.getPopEnabled());
        assertEquals(Boolean.FALSE, merged.getLongConnectionActive());
        assertSame(remotingProgress, merged.getConsumerProgress());
    }

    @Test
    public void testListAllClientsCollectorFailureReturnsEmpty() {
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenThrow(new RuntimeException("cluster unavailable"));

        List<ClientInstance> result = unifiedClientService.listAllClients(Optional.empty(), Optional.empty());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testDescribeClientEmptyId() {
        assertFalse(unifiedClientService.describeClient(null).isPresent());
        assertFalse(unifiedClientService.describeClient("  ").isPresent());
    }

    @Test
    public void testDescribeClientFoundInRemoting() {
        ClientInstance client = buildClient("c1");
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(client));

        Optional<ClientInstance> result = unifiedClientService.describeClient("c1");
        assertTrue(result.isPresent());
        assertSame(client, result.get());
    }

    @Test
    public void testDescribeClientFallbackToGrpc() {
        ClientInstance grpcClient = buildClient("c2");
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.emptyList());
        when(grpcClientCollector.getClientInstance("c2")).thenReturn(Optional.of(grpcClient));

        Optional<ClientInstance> result = unifiedClientService.describeClient("c2");
        assertTrue(result.isPresent());
        assertSame(grpcClient, result.get());
    }

    @Test
    public void testDescribeClientNotFoundAnywhere() {
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(buildClient("other")));
        when(grpcClientCollector.getClientInstance("missing")).thenReturn(Optional.empty());

        assertFalse(unifiedClientService.describeClient("missing").isPresent());
    }

    @Test
    public void testGetClientSubscriptionsFromRemoting() {
        ClientInstance client = buildClient("c1");
        SubscriptionInfo subscription = new SubscriptionInfo();
        client.setSubscriptions(Collections.singletonList(subscription));
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(client));

        List<SubscriptionInfo> result = unifiedClientService.getClientSubscriptions("c1");
        assertEquals(1, result.size());
        assertSame(subscription, result.get(0));
    }

    @Test
    public void testGetClientSubscriptionsFallbackToGrpc() {
        ClientInstance client = buildClient("c1");
        client.setSubscriptions(Collections.emptyList());
        SubscriptionInfo grpcSubscription = new SubscriptionInfo();
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.singletonList(client));
        when(grpcClientCollector.getClientSubscriptions("c1"))
            .thenReturn(Collections.singletonList(grpcSubscription));

        List<SubscriptionInfo> result = unifiedClientService.getClientSubscriptions("c1");
        assertEquals(1, result.size());
        assertSame(grpcSubscription, result.get(0));
    }

    @Test
    public void testGetClientSubscriptionsNoneFound() {
        when(remotingClientCollector.listClientInstances(any(), any()))
            .thenReturn(Collections.emptyList());
        when(grpcClientCollector.getClientSubscriptions("c1")).thenReturn(Collections.emptyList());

        assertTrue(unifiedClientService.getClientSubscriptions("c1").isEmpty());
    }

    @Test
    public void testGetAvailableChannelsRemotingOnly() {
        when(grpcClientCollector.hasDataAvailable()).thenReturn(false);

        List<String> channels = unifiedClientService.getAvailableChannels();
        assertEquals(Collections.singletonList("REMOTING"), channels);
    }

    @Test
    public void testGetAvailableChannelsWithGrpc() {
        when(grpcClientCollector.hasDataAvailable()).thenReturn(true);

        List<String> channels = unifiedClientService.getAvailableChannels();
        assertEquals(Arrays.asList("REMOTING", "GRPC"), channels);
    }
}
