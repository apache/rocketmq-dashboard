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
 * Pins the alert silence entity {@link RmqAlertSilence}: it maps to the {@code rmq_alert_silence}
 * table and every column round-trips - the silence window, its optional recurrence with time zone
 * and day mask, and who created it.
 */
class RmqAlertSilenceTest {

    @Test
    void theEntityMapsToTheSilenceTable() {
        TableName annotation = RmqAlertSilence.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_alert_silence");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAlertSilence silence = new RmqAlertSilence();
        silence.setId(7L);
        silence.setDomain("BUSINESS");
        silence.setRuleId(42L);
        silence.setInstanceId("instance-1");
        silence.setLabelsJson("{\"group\":\"order\"}");
        silence.setStartsAt(LocalDateTime.of(2026, 1, 2, 3, 0));
        silence.setEndsAt(LocalDateTime.of(2026, 1, 2, 5, 0));
        silence.setRecurrence("WEEKLY");
        silence.setTimeZone("Asia/Shanghai");
        silence.setRecurrenceDaysJson("[1,2,3,4,5]");
        silence.setRecurrenceUntil(LocalDateTime.of(2026, 2, 1, 0, 0));
        silence.setReason("maintenance window");
        silence.setCreatedBy("alice");

        assertThat(silence.getId()).isEqualTo(7L);
        assertThat(silence.getDomain()).isEqualTo("BUSINESS");
        assertThat(silence.getRuleId()).isEqualTo(42L);
        assertThat(silence.getInstanceId()).isEqualTo("instance-1");
        assertThat(silence.getLabelsJson()).isEqualTo("{\"group\":\"order\"}");
        assertThat(silence.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 0));
        assertThat(silence.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 5, 0));
        assertThat(silence.getRecurrence()).isEqualTo("WEEKLY");
        assertThat(silence.getTimeZone()).isEqualTo("Asia/Shanghai");
        assertThat(silence.getRecurrenceDaysJson()).isEqualTo("[1,2,3,4,5]");
        assertThat(silence.getRecurrenceUntil()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        assertThat(silence.getReason()).isEqualTo("maintenance window");
        assertThat(silence.getCreatedBy()).isEqualTo("alice");
    }
}
