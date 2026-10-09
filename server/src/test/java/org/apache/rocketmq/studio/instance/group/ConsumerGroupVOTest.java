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
package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the view defaults of {@link ConsumerGroupVO}: the subscribed-topic and instance lists are
 * empty rather than null (NPE-safe for the UI), a group reads as consume-stats-unavailable until
 * a provider proves otherwise, and the documented -1 sentinel for unavailable connection
 * inventory round-trips.
 */
class ConsumerGroupVOTest {

    @Test
    void theListFieldsDefaultToEmptyRatherThanNull() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        assertThat(group.getSubscribedTopics()).isNotNull().isEmpty();
        assertThat(group.getInstances()).isNotNull().isEmpty();
    }

    @Test
    void aFreshGroupReadsAsConsumeStatsUnavailable() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        assertThat(group.isConsumeStatsAvailable()).isFalse();
        assertThat(group.isConsumptionTimestampAvailable()).isFalse();
        assertThat(group.getTotalLag()).isZero();
        assertThat(group.getOnlineInstances()).isZero();
    }

    @Test
    void theUnavailableInventorySentinelRoundTrips() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setOnlineInstances(-1);
        group.setConsumeStatsAvailable(true);
        group.setSubscribedTopics(List.of("topic-1", "topic-2"));
        group.setSubscriptionDataType("JSON");
        group.setDeliveryOrderType("CONCURRENTLY");
        group.setRetryMaxTimes(16);
        group.setDelaySeconds(5);

        assertThat(group.getOnlineInstances()).isEqualTo(-1);
        assertThat(group.isConsumeStatsAvailable()).isTrue();
        assertThat(group.getSubscribedTopics()).containsExactly("topic-1", "topic-2");
        assertThat(group.getSubscriptionDataType()).isEqualTo("JSON");
        assertThat(group.getDeliveryOrderType()).isEqualTo("CONCURRENTLY");
        assertThat(group.getRetryMaxTimes()).isEqualTo(16);
        assertThat(group.getDelaySeconds()).isEqualTo(5);
    }
}
