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

    default List<AlertRuleRuntimeVO> findRuntimeByRuleIds(List<AlertRuleVO> rules) {
        return List.of();
    }
}
