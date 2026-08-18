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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static void awaitBlockedOrTerminated(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive()
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(thread.getState() == Thread.State.BLOCKED || !thread.isAlive()).isTrue();
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
