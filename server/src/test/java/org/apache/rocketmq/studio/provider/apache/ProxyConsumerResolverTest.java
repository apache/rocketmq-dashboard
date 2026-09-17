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

package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.header.GetMaxOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.QueryConsumerOffsetRequestHeader;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyConsumerResolverTest {

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MQAdminExt adminExt;

    @Mock
    private NettyRemotingClient remotingClient;

    private ProxyConsumerResolver resolver;

    @BeforeEach
    void setUp() {
        lenient().when(runtimeAdminClientResolver.execute(any(String.class), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        lenient().when(adminFactory.execute(any(), any(), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        resolver = new ProxyConsumerResolver(adminFactory, runtimeAdminClientResolver, new RocketMQProperties());
    }

    @Test
    void queryProxyLagShouldSendQueueScopedOffsetRequestsTest() throws Exception {
        resolver.setRemotingClientForTest(remotingClient);
        when(remotingClient.invokeSync(eq("10.0.4.66:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenReturn(offsetResponse(120), offsetResponse(70));
        MessageQueue queue = new MessageQueue("orders", "broker-a", 3);

        assertThat(resolver.queryProxyLag("10.0.4.66:8080", "cg-orders", queue)).isEqualTo(50);

        ArgumentCaptor<RemotingCommand> requests = ArgumentCaptor.forClass(RemotingCommand.class);
        verify(remotingClient, times(2))
                .invokeSync(eq("10.0.4.66:8080"), requests.capture(), eq(2_000L));
        assertThat(requests.getAllValues()).extracting(RemotingCommand::getCode)
                .containsExactly(RequestCode.GET_MAX_OFFSET, RequestCode.QUERY_CONSUMER_OFFSET);

        GetMaxOffsetRequestHeader maxOffset =
                (GetMaxOffsetRequestHeader) requests.getAllValues().get(0).readCustomHeader();
        assertThat(maxOffset.getTopic()).isEqualTo("orders");
        assertThat(maxOffset.getQueueId()).isEqualTo(3);
        assertThat(maxOffset.getBrokerName()).isEqualTo("broker-a");
        assertThat(maxOffset.isCommitted()).isTrue();

        QueryConsumerOffsetRequestHeader consumerOffset =
                (QueryConsumerOffsetRequestHeader) requests.getAllValues().get(1).readCustomHeader();
        assertThat(consumerOffset.getConsumerGroup()).isEqualTo("cg-orders");
        assertThat(consumerOffset.getTopic()).isEqualTo("orders");
        assertThat(consumerOffset.getQueueId()).isEqualTo(3);
        assertThat(consumerOffset.getBrokerName()).isEqualTo("broker-a");
        assertThat(consumerOffset.getSetZeroIfNotFound()).isFalse();
    }

    @Test
    void queryLagShouldTryNextProxyAndPreserveKnownZeroTest() throws Exception {
        resolver = resolverWithProxyAddresses("proxy-a:8080", "proxy-b:8080");
        resolver.setRemotingClientForTest(remotingClient);
        when(remotingClient.invokeSync(eq("proxy-a:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenThrow(new IllegalStateException("proxy unavailable"));
        when(remotingClient.invokeSync(eq("proxy-b:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenReturn(offsetResponse(70), offsetResponse(70));

        long lag = resolver.queryLag(
                "instance-a", "cg-orders", new MessageQueue("orders", "broker-a", 3));

        assertThat(lag).isZero();
        verify(remotingClient).invokeSync(eq("proxy-a:8080"), any(RemotingCommand.class), eq(2_000L));
        verify(remotingClient, times(2))
                .invokeSync(eq("proxy-b:8080"), any(RemotingCommand.class), eq(2_000L));
    }

    @Test
    void queryLagShouldReturnUnknownForPartialResponseTest() throws Exception {
        resolver = resolverWithProxyAddresses("proxy-a:8080");
        resolver.setRemotingClientForTest(remotingClient);
        RemotingCommand failure = RemotingCommand.createResponseCommand(ResponseCode.SYSTEM_ERROR, "failed");
        when(remotingClient.invokeSync(eq("proxy-a:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenReturn(offsetResponse(70), failure);

        long lag = resolver.queryLag(
                "instance-a", "cg-orders", new MessageQueue("orders", "broker-a", 3));

        assertThat(lag).isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void queryLagShouldReturnUnknownForMissingResponseHeaderTest() throws Exception {
        resolver = resolverWithProxyAddresses("proxy-a:8080");
        resolver.setRemotingClientForTest(remotingClient);
        RemotingCommand missingHeader = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
        when(remotingClient.invokeSync(eq("proxy-a:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenReturn(missingHeader);

        long lag = resolver.queryLag(
                "instance-a", "cg-orders", new MessageQueue("orders", "broker-a", 3));

        assertThat(lag).isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void queryLagShouldReturnUnknownForNegativeProxyDifferenceTest() throws Exception {
        resolver = resolverWithProxyAddresses("proxy-a:8080");
        resolver.setRemotingClientForTest(remotingClient);
        when(remotingClient.invokeSync(eq("proxy-a:8080"), any(RemotingCommand.class), eq(2_000L)))
                .thenReturn(offsetResponse(70), offsetResponse(71));

        long lag = resolver.queryLag(
                "instance-a", "cg-orders", new MessageQueue("orders", "broker-a", 3));

        assertThat(lag).isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void discoverProxyAddressesShouldDeriveRemotingAddressesFromHeartbeatSyncerTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        HashSet<Connection> connections = new HashSet<>();
        Connection proxyA = new Connection();
        proxyA.setClientId("proxy-a");
        proxyA.setClientAddr("10.0.4.66:10911");
        Connection proxyB = new Connection();
        proxyB.setClientId("proxy-b");
        proxyB.setClientAddr("10.0.3.110:10911");
        connections.add(proxyA);
        connections.add(proxyB);
        syncer.setConnectionSet(connections);
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic")).thenReturn(syncer);

        List<String> addresses = resolver.discoverProxyAddresses("instance-a");

        assertThat(addresses).containsExactlyInAnyOrder("10.0.4.66:8080", "10.0.3.110:8080");
    }

    @Test
    void discoverProxyAddressesShouldCacheResultsTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        syncer.setConnectionSet(new HashSet<>());
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic")).thenReturn(syncer);

        resolver.discoverProxyAddresses("instance-a");
        resolver.discoverProxyAddresses("instance-a");

        org.mockito.Mockito.verify(adminExt, org.mockito.Mockito.times(1))
                .examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic");
    }

    @Test
    void discoverProxyAddressesShouldRetryAfterATransientFailureTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        Connection proxy = new Connection();
        proxy.setClientId("proxy-a");
        proxy.setClientAddr("10.0.4.66:10911");
        syncer.setConnectionSet(new HashSet<>(List.of(proxy)));
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic"))
                .thenThrow(new IllegalStateException("nameserver unavailable"))
                .thenReturn(syncer);

        assertThat(resolver.discoverProxyAddresses("instance-a")).isEmpty();
        assertThat(resolver.discoverProxyAddresses("instance-a")).containsExactly("10.0.4.66:8080");

        org.mockito.Mockito.verify(adminExt, org.mockito.Mockito.times(2))
                .examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic");
    }

    @Test
    void resolveConsumerConnectionShouldReturnNullWhenNoProxyDiscoveredTest() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic"))
                .thenThrow(new IllegalStateException("syncer group missing"));

        assertThat(resolver.resolveConsumerConnection("instance-a", "cg-orders")).isNull();
    }

    @Test
    void resolveConsumerConnectionShouldReturnNullWhenProxyQueryFailsTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        HashSet<Connection> connections = new HashSet<>();
        Connection proxyA = new Connection();
        proxyA.setClientId("proxy-a");
        proxyA.setClientAddr("192.0.2.1:10911");
        connections.add(proxyA);
        syncer.setConnectionSet(connections);
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic")).thenReturn(syncer);

        // 192.0.2.1 (TEST-NET) is unreachable, so the remoting query must degrade to null
        assertThat(resolver.resolveConsumerConnection("instance-a", "cg-orders")).isNull();
    }

    private RemotingCommand offsetResponse(long offset) {
        RemotingCommand response = RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
        response.addExtField("offset", Long.toString(offset));
        return response;
    }

    private ProxyConsumerResolver resolverWithProxyAddresses(String... addresses) {
        return new ProxyConsumerResolver(adminFactory, runtimeAdminClientResolver, new RocketMQProperties()) {
            @Override
            List<String> discoverProxyAddresses(String instanceId) {
                return List.of(addresses);
            }
        };
    }
}
