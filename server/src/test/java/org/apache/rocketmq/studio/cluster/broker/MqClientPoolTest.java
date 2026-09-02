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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MqClientPoolTest {

    private static final class StubPool extends MqClientPool {
        private final DefaultMQPullConsumer pullConsumer = mock(DefaultMQPullConsumer.class);
        private final DefaultMQProducer producer = mock(DefaultMQProducer.class);

        @Override
        protected DefaultMQPullConsumer newPullConsumer(RPCHook rpcHook) {
            return pullConsumer;
        }

        @Override
        protected DefaultMQProducer newProducer(RPCHook rpcHook) {
            return producer;
        }
    }

    @Test
    void withProducerShouldSurviveCyclicCauseChainWithoutHanging() throws Exception {
        StubPool pool = new StubPool();

        // first <-> second form a two-exception cause cycle: the old
        // cause.getCause() != cause guard only caught a direct self-cycle.
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThatThrownBy(
                        () -> pool.withProducer("10.0.0.1:9876", null, "anon", client -> {
                            throw second;
                        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("client call failed: second")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(502)));
        verify(pool.producer, times(1)).start();
    }
}
