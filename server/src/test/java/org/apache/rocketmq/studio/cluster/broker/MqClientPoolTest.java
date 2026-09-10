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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the lifecycle guards of {@link MqClientPool}. Client creation is excluded
 * on purpose: creating a pull consumer/producer would start remoting against a live
 * NameServer, so these tests pin down the deterministic guard paths only (address
 * requirement, closed-pool rejection, and no-op releases).
 */
class MqClientPoolTest {

    @Test
    void requiresANameServerAddressBeforeAnyClientWork() {
        MqClientPool pool = new MqClientPool();

        assertThatThrownBy(() -> pool.withPullConsumer("   ", null, "anonymous", client -> "unused"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> pool.withProducer("\n", null, null, client -> "unused"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
    }

    @Test
    void rejectsClientUseAfterThePoolIsClosed() {
        MqClientPool pool = new MqClientPool();
        pool.shutdown();

        assertThatThrownBy(() -> pool.withPullConsumer("127.0.0.1:9876", null, "anonymous",
                client -> "unused"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(503));
        assertThatThrownBy(() -> pool.withProducer("127.0.0.1:9876", null, "anonymous",
                client -> "unused"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(503));
    }

    @Test
    void addressCheckTakesPrecedenceOverTheClosedFlag() {
        MqClientPool pool = new MqClientPool();
        pool.shutdown();

        assertThatThrownBy(() -> pool.withPullConsumer("  ", null, "anonymous", client -> "unused"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
    }

    @Test
    void releaseIsANoOpForBlankOrUnknownEndpoints() {
        MqClientPool pool = new MqClientPool();

        assertThatCode(() -> pool.release("  ")).doesNotThrowAnyException();
        assertThatCode(() -> pool.release(null, "anonymous")).doesNotThrowAnyException();
        // An endpoint that was never pooled has nothing to release.
        assertThatCode(() -> pool.release("10.0.0.1:9876")).doesNotThrowAnyException();
        assertThatCode(() -> pool.release("10.0.0.1:9876", "other-identity"))
                .doesNotThrowAnyException();
        pool.shutdown();
    }
}
