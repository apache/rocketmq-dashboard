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
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.ops.OpsConnectionSettings;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class MqClientPoolTest {

    private static final class RecordingPool extends MqClientPool {
        private final DefaultMQProducer producer = mock(DefaultMQProducer.class);
        private final DefaultMQPullConsumer consumer = mock(DefaultMQPullConsumer.class);
        private final AtomicInteger producerCreations = new AtomicInteger();
        private final AtomicInteger consumerCreations = new AtomicInteger();

        @Override
        protected DefaultMQProducer newProducer(RPCHook hook) {
            producerCreations.incrementAndGet();
            return producer;
        }

        @Override
        protected DefaultMQPullConsumer newPullConsumer(RPCHook hook) {
            consumerCreations.incrementAndGet();
            return consumer;
        }
    }

    @Test
    void defaultProducerAppliesTransportBeforeStartAndKeepsInstancePoolSeparate() throws Exception {
        RecordingPool pool = new RecordingPool();
        OpsConnectionSettings settings = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, true);

        pool.withProducerDefault(settings, null, "anonymous", ignored -> null);
        pool.withProducer("namesrv:9876", null, "anonymous", ignored -> null);

        assertThat(pool.producerCreations.get()).isEqualTo(2);
        InOrder calls = inOrder(pool.producer);
        calls.verify(pool.producer).setNamesrvAddr("namesrv:9876");
        calls.verify(pool.producer).setVipChannelEnabled(true);
        calls.verify(pool.producer).setUseTLS(true);
        verify(pool.producer, times(2)).start();
        pool.shutdown();
    }

    @Test
    void changingDefaultConsumerTransportDoesNotReuseTheOldClient() throws Exception {
        RecordingPool pool = new RecordingPool();
        OpsConnectionSettings standard = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings tls = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, true);

        pool.withPullConsumerDefault(standard, null, "anonymous", ignored -> null);
        pool.withPullConsumerDefault(tls, null, "anonymous", ignored -> null);
        pool.withPullConsumerDefault(tls, null, "anonymous", ignored -> null);

        assertThat(pool.consumerCreations.get()).isEqualTo(2);
        verify(pool.consumer, times(2)).start();
        pool.shutdown();
    }

    @Test
    void managedChangeClosesOnlyObsoleteDefaultProducerAndConsumer() throws Exception {
        DefaultMQProducer oldProducer = mock(DefaultMQProducer.class);
        DefaultMQProducer instanceProducer = mock(DefaultMQProducer.class);
        DefaultMQProducer currentProducer = mock(DefaultMQProducer.class);
        DefaultMQPullConsumer oldConsumer = mock(DefaultMQPullConsumer.class);
        DefaultMQPullConsumer instanceConsumer = mock(DefaultMQPullConsumer.class);
        DefaultMQPullConsumer currentConsumer = mock(DefaultMQPullConsumer.class);
        AtomicInteger producers = new AtomicInteger();
        AtomicInteger consumers = new AtomicInteger();
        List<DefaultMQProducer> producerClients = List.of(oldProducer, instanceProducer, currentProducer);
        List<DefaultMQPullConsumer> consumerClients = List.of(oldConsumer, instanceConsumer, currentConsumer);
        MqClientPool pool = new MqClientPool() {
            @Override
            protected DefaultMQProducer newProducer(RPCHook hook) {
                return producerClients.get(producers.getAndIncrement());
            }

            @Override
            protected DefaultMQPullConsumer newPullConsumer(RPCHook hook) {
                return consumerClients.get(consumers.getAndIncrement());
            }
        };
        OpsConnectionSettings old = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings current = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, false);
        pool.withProducerDefault(old, null, "anonymous", ignored -> null);
        pool.withPullConsumerDefault(old, null, "anonymous", ignored -> null);
        pool.withProducer("namesrv:9876", null, "anonymous", ignored -> null);
        pool.withPullConsumer("namesrv:9876", null, "anonymous", ignored -> null);
        pool.withProducerDefault(current, null, "anonymous", ignored -> null);
        pool.withPullConsumerDefault(current, null, "anonymous", ignored -> null);

        pool.releaseInactiveManagedDefaults(current);
        pool.withProducer("namesrv:9876", null, "anonymous", ignored -> null);
        pool.withPullConsumerDefault(current, null, "anonymous", ignored -> null);

        assertThat(producers.get()).isEqualTo(3);
        assertThat(consumers.get()).isEqualTo(3);
        verify(oldProducer).shutdown();
        verify(oldConsumer).shutdown();
        verify(instanceProducer, never()).shutdown();
        verify(instanceConsumer, never()).shutdown();
        verify(currentProducer, never()).shutdown();
        verify(currentConsumer, never()).shutdown();
    }

    @Test
    void managedReleaseWaitsForAnInFlightSendBeforeProducerShutdown() throws Exception {
        RecordingPool pool = new RecordingPool();
        OpsConnectionSettings old = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", false, false);
        OpsConnectionSettings current = new OpsConnectionSettings(
                List.of("namesrv:9876"), "namesrv:9876", true, false);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        Thread inFlight = Thread.startVirtualThread(() -> result.set(pool.withProducerDefault(old, null,
                "anonymous", ignored -> {
                    started.countDown();
                    if (!proceed.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Test producer action was not released");
                    }
                    return "sent";
                })));
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            pool.releaseInactiveManagedDefaults(current);
            verify(pool.producer, never()).shutdown();
        } finally {
            proceed.countDown();
            inFlight.join();
        }

        assertThat(result.get()).isEqualTo("sent");
        verify(pool.producer).shutdown();
    }
}
