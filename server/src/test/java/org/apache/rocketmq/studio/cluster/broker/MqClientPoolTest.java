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
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MqClientPoolTest {

    @Test
    void releaseShouldWaitForInFlightProducerBeforeShutdownTest() throws Exception {
        try (MockedConstruction<DefaultMQProducer> construction =
                     mockConstruction(DefaultMQProducer.class)) {
            MqClientPool pool = new MqClientPool();
            pool.withProducer("namesrv:9876", null, "credential-a", ignored -> null);
            DefaultMQProducer retiredProducer = construction.constructed().getFirst();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread inFlight = Thread.startVirtualThread(() -> {
                try {
                    pool.withProducer("namesrv:9876", null, "credential-a", ignored -> {
                        started.countDown();
                        if (!proceed.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Test producer action was not released");
                        }
                        return null;
                    });
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                pool.release("namesrv:9876");
                verify(retiredProducer, never()).shutdown();

                pool.withProducer("namesrv:9876", null, "credential-a", ignored -> null);
                assertThat(construction.constructed()).hasSize(2);
                verify(construction.constructed().get(1), never()).shutdown();
            } finally {
                proceed.countDown();
                inFlight.join(2000);
            }

            assertThat(inFlight.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
            verify(retiredProducer).shutdown();
            pool.shutdown();
        }
    }

    @Test
    void credentialReleaseShouldWaitForInFlightConsumerBeforeShutdownTest() throws Exception {
        try (MockedConstruction<DefaultMQPullConsumer> construction =
                     mockConstruction(DefaultMQPullConsumer.class)) {
            MqClientPool pool = new MqClientPool();
            pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> null);
            DefaultMQPullConsumer retiredConsumer = construction.constructed().getFirst();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch proceed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread inFlight = Thread.startVirtualThread(() -> {
                try {
                    pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> {
                        started.countDown();
                        if (!proceed.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Test consumer action was not released");
                        }
                        return null;
                    });
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                pool.release("namesrv:9876", "credential-a");
                verify(retiredConsumer, never()).shutdown();

                pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> null);
                assertThat(construction.constructed()).hasSize(2);
                verify(construction.constructed().get(1), never()).shutdown();
            } finally {
                proceed.countDown();
                inFlight.join(2000);
            }

            assertThat(inFlight.isAlive()).isFalse();
            assertThat(failure.get()).isNull();
            verify(retiredConsumer).shutdown();
            pool.shutdown();
        }
    }
}
