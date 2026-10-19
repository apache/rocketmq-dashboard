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

import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the system alert view of {@link SystemAlertVO}: the emitted alert as the feed renders
 * it - identity, lifecycle, fingerprint, suppression chain and the labels payload - with a
 * fresh alert reading as unacknowledged and unsuppressed.
 */
class SystemAlertVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(7L)
                .level(AlertLevel.warning)
                .title("lag high")
                .description("consumer lag above threshold")
                .time(LocalDateTime.of(2026, 1, 2, 3, 4))
                .acknowledged(true)
                .acknowledgedBy("alice")
                .acknowledgedAt(LocalDateTime.of(2026, 1, 2, 4, 4))
                .domain(AlertDomain.BUSINESS)
                .ruleId(42L)
                .fingerprint("fp-1")
                .transition("firing")
                .instanceId("instance-1")
                .currentValue(1200.5)
                .notificationSuppressed(true)
                .suppressionCauseAlertId(6L)
                .suppressionReason("deduplicated")
                .labels(Map.of("group", "order"))
                .build();

        assertThat(alert.getId()).isEqualTo(7L);
        assertThat(alert.getLevel()).isEqualTo(AlertLevel.warning);
        assertThat(alert.getTitle()).isEqualTo("lag high");
        assertThat(alert.isAcknowledged()).isTrue();
        assertThat(alert.getAcknowledgedBy()).isEqualTo("alice");
        assertThat(alert.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(alert.getRuleId()).isEqualTo(42L);
        assertThat(alert.getFingerprint()).isEqualTo("fp-1");
        assertThat(alert.getTransition()).isEqualTo("firing");
        assertThat(alert.getCurrentValue()).isEqualTo(1200.5);
        assertThat(alert.isNotificationSuppressed()).isTrue();
        assertThat(alert.getSuppressionCauseAlertId()).isEqualTo(6L);
        assertThat(alert.getLabels()).containsEntry("group", "order");
    }

    @Test
    void aFreshAlertIsUnacknowledgedAndUnsuppressed() {
        SystemAlertVO alert = new SystemAlertVO();
        assertThat(alert.isAcknowledged()).isFalse();
        assertThat(alert.isNotificationSuppressed()).isFalse();
        assertThat(alert.getLabels()).isEmpty();
    }
}
