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

import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyConsumerResolverTest {

    private static final String PROXY_ADDR = "192.0.2.10:8080";
    private static final String CONSUMER_GROUP = "cg-orders";
    private static final String PROXY_START_FAILURE = "proxy remoting client start failed";
    private static final String QUERY_BEFORE_START_FINISHED =
            "proxy query reached the client before startup finished";

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MQAdminExt adminExt;

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
    void resolveConsumerConnectionStatusShouldMarkDiscoveryFailureUnavailableTest() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic"))
                .thenThrow(new IllegalStateException("nameserver unavailable"));

        ProxyConsumerResolver.ConsumerConnectionResolution result =
                resolver.resolveConsumerConnectionStatus("instance-a", "cg-orders");

        assertThat(result.available()).isFalse();
        assertThat(result.connection()).isNull();
    }

    @Test
    void resolveConsumerConnectionStatusShouldTreatNoProxiesAsKnownOfflineTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        syncer.setConnectionSet(new HashSet<>());
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic")).thenReturn(syncer);

        ProxyConsumerResolver.ConsumerConnectionResolution result =
                resolver.resolveConsumerConnectionStatus("instance-a", "cg-orders");

        assertThat(result.available()).isTrue();
        assertThat(result.connection()).isNull();
    }

    @Test
    void resolveConsumerConnectionStatusShouldDistinguishOfflineFromProxyFailureTest() throws Exception {
        ConsumerConnection syncer = new ConsumerConnection();
        Connection proxy = new Connection();
        proxy.setClientAddr("192.0.2.1:10911");
        syncer.setConnectionSet(new HashSet<>(List.of(proxy)));
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic")).thenReturn(syncer);
        NettyRemotingClient client = mock(NettyRemotingClient.class);
        resolver.setRemotingClientForTest(client);
        when(client.invokeSync(anyString(), any(RemotingCommand.class), anyLong()))
                .thenReturn(RemotingCommand.createResponseCommand(ResponseCode.CONSUMER_NOT_ONLINE, "not online"))
                .thenReturn(null);

        ProxyConsumerResolver.ConsumerConnectionResolution offline =
                resolver.resolveConsumerConnectionStatus("instance-a", "cg-orders");
        ProxyConsumerResolver.ConsumerConnectionResolution unavailable =
                resolver.resolveConsumerConnectionStatus("instance-a", "cg-orders");

        assertThat(offline.available()).isTrue();
        assertThat(offline.connection()).isNull();
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.connection()).isNull();
    }

    @Test
    void firstStartFailureShouldNotBeCachedAndTheNextQueryShouldStartAFreshClientTest() throws Exception {
        AtomicInteger constructions = new AtomicInteger();
        try (MockedConstruction<NettyRemotingClient> construction = mockConstruction(NettyRemotingClient.class,
                (client, context) -> {
                    if (constructions.getAndIncrement() == 0) {
                        doThrow(new IllegalStateException(PROXY_START_FAILURE)).when(client).start();
                    } else {
                        when(client.invokeSync(anyString(), any(RemotingCommand.class), anyLong()))
                                .thenReturn(RemotingCommand.createResponseCommand(
                                        ResponseCode.CONSUMER_NOT_ONLINE, "not online"));
                    }
                })) {
            assertThatThrownBy(() -> resolver.queryProxy(PROXY_ADDR, CONSUMER_GROUP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(PROXY_START_FAILURE);

            // A client whose start failed must not be published, so it has to be disposed of instead
            // of being reused by the next proxy query.
            assertThat(construction.constructed()).hasSize(1);
            NettyRemotingClient failed = construction.constructed().get(0);
            verify(failed).start();
            verify(failed).shutdown();

            assertThat(resolver.queryProxy(PROXY_ADDR, CONSUMER_GROUP)).isNull();

            // The next query retries with a freshly created client and starts it.
            assertThat(construction.constructed()).hasSize(2);
            NettyRemotingClient replacement = construction.constructed().get(1);
            verify(replacement).start();
            verify(replacement).invokeSync(anyString(), any(RemotingCommand.class), anyLong());
        }
    }

    @Test
    void concurrentFirstUseShouldWaitUntilTheRemotingClientStartupFinishedTest() throws Exception {
        CountDownLatch startEntered = new CountDownLatch(1);
        CountDownLatch concurrentAttempt = new CountDownLatch(1);
        AtomicBoolean startupFinished = new AtomicBoolean(false);
        AtomicReference<Throwable> concurrentFailure = new AtomicReference<>();
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        try (MockedConstruction<NettyRemotingClient> construction = mockConstruction(NettyRemotingClient.class,
                (client, context) -> {
                    doAnswer(invocation -> {
                        events.add("start-entered");
                        startEntered.countDown();
                        // Let the concurrent caller reach the resolver while this startup is pending,
                        // then give it a bounded allowance to act.
                        concurrentAttempt.await(5, TimeUnit.SECONDS);
                        Thread.sleep(500L);
                        startupFinished.set(true);
                        events.add("start-finished");
                        return null;
                    }).when(client).start();
                    when(client.invokeSync(anyString(), any(RemotingCommand.class), anyLong()))
                            .thenAnswer(invocation -> {
                                if (!startupFinished.get()) {
                                    throw new IllegalStateException(QUERY_BEFORE_START_FINISHED);
                                }
                                events.add("invokeSync");
                                return RemotingCommand.createResponseCommand(
                                        ResponseCode.CONSUMER_NOT_ONLINE, "not online");
                            });
                })) {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<?> concurrent = pool.submit(() -> {
                    try {
                        startEntered.await(5, TimeUnit.SECONDS);
                        concurrentAttempt.countDown();
                        resolver.queryProxy(PROXY_ADDR, CONSUMER_GROUP);
                    } catch (Throwable t) {
                        concurrentFailure.set(t);
                    }
                });

                assertThat(resolver.queryProxy(PROXY_ADDR, CONSUMER_GROUP)).isNull();
                concurrent.get(5, TimeUnit.SECONDS);

                assertThat(concurrentFailure.get()).isNull();
                assertThat(construction.constructed()).hasSize(1);
                assertThat(events).containsExactly("start-entered", "start-finished", "invokeSync", "invokeSync");
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
