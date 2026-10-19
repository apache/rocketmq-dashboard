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
 * Pins {@link NotificationDeliveryPageVO}: one notification delivery row joined with the
 * alert event that produced it — the delivery lifecycle (channel, status, attempts, next
 * retry, last error, delivered-at) next to the alert identity (title, domain, transition,
 * instance). The deliveries table renders exactly this join, and the retry button keys on
 * status + nextAttemptAt, so the row must carry both sides untouched.
 */
class NotificationDeliveryPageVOTest {

    @Test
    void aRowCarriesTheDeliveryAndTheAlertBehindIt() {
        NotificationDeliveryPageVO row = NotificationDeliveryPageVO.builder()
                .id(51L)
                .alertId(12L)
                .channel("email")
                .status(NotificationOutboxStatus.RETRY_WAIT)
                .attemptCount(2)
                .nextAttemptAt(LocalDateTime.of(2026, 10, 13, 10, 0))
                .lastError("SMTP relay refused connection")
                .deliveredAt(null)
                .createdAt(LocalDateTime.of(2026, 10, 13, 9, 40))
                .messageContent("broker down")
                .alertTitle("Broker offline")
                .alertDomain(AlertDomain.CLUSTER)
                .transition("firing")
                .instanceId("instance-1")
                .build();

        assertThat(row.getId()).isEqualTo(51L);
        assertThat(row.getAlertId()).isEqualTo(12L);
        assertThat(row.getChannel()).isEqualTo("email");
        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.RETRY_WAIT);
        assertThat(row.getAttemptCount()).isEqualTo(2);
        assertThat(row.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 10, 0));
        assertThat(row.getLastError()).isEqualTo("SMTP relay refused connection");
        assertThat(row.getDeliveredAt()).isNull();
        assertThat(row.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 9, 40));
        assertThat(row.getMessageContent()).isEqualTo("broker down");
        assertThat(row.getAlertTitle()).isEqualTo("Broker offline");
        assertThat(row.getAlertDomain()).isEqualTo(AlertDomain.CLUSTER);
        assertThat(row.getTransition()).isEqualTo("firing");
        assertThat(row.getInstanceId()).isEqualTo("instance-1");
    }

    @Test
    void aDeliveredRowCarriesItsDeliveryTime() {
        NotificationDeliveryPageVO row = NotificationDeliveryPageVO.builder()
                .status(NotificationOutboxStatus.DELIVERED)
                .deliveredAt(LocalDateTime.of(2026, 10, 13, 9, 41))
                .build();

        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.DELIVERED);
        assertThat(row.getDeliveredAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 9, 41));
        assertThat(row.getNextAttemptAt()).isNull();
        assertThat(row.getAttemptCount()).isZero();
    }
}
