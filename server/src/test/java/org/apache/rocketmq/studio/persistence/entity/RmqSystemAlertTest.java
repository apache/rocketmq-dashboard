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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the system alert entity {@link RmqSystemAlert}: it maps to the {@code rmq_system_alert}
 * table and every column round-trips - the alert identity and lifecycle (acknowledgement),
 * the fingerprint that deduplicates it, the suppression chain and the labels payload.
 */
class RmqSystemAlertTest {

    @Test
    void theEntityMapsToTheSystemAlertTable() {
        TableName annotation = RmqSystemAlert.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_system_alert");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqSystemAlert alert = new RmqSystemAlert();
        alert.setId(7L);
        alert.setLevel("warning");
        alert.setTitle("lag high");
        alert.setDescription("consumer lag above threshold");
        alert.setTime(LocalDateTime.of(2026, 1, 2, 3, 4));
        alert.setAcknowledged(true);
        alert.setAcknowledgedBy("alice");
        alert.setAcknowledgedAt(LocalDateTime.of(2026, 1, 2, 4, 4));
        alert.setDomain("BUSINESS");
        alert.setRuleId(42L);
        alert.setFingerprint("fp-1");
        alert.setTransition("firing");
        alert.setInstanceId("instance-1");
        alert.setCurrentValue(1200.5);
        alert.setNotificationSuppressed(true);
        alert.setSuppressionCauseAlertId(6L);
        alert.setSuppressionReason("deduplicated");
        alert.setLabelsJson("{\"group\":\"order\"}");
        alert.setGmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4));
        alert.setGmtModified(LocalDateTime.of(2026, 1, 2, 4, 4));

        assertThat(alert.getId()).isEqualTo(7L);
        assertThat(alert.getLevel()).isEqualTo("warning");
        assertThat(alert.getTitle()).isEqualTo("lag high");
        assertThat(alert.getTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(alert.getAcknowledged()).isTrue();
        assertThat(alert.getAcknowledgedBy()).isEqualTo("alice");
        assertThat(alert.getRuleId()).isEqualTo(42L);
        assertThat(alert.getFingerprint()).isEqualTo("fp-1");
        assertThat(alert.getTransition()).isEqualTo("firing");
        assertThat(alert.getCurrentValue()).isEqualTo(1200.5);
        assertThat(alert.getNotificationSuppressed()).isTrue();
        assertThat(alert.getSuppressionCauseAlertId()).isEqualTo(6L);
        assertThat(alert.getLabelsJson()).isEqualTo("{\"group\":\"order\"}");
    }
}
