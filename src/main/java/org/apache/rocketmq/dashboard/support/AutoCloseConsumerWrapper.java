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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AutoCloseConsumerWrapper {

    private final Logger logger = LoggerFactory.getLogger(GlobalRestfulResponseBodyAdvice.class);

    private static final AtomicReference<DefaultMQPullConsumer> CONSUMER_REF = new AtomicReference<>();
    private final AtomicBoolean isTaskScheduled = new AtomicBoolean(false);
    private final AtomicBoolean isClosing = new AtomicBoolean(false);
    private static volatile Instant lastUsedTime = Instant.now();


    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor();

    public AutoCloseConsumerWrapper() {
        startIdleCheckTask();
    }


    public DefaultMQPullConsumer getConsumer(RPCHook rpcHook,Boolean useTLS)  {
        lastUsedTime = Instant.now();

        DefaultMQPullConsumer consumer = CONSUMER_REF.get();
        if (consumer == null) {
            synchronized (this) {
                consumer = CONSUMER_REF.get();
                if (consumer == null) {
                    consumer = createNewConsumer(rpcHook,useTLS);
                    CONSUMER_REF.set(consumer);
                }
                try {
                    consumer.start();
                } catch (MQClientException e) {
                    consumer.shutdown();
                    CONSUMER_REF.set(null);
                    throw new RuntimeException("Failed to start consumer", e);

                }
            }
        }
        return consumer;
    }


    protected DefaultMQPullConsumer createNewConsumer(RPCHook rpcHook, Boolean useTLS) {
        return new DefaultMQPullConsumer(MixAll.TOOLS_CONSUMER_GROUP, rpcHook) {
            { setUseTLS(useTLS); } };
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
        if (shouldClose()) {
            synchronized (this) {
                if (shouldClose()) {
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
