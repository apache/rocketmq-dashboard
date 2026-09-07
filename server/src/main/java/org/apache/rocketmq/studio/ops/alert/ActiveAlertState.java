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

import java.util.Map;
import java.util.Objects;

/** Persisted active state plus labels needed to emit a lifecycle recovery event. */
public record ActiveAlertState(AlertStateKey key, AlertRuleState state, String instanceId, Map<String, String> labels) {
    public ActiveAlertState {
        Objects.requireNonNull(key, "key is required");
        Objects.requireNonNull(state, "state is required");
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("instanceId is required");
        }
        labels = Map.copyOf(labels == null ? Map.of() : labels);
    }
}
