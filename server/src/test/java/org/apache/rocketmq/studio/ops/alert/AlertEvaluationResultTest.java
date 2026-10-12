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

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the pure evaluation result of {@link AlertEvaluationResult}: one rule/sample comparison
 * without persistence or side effects - matches (the verdict the state machine consumes) is
 * deliberately separate from conditionMet (the raw threshold comparison), because the duration
 * window combines multiple samples before matches becomes true.
 */
class AlertEvaluationResultTest {

    @Test
    void theVerdictAndTheRawComparisonAreSeparate() {
        AlertEvaluationResult firing = new AlertEvaluationResult(
                true, true, 1200.5, MetricAvailability.AVAILABLE);
        assertThat(firing.matches()).isTrue();
        assertThat(firing.conditionMet()).isTrue();
        assertThat(firing.currentValue()).isEqualTo(1200.5);
        assertThat(firing.availability()).isEqualTo(MetricAvailability.AVAILABLE);

        // the threshold is met but the duration window has not accumulated yet:
        // the raw comparison is true while the verdict stays false
        AlertEvaluationResult pending = new AlertEvaluationResult(
                false, true, 1200.5, MetricAvailability.AVAILABLE);
        assertThat(pending.matches()).isFalse();
        assertThat(pending.conditionMet()).isTrue();
    }

    @Test
    void anUnavailableSampleCarriesNoValue() {
        AlertEvaluationResult unavailable = new AlertEvaluationResult(
                false, false, null, MetricAvailability.UNAVAILABLE);
        assertThat(unavailable.matches()).isFalse();
        assertThat(unavailable.conditionMet()).isFalse();
        assertThat(unavailable.currentValue()).isNull();
        assertThat(unavailable.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
    }
}
