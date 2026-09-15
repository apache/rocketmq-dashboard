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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.OpsConnectionSettings;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class MqAdminExtFactoryTest {

    private static final class RecordingFactory extends MqAdminExtFactory {
        private final DefaultMQAdminExt admin;
        private final AtomicInteger created = new AtomicInteger();

        private RecordingFactory(DefaultMQAdminExt admin) {
            this.admin = admin;
        }

        @Override
        protected DefaultMQAdminExt newAdmin(RPCHook rpcHook) {
            created.incrementAndGet();
            return admin;
        }
    }

    @Test
    void executeShouldRejectBlankNamesrvAddr() {
        MqAdminExtFactory factory = new MqAdminExtFactory();

        assertThatThrownBy(() -> factory.execute("  ", null, admin -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NameServer address is required");
    }

    @Test
    void executeShouldStartClientOnceAndCacheIt() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        String first = factory.execute("10.0.0.1:9876", null, a -> "first");
        String second = factory.execute("10.0.0.1:9876", null, a -> "second");

        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
        assertThat(factory.created.get()).isEqualTo(1);
        verify(admin, times(1)).start();
    }

    @Test
    void defaultConnectionAppliesTransportBeforeStartWithoutSharingInstanceClient() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);
        OpsConnectionSettings settings = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, true);

        factory.executeDefault(settings, null, "anonymous", ignored -> "default");
        factory.execute("namesrv:9876", null, "anonymous", ignored -> "instance");

        assertThat(factory.created.get()).isEqualTo(2);
        InOrder calls = inOrder(admin);
        calls.verify(admin).setNamesrvAddr("namesrv:9876");
        calls.verify(admin).setVipChannelEnabled(true);
        calls.verify(admin).setUseTLS(true);
        verify(admin, times(2)).start();
    }

    @Test
    void changingDefaultTransportCreatesANewAdminClient() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);
        OpsConnectionSettings standard = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings vip = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, false);

        factory.executeDefault(standard, null, "anonymous", ignored -> null);
        factory.executeDefault(vip, null, "anonymous", ignored -> null);
        factory.executeDefault(vip, null, "anonymous", ignored -> null);

        assertThat(factory.created.get()).isEqualTo(2);
        verify(admin, times(2)).start();
    }

    @Test
    void managedChangeReleasesOnlyOldDefaultAdminAndKeepsInstanceClient() throws Exception {
        DefaultMQAdminExt oldDefault = mock(DefaultMQAdminExt.class);
        DefaultMQAdminExt instance = mock(DefaultMQAdminExt.class);
        DefaultMQAdminExt currentDefault = mock(DefaultMQAdminExt.class);
        AtomicInteger created = new AtomicInteger();
        List<DefaultMQAdminExt> clients = List.of(oldDefault, instance, currentDefault);
        MqAdminExtFactory factory = new MqAdminExtFactory() {
            @Override
            protected DefaultMQAdminExt newAdmin(RPCHook hook) {
                return clients.get(created.getAndIncrement());
            }
        };
        OpsConnectionSettings old = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings current = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, false);
        factory.executeDefault(old, null, "anonymous", ignored -> null);
        factory.execute("namesrv:9876", null, "anonymous", ignored -> null);
        factory.executeDefault(current, null, "anonymous", ignored -> null);

        factory.releaseInactiveManagedDefaults(current);
        factory.execute("namesrv:9876", null, "anonymous", ignored -> null);
        factory.executeDefault(current, null, "anonymous", ignored -> null);

        assertThat(created.get()).isEqualTo(3);
        verify(oldDefault).shutdown();
        verify(instance, never()).shutdown();
        verify(currentDefault, never()).shutdown();
    }

    @Test
    void managedReleaseWaitsForAnInFlightAdminActionBeforeShutdown() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);
        OpsConnectionSettings old = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings current = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        Thread inFlight = Thread.startVirtualThread(() -> result.set(factory.executeDefault(old, null,
                "anonymous", ignored -> {
                    started.countDown();
                    if (!proceed.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test admin action was not released");
                    }
                    return "completed";
                })));
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            factory.releaseInactiveManagedDefaults(current);
            verify(admin, never()).shutdown();
        } finally {
            proceed.countDown();
            inFlight.join();
        }

        assertThat(result.get()).isEqualTo("completed");
        verify(admin).shutdown();
    }

    @Test
    void releaseShouldShutdownAndEvictCachedClient() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.1:9876", null, ignored -> "first");
        factory.release(" 10.0.0.1:9876 ");
        factory.execute("10.0.0.1:9876", null, ignored -> "second");

        assertThat(factory.created.get()).isEqualTo(2);
        verify(admin, times(2)).start();
        verify(admin).shutdown();
    }

    @Test
    void releaseShouldEvictOnlyMatchingCredentialIdentityTest() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.1:9876", null, "credential-a", ignored -> null);
        factory.execute("10.0.0.1:9876", null, "credential-b", ignored -> null);
        factory.release("10.0.0.1:9876", "credential-a");
        factory.execute("10.0.0.1:9876", null, "credential-b", ignored -> null);
        factory.execute("10.0.0.1:9876", null, "credential-a", ignored -> null);

        assertThat(factory.created.get()).isEqualTo(3);
        verify(admin, times(3)).start();
        verify(admin).shutdown();
    }

    @Test
    void releaseShouldEvictClientUsingEquivalentNameServerAddressList() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.2:9876,10.0.0.1:9876", null, ignored -> "first");
        factory.release("10.0.0.1:9876;10.0.0.2:9876");
        factory.execute("10.0.0.1:9876;10.0.0.2:9876", null, ignored -> "second");

        assertThat(factory.created.get()).isEqualTo(2);
        verify(admin).shutdown();
    }

    @Test
    void executeShouldReuseClientForEquivalentNameServerAddressLists() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.2:9876, 10.0.0.1:9876;10.0.0.2:9876", null, ignored -> null);
        factory.execute("10.0.0.1:9876;10.0.0.2:9876", null, ignored -> null);

        assertThat(factory.created.get()).isEqualTo(1);
        verify(admin).setNamesrvAddr("10.0.0.1:9876;10.0.0.2:9876");
    }

    @Test
    void executeShouldNotShareClientsAcrossCredentialReferences() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.1:9876", mock(RPCHook.class), "credential-a", ignored -> null);
        factory.execute("10.0.0.1:9876", mock(RPCHook.class), "credential-b", ignored -> null);

        assertThat(factory.created.get()).isEqualTo(2);
        verify(admin, times(2)).start();
    }

    @Test
    void executeShouldNotShareClientsAcrossLegacyHookInstances() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.1:9876", mock(RPCHook.class), ignored -> null);
        factory.execute("10.0.0.1:9876", mock(RPCHook.class), ignored -> null);

        assertThat(factory.created.get()).isEqualTo(2);
    }

    @Test
    void releaseShouldEvictEveryCredentialIdentityForAnEndpoint() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);

        factory.execute("10.0.0.1:9876", mock(RPCHook.class), "credential-a", ignored -> null);
        factory.execute("10.0.0.1:9876", mock(RPCHook.class), "credential-b", ignored -> null);
        factory.release("10.0.0.1:9876");
        factory.execute("10.0.0.1:9876", mock(RPCHook.class), "credential-a", ignored -> null);

        assertThat(factory.created.get()).isEqualTo(3);
        verify(admin, times(2)).shutdown();
    }

    @Test
    void executeShouldRejectAddressListsWithoutUsableEntries() {
        MqAdminExtFactory factory = new MqAdminExtFactory();

        assertThatThrownBy(() -> factory.execute(" ; , ", null, ignored -> null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NameServer address is required");
    }

    @Test
    void executeShouldWrapConnectionFailure() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        doThrow(new RuntimeException("connection refused")).when(admin).start();
        RecordingFactory factory = new RecordingFactory(admin);

        assertThatThrownBy(() -> factory.execute("10.0.0.9:9876", null, a -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("connection refused");
        verify(admin).shutdown();
    }

    @Test
    void executeShouldRejectCallsAfterShutdown() {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);
        factory.shutdown();

        assertThatThrownBy(() -> factory.execute("10.0.0.1:9876", null, a -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shutting down");
    }

    @Test
    void executeShouldNotCreateConnectionAfterShutdown() {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        RecordingFactory factory = new RecordingFactory(admin);
        factory.shutdown();

        assertThatThrownBy(() -> factory.execute("10.0.0.1:9876", null, a -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shutting down");
        // No fresh admin connection may be established while the factory is shut down.
        assertThat(factory.created.get()).isZero();
    }
}
