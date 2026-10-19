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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-group consume status of {@link ConsumerStatusVO}: the group, its delivery
 * status, the consume time and the retry count round-trip - the delivery table under a trace.
 */
class ConsumerStatusVOTest {

    @Test
    void theStatusRoundTripsThroughTheBuilder() {
        ConsumerStatusVO status = ConsumerStatusVO.builder()
                .group("order-group")
                .deliveryStatus(DeliveryStatus.failed)
                .consumeTime(1784112606000L)
                .retryCount(3)
                .build();

        assertThat(status.getGroup()).isEqualTo("order-group");
        assertThat(status.getDeliveryStatus()).isEqualTo(DeliveryStatus.failed);
        assertThat(status.getConsumeTime()).isEqualTo(1784112606000L);
        assertThat(status.getRetryCount()).isEqualTo(3);
    }

    @Test
    void aFreshStatusCountsNoRetries() {
        ConsumerStatusVO status = new ConsumerStatusVO();
        assertThat(status.getRetryCount()).isZero();
        assertThat(status.getConsumeTime()).isZero();
    }
}
