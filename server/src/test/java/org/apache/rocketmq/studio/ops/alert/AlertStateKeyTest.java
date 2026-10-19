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

/**
 * Pins the alert state key of {@link AlertStateKey}: one rule's state is keyed by the rule id
 * plus the resource fingerprint - and the record's value equality is what lets the state map
 * deduplicate concurrent evaluations of the same rule/resource pair.
 */
class AlertStateKeyTest {

    @Test
    void theKeyRoundTrips() {
        AlertStateKey key = new AlertStateKey(42L, "fp-1");
        assertThat(key.ruleId()).isEqualTo(42L);
        assertThat(key.fingerprint()).isEqualTo("fp-1");
    }

    @Test
    void keysAreValueEqual() {
        assertThat(new AlertStateKey(42L, "fp-1"))
                .isEqualTo(new AlertStateKey(42L, "fp-1"));
        assertThat(new AlertStateKey(42L, "fp-1").hashCode())
                .isEqualTo(new AlertStateKey(42L, "fp-1").hashCode());

        assertThat(new AlertStateKey(42L, "fp-1"))
                .isNotEqualTo(new AlertStateKey(42L, "fp-2"));
        assertThat(new AlertStateKey(42L, "fp-1"))
                .isNotEqualTo(new AlertStateKey(43L, "fp-1"));
    }
}
