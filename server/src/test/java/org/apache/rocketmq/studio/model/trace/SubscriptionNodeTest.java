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
package org.apache.rocketmq.studio.model.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the consumer side of a message trace: {@link SubscriptionNode} carries one subscription
 * group and its consume trace nodes.
 */
class SubscriptionNodeTest {

    @Test
    void theGroupAndItsConsumeNodesRoundTrip() {
        TraceNode consume = new TraceNode();
        consume.setRequestId("req-1");

        SubscriptionNode subscription = new SubscriptionNode();
        subscription.setSubscriptionGroup("order-group");
        subscription.setConsumeNodeList(List.of(consume));

        assertThat(subscription.getSubscriptionGroup()).isEqualTo("order-group");
        assertThat(subscription.getConsumeNodeList()).containsExactly(consume);
    }

    @Test
    void aFreshSubscriptionCarriesNoNodes() {
        SubscriptionNode subscription = new SubscriptionNode();
        assertThat(subscription.getSubscriptionGroup()).isNull();
        assertThat(subscription.getConsumeNodeList()).isNull();
    }
}
