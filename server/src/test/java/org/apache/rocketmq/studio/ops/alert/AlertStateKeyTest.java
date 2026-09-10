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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link AlertStateKey}, the per-rule/fingerprint key that identifies
 * persisted alert state.
 */
class AlertStateKeyTest {

    @Test
    void rejectsNullOrNonPositiveRuleIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlertStateKey(null, "fp"))
                .withMessage("ruleId is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlertStateKey(0L, "fp"))
                .withMessage("ruleId is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlertStateKey(-1L, "fp"))
                .withMessage("ruleId is required");
    }

    @Test
    void rejectsBlankFingerprints() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlertStateKey(1L, null))
                .withMessage("fingerprint is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AlertStateKey(1L, "  "))
                .withMessage("fingerprint is required");
    }

    @Test
    void carriesRuleAndFingerprintWithValueEquality() {
        AlertStateKey key = new AlertStateKey(7L, "fingerprint-1");

        assertThat(key.ruleId()).isEqualTo(7L);
        assertThat(key.fingerprint()).isEqualTo("fingerprint-1");
        assertThat(key).isEqualTo(new AlertStateKey(7L, "fingerprint-1"))
                .hasSameHashCodeAs(new AlertStateKey(7L, "fingerprint-1"));
        assertThat(key).isNotEqualTo(new AlertStateKey(8L, "fingerprint-1"));
    }
}
