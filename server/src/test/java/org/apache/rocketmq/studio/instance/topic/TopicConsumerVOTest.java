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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the topic consumer row of {@link TopicConsumerVO}: the group's consumption shape
 * round-trips, and metricsAvailable defaults to true - a row reads as real until the provider
 * says otherwise, so the UI never shows a silent zero.
 */
class TopicConsumerVOTest {

    @Test
    void theConsumptionShapeRoundTrips() {
        TopicConsumerVO consumer = TopicConsumerVO.builder()
                .group("order-group")
                .consumeType(ConsumeType.CLUSTERING)
                .messageModel("CLUSTERING")
                .consumeTps(42.5)
                .diffTotal(1000L)
                .metricsAvailable(true)
                .build();

        assertThat(consumer.getGroup()).isEqualTo("order-group");
        assertThat(consumer.getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(consumer.getMessageModel()).isEqualTo("CLUSTERING");
        assertThat(consumer.getConsumeTps()).isEqualTo(42.5);
        assertThat(consumer.getDiffTotal()).isEqualTo(1000L);
        assertThat(consumer.isMetricsAvailable()).isTrue();
    }

    @Test
    void aFreshRowReadsAsRealMetrics() {
        TopicConsumerVO consumer = new TopicConsumerVO();
        assertThat(consumer.isMetricsAvailable()).isTrue();
        assertThat(consumer.getConsumeTps()).isZero();
        assertThat(consumer.getDiffTotal()).isZero();
    }

    @Test
    void aRowWithoutMetricsCanBeFlagged() {
        TopicConsumerVO consumer = TopicConsumerVO.builder()
                .group("order-group")
                .metricsAvailable(false)
                .build();
        assertThat(consumer.isMetricsAvailable()).isFalse();
    }
}
