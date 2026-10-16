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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    void withProducerShouldRestoreInterruptFlagWhenActionIsInterruptedTest() throws Exception {
        try (MockedConstruction<DefaultMQProducer> construction =
                     mockConstruction(DefaultMQProducer.class)) {
            MqClientPool pool = new MqClientPool();
            pool.withProducer("namesrv:9876", null, "credential-a", ignored -> null);
            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = Thread.startVirtualThread(() -> {
                try {
                    pool.withProducer("namesrv:9876", null, "credential-a", ignored -> {
                        started.countDown();
                        // A real interruptible blocking operation, like a remoting semaphore wait.
                        new CountDownLatch(1).await();
                        return null;
                    });
                } catch (Throwable throwable) {
                    failure.set(throwable);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
            } finally {
                caller.join(2000);
            }

            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("RocketMQ client call failed")
                    .hasMessageContaining("InterruptedException");
            // The wrapper must restore the flag the JVM cleared when the action was interrupted.
            assertThat(interrupted).isTrue();
            // The lease was still released, so the pooled producer remains usable.
            String producerReplay = pool.withProducer("namesrv:9876", null, "credential-a", ignored -> "ok");
            assertThat(producerReplay).isEqualTo("ok");
            assertThat(construction.constructed()).hasSize(1);
            pool.shutdown();
        }
    }

    @Test
    void withPullConsumerShouldRestoreInterruptFlagWhenActionIsInterruptedTest() throws Exception {
        try (MockedConstruction<DefaultMQPullConsumer> construction =
                     mockConstruction(DefaultMQPullConsumer.class)) {
            MqClientPool pool = new MqClientPool();
            pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> null);
            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = Thread.startVirtualThread(() -> {
                try {
                    pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> {
                        started.countDown();
                        // A real interruptible blocking operation, like a remoting semaphore wait.
                        new CountDownLatch(1).await();
                        return null;
                    });
                } catch (Throwable throwable) {
                    failure.set(throwable);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
            } finally {
                caller.join(2000);
            }

            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("RocketMQ client call failed")
                    .hasMessageContaining("InterruptedException");
            // The wrapper must restore the flag the JVM cleared when the action was interrupted.
            assertThat(interrupted).isTrue();
            // The lease was still released, so the pooled consumer remains usable.
            String consumerReplay = pool.withPullConsumer("namesrv:9876", null, "credential-a", ignored -> "ok");
            assertThat(consumerReplay).isEqualTo("ok");
            assertThat(construction.constructed()).hasSize(1);
            pool.shutdown();
        }
    }

    @Test
    void withProducerShouldLeaveInterruptFlagUnsetForOrdinaryFailuresTest() throws Exception {
        try (MockedConstruction<DefaultMQProducer> construction =
                     mockConstruction(DefaultMQProducer.class)) {
            MqClientPool pool = new MqClientPool();
            pool.withProducer("namesrv:9876", null, "credential-a", ignored -> null);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = Thread.startVirtualThread(() -> {
                try {
                    pool.withProducer("namesrv:9876", null, "credential-a", ignored -> {
                        throw new IllegalStateException("send rejected");
                    });
                } catch (Throwable throwable) {
                    failure.set(throwable);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            caller.join(2000);

            assertThat(caller.isAlive()).isFalse();
            assertThat(failure.get())
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("send rejected");
            // An ordinary failure is no reason to flag the calling thread as interrupted.
            assertThat(interrupted).isFalse();
            pool.shutdown();
        }
    }
}
