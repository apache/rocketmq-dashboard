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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MqClientPoolTest {

    @Test
    void withPullConsumerShouldReusePooledClient() throws Exception {
        RecordingPool pool = new RecordingPool();

        DefaultMQPullConsumer first =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer);
        DefaultMQPullConsumer second =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer);

        assertThat(second).isSameAs(first);
        assertThat(pool.created).hasSize(1);
    }

    @Test
    void withPullConsumerShouldNotShareClientsAcrossIdentities() throws Exception {
        RecordingPool pool = new RecordingPool();

        DefaultMQPullConsumer first =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer);
        DefaultMQPullConsumer second =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-b", consumer -> consumer);

        assertThat(second).isNotSameAs(first);
        assertThat(pool.created).hasSize(2);
    }

    @Test
    void releaseShouldShutdownAndEvictClient() throws Exception {
        RecordingPool pool = new RecordingPool();

        DefaultMQPullConsumer first =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer);
        pool.release("10.0.0.1:9876", "credential-a");
        DefaultMQPullConsumer second =
                pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer);

        verify(first).shutdown();
        assertThat(second).isNotSameAs(first);
        assertThat(pool.created).hasSize(2);
    }

    @Test
    void shutdownShouldCloseConcurrentlyCreatedClient() throws Exception {
        BlockingPool pool = new BlockingPool();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> caller = executor.submit(
                    () -> pool.withPullConsumer("10.0.0.1:9876", null, "credential-a", consumer -> consumer));
            assertThat(pool.creationStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // The in-flight creation holds the lifecycle lock, so shutdown must wait for it and
            // close the client it produces instead of scanning an empty pool and leaking it.
            Future<?> shutdownTask = executor.submit(pool::shutdown);

            pool.allowCreation.countDown();
            caller.get(5, TimeUnit.SECONDS);
            shutdownTask.get(5, TimeUnit.SECONDS);

            assertThat(pool.created).hasSize(1);
            verify(pool.created.get(0), times(1)).shutdown();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void withPullConsumerShouldRejectCallsAfterShutdown() {
        RecordingPool pool = new RecordingPool();
        pool.shutdown();

        assertThatThrownBy(
                () -> pool.withPullConsumer("10.0.0.1:9876", null, null, consumer -> consumer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shutting down");
        assertThat(pool.created).isEmpty();
    }

    private static class RecordingPool extends MqClientPool {
        final List<DefaultMQPullConsumer> created = new CopyOnWriteArrayList<>();

        @Override
        protected DefaultMQPullConsumer createPullConsumer(String namesrvAddr, RPCHook rpcHook) {
            DefaultMQPullConsumer consumer = mock(DefaultMQPullConsumer.class);
            created.add(consumer);
            return consumer;
        }
    }

    private static final class BlockingPool extends RecordingPool {
        private final CountDownLatch creationStarted = new CountDownLatch(1);
        private final CountDownLatch allowCreation = new CountDownLatch(1);

        @Override
        protected DefaultMQPullConsumer createPullConsumer(String namesrvAddr, RPCHook rpcHook) {
            DefaultMQPullConsumer consumer = super.createPullConsumer(namesrvAddr, rpcHook);
            creationStarted.countDown();
            try {
                assertThat(allowCreation.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Client creation interrupted", exception);
            }
            return consumer;
        }
    }
}
