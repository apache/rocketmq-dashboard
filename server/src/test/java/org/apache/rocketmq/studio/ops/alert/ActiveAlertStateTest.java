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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ActiveAlertState}, the persisted active state plus labels needed to
 * emit a lifecycle recovery event.
 */
class ActiveAlertStateTest {

    private static AlertStateKey key() {
        return new AlertStateKey(1L, "fingerprint-1");
    }

    private static AlertRuleState state() {
        return new AlertRuleState(AlertStateStatus.FIRING, 1, 90.0,
                null, null, null, null);
    }

    @Test
    void validatesRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ActiveAlertState(null, state(), "i1", Map.of()))
                .withMessage("key is required");
        assertThatNullPointerException()
                .isThrownBy(() -> new ActiveAlertState(key(), null, "i1", Map.of()))
                .withMessage("state is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ActiveAlertState(key(), state(), "  ", Map.of()))
                .withMessage("instanceId is required");
    }

    @Test
    void copiesLabelsDefensivelyAndDefaultsNullToEmpty() {
        Map<String, String> mutable = new HashMap<>();
        mutable.put("broker", "b1");
        ActiveAlertState active = new ActiveAlertState(key(), state(), "i1", mutable);

        mutable.put("extra", "e1");
        assertThat(active.labels()).containsExactlyEntriesOf(Map.of("broker", "b1"));
        assertThatThrownBy(() -> active.labels().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);

        ActiveAlertState nullLabels = new ActiveAlertState(key(), state(), "i1", null);
        assertThat(nullLabels.labels()).isEmpty();
    }

    @Test
    void exposesKeyStateAndInstance() {
        AlertStateKey key = key();
        AlertRuleState state = state();
        ActiveAlertState active = new ActiveAlertState(key, state, "instance-1", Map.of("broker", "b1"));

        assertThat(active.key()).isEqualTo(key);
        assertThat(active.state()).isEqualTo(state);
        assertThat(active.instanceId()).isEqualTo("instance-1");
        assertThat(active.labels()).containsEntry("broker", "b1");
    }
}
