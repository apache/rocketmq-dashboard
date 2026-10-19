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

import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public interface AlertStateRepository {
    Optional<AlertRuleState> find(AlertStateKey key);

    /**
     * Persists the next state and returns whether this evaluator won the state transition.
     * Callers must only emit lifecycle events after a successful write.
     */
    boolean save(AlertStateKey key, AlertRuleState state);

    /**
     * Acknowledges the currently firing incident only when it is the same firing
     * occurrence represented by the alert event being acknowledged.
     */
    boolean acknowledge(AlertStateKey key, Instant firedAt);

    void deleteByRuleId(Long ruleId);

    /**
     * Drops the runtime states whose incidents were acknowledged and whose events are being cleared.
     *
     * <p>A state row keeps only the rule and the fingerprint; the incident's instance and labels -
     * which a RESOLVED event needs - live on its alert row. Once that row is gone the state can
     * neither be resolved by reconciliation nor fire again, so the episodes behind the cleared
     * events go with them and a still-breaching series starts a fresh incident.
     */
    default int deleteForAcknowledgedAlerts() {
        return 0;
    }

    default List<ActiveAlertState> findActive(MetricCollectionScope scope, List<AlertRuleVO> rules) {
        return List.of();
    }

    default List<AlertRuleRuntimeVO> findRuntimeByRuleIds(List<AlertRuleVO> rules) {
        return List.of();
    }
}
