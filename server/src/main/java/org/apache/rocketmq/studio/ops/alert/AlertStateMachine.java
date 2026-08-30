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
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;

@Component
public class AlertStateMachine {

    public AlertStateUpdate advance(AlertRuleState previous, AlertEvaluationResult evaluation,
            int requiredConsecutiveSamples, Instant now) {
        return advance(previous, evaluation, requiredConsecutiveSamples, Duration.ZERO, now);
    }

    public AlertStateUpdate advance(AlertRuleState previous, AlertEvaluationResult evaluation,
            int requiredConsecutiveSamples, Duration requiredDuration, Instant now) {
        return advance(previous, evaluation, requiredConsecutiveSamples, requiredDuration, Duration.ZERO, now);
    }

    public AlertStateUpdate advance(AlertRuleState previous, AlertEvaluationResult evaluation,
            int requiredConsecutiveSamples, Duration requiredDuration, Duration reminderInterval, Instant now) {
        if (requiredConsecutiveSamples < 1) {
            throw new IllegalArgumentException("requiredConsecutiveSamples must be positive");
        }
        if (requiredDuration == null || requiredDuration.isNegative()) {
            throw new IllegalArgumentException("requiredDuration must not be negative");
        }
        if (reminderInterval == null || reminderInterval.isNegative()) {
            throw new IllegalArgumentException("reminderInterval must not be negative");
        }
        AlertRuleState state = previous == null ? AlertRuleState.initial() : previous;
        if (!evaluation.matches()) {
            return new AlertStateUpdate(state, AlertStateTransition.NONE);
        }
        // A regular metric must not clear an alert merely because collection failed. The explicit
        // UNAVAILABLE condition is the sole opt-in path that turns a failed probe into a firing alert.
        if (evaluation.availability() != MetricAvailability.AVAILABLE && !evaluation.conditionMet()) {
            return new AlertStateUpdate(state, AlertStateTransition.NONE);
        }
        if (evaluation.conditionMet()) {
            return advanceHit(state, evaluation.currentValue(), requiredConsecutiveSamples, requiredDuration,
                    reminderInterval, now);
        }
        return advanceClear(state, evaluation.currentValue(), now);
    }

    private AlertStateUpdate advanceHit(AlertRuleState state, Double value, int required, Duration duration,
            Duration reminderInterval, Instant now) {
        if (state.status() == AlertStateStatus.ACKED) {
            return new AlertStateUpdate(new AlertRuleState(state.status(), state.consecutiveHits(), value,
                    state.firstPendingAt(), state.firedAt(), state.lastNotifiedAt(), null), AlertStateTransition.NONE);
        }
        if (state.status() == AlertStateStatus.FIRING) {
            Instant lastNotifiedAt = state.lastNotifiedAt();
            if (!reminderInterval.isZero() && (lastNotifiedAt == null || !now.isBefore(lastNotifiedAt.plus(reminderInterval)))) {
                return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.FIRING, state.consecutiveHits(), value,
                        state.firstPendingAt(), state.firedAt(), now, null), AlertStateTransition.REMINDER);
            }
            return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.FIRING, state.consecutiveHits(), value,
                    state.firstPendingAt(), state.firedAt(), lastNotifiedAt, null), AlertStateTransition.NONE);
        }
        int hits = state.consecutiveHits() + 1;
        Instant pendingAt = state.firstPendingAt() == null ? now : state.firstPendingAt();
        if (hits < required || now.isBefore(pendingAt.plus(duration))) {
            return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.PENDING, hits, value, pendingAt,
                    null, null, null), AlertStateTransition.PENDING);
        }
        return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.FIRING, hits, value, pendingAt, now,
                now, null), AlertStateTransition.FIRING);
    }

    private AlertStateUpdate advanceClear(AlertRuleState state, Double value, Instant now) {
        if (state.status() == AlertStateStatus.FIRING || state.status() == AlertStateStatus.ACKED) {
            return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.RESOLVED, 0, value, null,
                    state.firedAt(), state.lastNotifiedAt(), now), AlertStateTransition.RESOLVED);
        }
        return new AlertStateUpdate(new AlertRuleState(AlertStateStatus.OK, 0, value, null, null, null, null),
                AlertStateTransition.NONE);
    }
}
