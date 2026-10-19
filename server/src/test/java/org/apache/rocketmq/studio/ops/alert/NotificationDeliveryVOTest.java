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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the notification delivery row of {@link NotificationDeliveryVO}: one outbox delivery -
 * channel, status, attempt count, the scheduled next attempt, the last error and the delivered
 * timestamp. A pending delivery carries a next attempt and no delivered timestamp; a delivered
 * one the reverse.
 */
class NotificationDeliveryVOTest {

    @Test
    void aPendingDeliveryRoundTrips() {
        NotificationDeliveryVO delivery = NotificationDeliveryVO.builder()
                .id(7L)
                .channel("dingtalk")
                .status(NotificationOutboxStatus.RETRY_WAIT)
                .attemptCount(2)
                .nextAttemptAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .lastError("webhook timeout")
                .build();

        assertThat(delivery.getId()).isEqualTo(7L);
        assertThat(delivery.getChannel()).isEqualTo("dingtalk");
        assertThat(delivery.getStatus()).isEqualTo(NotificationOutboxStatus.RETRY_WAIT);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(delivery.getLastError()).isEqualTo("webhook timeout");
        assertThat(delivery.getDeliveredAt()).isNull();
    }

    @Test
    void aDeliveredDeliveryCarriesItsTimestamp() {
        NotificationDeliveryVO delivery = NotificationDeliveryVO.builder()
                .channel("dingtalk")
                .status(NotificationOutboxStatus.DELIVERED)
                .attemptCount(1)
                .deliveredAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();

        assertThat(delivery.getStatus()).isEqualTo(NotificationOutboxStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(delivery.getLastError()).isNull();
    }
}
