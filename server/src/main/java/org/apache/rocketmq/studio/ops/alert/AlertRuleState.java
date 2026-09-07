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

/** Persistable state for a rule/resource fingerprint, independent from individual event rows. */
public record AlertRuleState(AlertStateStatus status, int consecutiveHits, Double currentValue,
        Instant firstPendingAt, Instant firedAt, Instant lastNotifiedAt, Instant resolvedAt) {
    public static AlertRuleState initial() {
        return new AlertRuleState(AlertStateStatus.OK, 0, null, null, null, null, null);
    }
}
