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

import java.time.Instant;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStateMachineTest {
    private final AlertStateMachine stateMachine = new AlertStateMachine();
    private final Instant now = Instant.parse("2026-08-21T00:00:00Z");

    @Test
    void transitionsFromPendingToFiringAfterRequiredHitsTest() {
        AlertStateUpdate pending = stateMachine.advance(null, met(), 2, now);
        AlertStateUpdate firing = stateMachine.advance(pending.state(), met(), 2, now.plusSeconds(30));

        assertThat(pending.transition()).isEqualTo(AlertStateTransition.PENDING);
        assertThat(pending.state().status()).isEqualTo(AlertStateStatus.PENDING);
        assertThat(firing.transition()).isEqualTo(AlertStateTransition.FIRING);
        assertThat(firing.state().status()).isEqualTo(AlertStateStatus.FIRING);
    }

    @Test
    void clearsFiringAlertWhenAvailableSampleRecoversTest() {
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 2, 0.9, now, now, now, null);

        AlertStateUpdate update = stateMachine.advance(firing, clear(), 2, now.plusSeconds(30));

        assertThat(update.transition()).isEqualTo(AlertStateTransition.RESOLVED);
        assertThat(update.state().status()).isEqualTo(AlertStateStatus.RESOLVED);
    }

    @Test
    void unavailableCollectionDoesNotResolveFiringAlertTest() {
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 2, 0.9, now, now, now, null);
        AlertEvaluationResult unavailable = new AlertEvaluationResult(true, false, null,
                MetricAvailability.UNAVAILABLE);

        AlertStateUpdate update = stateMachine.advance(firing, unavailable, 2, now.plusSeconds(30));

        assertThat(update.transition()).isEqualTo(AlertStateTransition.NONE);
        assertThat(update.state()).isEqualTo(firing);
    }

    @Test
    void firesForExplicitUnavailableConditionTest() {
        AlertEvaluationResult unavailable = new AlertEvaluationResult(true, true, null,
                MetricAvailability.UNAVAILABLE);

        AlertStateUpdate update = stateMachine.advance(null, unavailable, 1, now);

        assertThat(update.transition()).isEqualTo(AlertStateTransition.FIRING);
        assertThat(update.state().status()).isEqualTo(AlertStateStatus.FIRING);
    }

    @Test
    void waitsForTheConfiguredDurationAfterTheFirstMatchingSampleTest() {
        AlertStateUpdate pending = stateMachine.advance(null, met(), 1, Duration.ofMinutes(5), now);
        AlertStateUpdate stillPending = stateMachine.advance(pending.state(), met(), 1, Duration.ofMinutes(5),
                now.plusSeconds(299));
        AlertStateUpdate firing = stateMachine.advance(stillPending.state(), met(), 1, Duration.ofMinutes(5),
                now.plusSeconds(300));

        assertThat(pending.transition()).isEqualTo(AlertStateTransition.PENDING);
        assertThat(stillPending.transition()).isEqualTo(AlertStateTransition.PENDING);
        assertThat(firing.transition()).isEqualTo(AlertStateTransition.FIRING);
    }

    @Test
    void remindsOnlyForAnUnacknowledgedFiringAlertAfterTheConfiguredIntervalTest() {
        AlertRuleState firing = new AlertRuleState(AlertStateStatus.FIRING, 1, 0.9, now, now, now, null);

        AlertStateUpdate beforeDue = stateMachine.advance(firing, met(), 1, Duration.ZERO,
                Duration.ofMinutes(30), now.plusSeconds(29 * 60));
        AlertStateUpdate reminder = stateMachine.advance(firing, met(), 1, Duration.ZERO,
                Duration.ofMinutes(30), now.plusSeconds(30 * 60));
        AlertRuleState acknowledged = new AlertRuleState(AlertStateStatus.ACKED, 1, 0.9, now, now, now, null);
        AlertStateUpdate noReminder = stateMachine.advance(acknowledged, met(), 1, Duration.ZERO,
                Duration.ofMinutes(30), now.plusSeconds(60 * 60));

        assertThat(beforeDue.transition()).isEqualTo(AlertStateTransition.NONE);
        assertThat(reminder.transition()).isEqualTo(AlertStateTransition.REMINDER);
        assertThat(reminder.state().lastNotifiedAt()).isEqualTo(now.plusSeconds(30 * 60));
        assertThat(noReminder.transition()).isEqualTo(AlertStateTransition.NONE);
    }

    private static AlertEvaluationResult met() {
        return new AlertEvaluationResult(true, true, 0.9, MetricAvailability.AVAILABLE);
    }

    private static AlertEvaluationResult clear() {
        return new AlertEvaluationResult(true, false, 0.5, MetricAvailability.AVAILABLE);
    }
}
