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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertEvaluationResultTest {

    @Test
    void exposesAllRecordComponents() {
        AlertEvaluationResult result = new AlertEvaluationResult(true, false, 98.5,
                MetricAvailability.AVAILABLE);

        assertEquals(true, result.matches());
        assertEquals(false, result.conditionMet());
        assertEquals(98.5, result.currentValue());
        assertEquals(MetricAvailability.AVAILABLE, result.availability());
    }

    @Test
    void currentValueMayBeNull() {
        AlertEvaluationResult result = new AlertEvaluationResult(true, true, null,
                MetricAvailability.UNAVAILABLE);

        assertNull(result.currentValue());
    }

    @Test
    void equalityFollowsRecordComponents() {
        AlertEvaluationResult a = new AlertEvaluationResult(true, true, 98.5,
                MetricAvailability.AVAILABLE);
        AlertEvaluationResult same = new AlertEvaluationResult(true, true, 98.5,
                MetricAvailability.AVAILABLE);
        AlertEvaluationResult different = new AlertEvaluationResult(true, false, 98.5,
                MetricAvailability.AVAILABLE);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
