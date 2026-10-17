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

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the active alert state of {@link ActiveAlertState}: the key, the state it carries and
 * the identity context (instance, labels) travel together - one rule firing on one resource.
 */
class ActiveAlertStateTest {

    @Test
    void theActiveStateCarriesKeyStateAndContext() {
        AlertStateKey key = new AlertStateKey(42L, "fp-1");
        AlertRuleState state = new AlertRuleState(
                AlertStateStatus.FIRING, 3, 1200.5,
                Instant.parse("2026-01-02T03:00:00Z"), Instant.parse("2026-01-02T03:05:00Z"),
                null, null);
        ActiveAlertState active = new ActiveAlertState(
                key, state, "instance-1", Map.of("group", "order"));

        assertThat(active.key()).isEqualTo(key);
        assertThat(active.state()).isEqualTo(state);
        assertThat(active.instanceId()).isEqualTo("instance-1");
        assertThat(active.labels()).containsEntry("group", "order");
    }

    @Test
    void theRecordIsValueEqualForTheSameTriple() {
        AlertStateKey key = new AlertStateKey(42L, "fp-1");
        AlertRuleState state = AlertRuleState.initial();
        assertThat(new ActiveAlertState(key, state, "instance-1", Map.of()))
                .isEqualTo(new ActiveAlertState(key, state, "instance-1", Map.of()));
    }
}
