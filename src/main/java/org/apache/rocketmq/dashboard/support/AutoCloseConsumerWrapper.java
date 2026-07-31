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

package org.apache.rocketmq.dashboard.support;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.RPCHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AutoCloseConsumerWrapper {

    private final Logger logger = LoggerFactory.getLogger(AutoCloseConsumerWrapper.class);

    private static final AtomicReference<DefaultMQPullConsumer> CONSUMER_REF = new AtomicReference<>();
    private final AtomicBoolean isTaskScheduled = new AtomicBoolean(false);
    private final AtomicBoolean isClosing = new AtomicBoolean(false);
    private static volatile Instant lastUsedTime = Instant.now();
    // Number of threads currently using the consumer. The idle-close task must never
    // shut the consumer down while any thread is still using it.
    private final AtomicInteger inUseCount = new AtomicInteger(0);


    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor();

    public AutoCloseConsumerWrapper() {
        startIdleCheckTask();
    }


    public DefaultMQPullConsumer getConsumer(RPCHook rpcHook, Boolean useTLS) {
        lastUsedTime = Instant.now();

        // The whole acquisition must happen while holding the lock: close() also runs under
        // this lock, so the consumer we return cannot be shut down by the idle-close task in
        // between (without the lock, close() could run right after the null-check and shut
        // down a consumer that is about to be handed to the caller).
        synchronized (this) {
            DefaultMQPullConsumer consumer = CONSUMER_REF.get();
            if (consumer == null) {
                consumer = createNewConsumer(rpcHook, useTLS);
                CONSUMER_REF.set(consumer);
                try {
                    consumer.start();
                } catch (MQClientException e) {
                    consumer.shutdown();
                    CONSUMER_REF.set(null);
                    throw new RuntimeException("Failed to start consumer", e);
                }
            }
            inUseCount.incrementAndGet();
            return consumer;
        }
    }

    /**
     * Marks one usage of the consumer as finished. Must be called from a finally block after
     * every successful {@link #getConsumer} call, otherwise the in-use count never drops to
     * zero and the consumer will never be closed by the idle-close task again.
     */
    public void releaseConsumer() {
        inUseCount.decrementAndGet();
    }


    protected DefaultMQPullConsumer createNewConsumer(RPCHook rpcHook, Boolean useTLS) {
        return new DefaultMQPullConsumer(MixAll.TOOLS_CONSUMER_GROUP, rpcHook) {
            {
                setUseTLS(useTLS);
            }
        };
    }

    private void startIdleCheckTask() {
        if (!isTaskScheduled.get()) {
            synchronized (this) {
                if (!isTaskScheduled.get()) {
                    SCHEDULER.scheduleWithFixedDelay(() -> {
                        try {
                            checkAndCloseIdleConsumer();
                        } catch (Exception e) {
                            logger.error("Idle check failed", e);
                        }
                    }, 1, 1, TimeUnit.MINUTES);

                    isTaskScheduled.set(true);
                }
            }
        }
    }

    public void checkAndCloseIdleConsumer() {
        if (inUseCount.get() == 0 && shouldClose()) {
            synchronized (this) {
                // Re-check under the lock: a thread may have acquired the consumer while we
                // were waiting for the lock.
                if (inUseCount.get() == 0 && shouldClose()) {
                    close();
                }
            }
        }
    }

    private boolean shouldClose() {
        long idleTimeoutMs = 60_000;
        return CONSUMER_REF.get() != null &&
                Duration.between(lastUsedTime, Instant.now()).toMillis() > idleTimeoutMs;
    }


    public void close() {
        if (isClosing.compareAndSet(false, true)) {
            try {
                DefaultMQPullConsumer consumer = CONSUMER_REF.getAndSet(null);
                if (consumer != null) {
                    consumer.shutdown();
                }
                isTaskScheduled.set(false);
            } finally {
                isClosing.set(false);
            }
        }
    }

}
