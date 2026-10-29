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
 * Pins the alert state entity {@link RmqAlertState}: it maps to the {@code rmq_alert_state}
 * table and every column round-trips - the per-fingerprint evaluation state including the
 * consecutive-hit counter, the lifecycle timestamps (pending, fired, notified, resolved) and
 * the optimistic-lock version.
 */
class RmqAlertStateTest {

    @Test
    void theEntityMapsToTheAlertStateTable() {
        TableName annotation = RmqAlertState.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_alert_state");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAlertState state = new RmqAlertState();
        state.setId(7L);
        state.setRuleId(42L);
        state.setFingerprint("fp-1");
        state.setStatus("firing");
        state.setConsecutiveHits(3);
        state.setCurrentValue(1200.5);
        state.setFirstPendingAt(LocalDateTime.of(2026, 1, 2, 3, 0));
        state.setFiredAt(LocalDateTime.of(2026, 1, 2, 3, 5));
        state.setLastNotifiedAt(LocalDateTime.of(2026, 1, 2, 3, 6));
        state.setResolvedAt(LocalDateTime.of(2026, 1, 2, 4, 0));
        state.setVersion(5);
        state.setGmtModified(LocalDateTime.of(2026, 1, 2, 4, 0));

        assertThat(state.getId()).isEqualTo(7L);
        assertThat(state.getRuleId()).isEqualTo(42L);
        assertThat(state.getFingerprint()).isEqualTo("fp-1");
        assertThat(state.getStatus()).isEqualTo("firing");
        assertThat(state.getConsecutiveHits()).isEqualTo(3);
        assertThat(state.getCurrentValue()).isEqualTo(1200.5);
        assertThat(state.getFirstPendingAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 0));
        assertThat(state.getFiredAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 5));
        assertThat(state.getLastNotifiedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 6));
        assertThat(state.getResolvedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 4, 0));
        assertThat(state.getVersion()).isEqualTo(5);
    }
}
