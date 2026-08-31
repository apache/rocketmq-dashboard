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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TencentClientFactoryTest {

    @Test
    void endpointForShouldUseNearbyAccessForNonFinancialRegionsTest() {
        assertThat(TencentClientFactory.endpointFor("ap-seoul"))
                .isEqualTo(TencentClientFactory.ENDPOINT);
        assertThat(TencentClientFactory.endpointFor("ap-shanghai-adc"))
                .isEqualTo(TencentClientFactory.ENDPOINT);
    }

    @Test
    void endpointForShouldUseNearbyAccessWhenRegionIsMissingTest() {
        assertThat(TencentClientFactory.endpointFor(null))
                .isEqualTo(TencentClientFactory.ENDPOINT);
        assertThat(TencentClientFactory.endpointFor(""))
                .isEqualTo(TencentClientFactory.ENDPOINT);
    }

    @Test
    void endpointForShouldUseRegionalEndpointForFinancialRegionsTest() {
        assertThat(TencentClientFactory.endpointFor("ap-shenzhen-fsi"))
                .isEqualTo("trocket.ap-shenzhen-fsi.tencentcloudapi.com");
        assertThat(TencentClientFactory.endpointFor("ap-shanghai-fsi"))
                .isEqualTo("trocket.ap-shanghai-fsi.tencentcloudapi.com");
    }

    @Test
    void invalidateCredentialShouldRemoveAnInFlightClientCreationTest() throws Exception {
        long credentialId = 1L;
        BlockingClientFactory factory = new BlockingClientFactory();
        FutureTask<TrocketClient> firstClientTask = new FutureTask<>(
                () -> factory.client(credentialId, "ap-shanghai"));
        Thread creationThread = new Thread(firstClientTask, "tencent-client-creation-test");
        creationThread.start();
        assertThat(factory.creationStarted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread invalidationThread = new Thread(
                () -> factory.invalidateCredential(credentialId),
                "tencent-client-invalidation-test");
        invalidationThread.start();
        awaitBlockedOrTerminated(invalidationThread);

        factory.allowCreation.countDown();
        TrocketClient firstClient = firstClientTask.get(5, TimeUnit.SECONDS);
        invalidationThread.join(TimeUnit.SECONDS.toMillis(5));

        assertThat(invalidationThread.isAlive()).isFalse();
        assertThat(factory.client(credentialId, "ap-shanghai")).isNotSameAs(firstClient);
        assertThat(factory.creationCount).hasValue(2);
    }

    @Test
    void clientShouldNotReturnClientEvictedByConcurrentInvalidationTest() throws Exception {
        long credentialId = 1L;
        String region = "ap-shanghai";
        StaleReadFactory factory = new StaleReadFactory();
        TrocketClient cached = factory.client(credentialId, region);

        Thread lookupThread = new Thread(() ->
                factory.observed.set(factory.client(credentialId, region)),
                "tencent-client-stale-read-test");
        lookupThread.start();
        assertThat(factory.readCompleted.await(5, TimeUnit.SECONDS)).isTrue();

        Thread invalidationThread = new Thread(() -> {
            factory.invalidateCredential(credentialId);
            factory.invalidationCompleted.set(Instant.now());
        }, "tencent-client-invalidation-test");
        invalidationThread.start();
        awaitBlockedOrTerminated(invalidationThread);

        factory.allowReturn.countDown();
        lookupThread.join(TimeUnit.SECONDS.toMillis(5));
        invalidationThread.join(TimeUnit.SECONDS.toMillis(5));

        // The lookup may return the cached client, but only if the invalidation that evicted it
        // had not finished before the lookup returned.
        assertThat(factory.observed.get()).isSameAs(cached);
        assertThat(factory.lookupCompleted.get()).isBefore(factory.invalidationCompleted.get());
    }

    private static void awaitBlockedOrTerminated(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive()
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState() == Thread.State.BLOCKED || !thread.isAlive()).isTrue();
    }

    private static final class StaleReadFactory extends TencentClientFactory {
        final CountDownLatch readCompleted = new CountDownLatch(1);
        final CountDownLatch allowReturn = new CountDownLatch(1);
        final AtomicReference<TrocketClient> observed = new AtomicReference<>();
        final AtomicReference<Instant> lookupCompleted = new AtomicReference<>();
        final AtomicReference<Instant> invalidationCompleted = new AtomicReference<>();

        private StaleReadFactory() {
            super(mock(CloudCredentialRepository.class));
        }

        @Override
        protected TrocketClient createClient(Long credentialId, String region) {
            return mock(TrocketClient.class);
        }

        @Override
        TrocketClient cachedClient(String key) {
            TrocketClient cached = super.cachedClient(key);
            if (cached == null) {
                return cached;
            }
            // Simulate a thread suspended between the cache read and the return, while a
            // concurrent credential rotation runs. The completion timestamp is stamped while
            // still inside client(), so the ordering against the invalidation is exact.
            readCompleted.countDown();
            try {
                assertThat(allowReturn.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Stale read interrupted", exception);
            }
            lookupCompleted.set(Instant.now());
            return cached;
        }
    }

    private static final class BlockingClientFactory extends TencentClientFactory {
        private final CountDownLatch creationStarted = new CountDownLatch(1);
        private final CountDownLatch allowCreation = new CountDownLatch(1);
        private final AtomicInteger creationCount = new AtomicInteger();

        private BlockingClientFactory() {
            super(mock(CloudCredentialRepository.class));
        }

        @Override
        protected TrocketClient createClient(Long credentialId, String region) {
            TrocketClient client = mock(TrocketClient.class);
            if (creationCount.incrementAndGet() == 1) {
                creationStarted.countDown();
                try {
                    assertThat(allowCreation.await(5, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Client creation interrupted", exception);
                }
            }
            return client;
        }
    }
}
