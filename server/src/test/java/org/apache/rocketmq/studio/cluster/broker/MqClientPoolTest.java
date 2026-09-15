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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
}
