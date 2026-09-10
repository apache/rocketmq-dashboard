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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AlertCorrelationScope}, the shared instance/resource-scope matcher
 * used to correlate cross-domain alerts: an alert only matches another alert on the same
 * instance whose declared resource labels never conflict.
 */
class AlertCorrelationScopeTest {

    private static SystemAlertVO alert(String instanceId, Map<String, String> labels) {
        return SystemAlertVO.builder().instanceId(instanceId).labels(labels).build();
    }

    @Test
    void rejectsAlertsWithoutASourceInstanceId() {
        assertThat(AlertCorrelationScope.matches(alert(null, Map.of()), alert("i1", Map.of()))).isFalse();
        assertThat(AlertCorrelationScope.matches(alert("  ", Map.of()), alert("i1", Map.of()))).isFalse();
    }

    @Test
    void requiresMatchingInstanceIds() {
        assertThat(AlertCorrelationScope.matches(alert("i1", Map.of()), alert("i2", Map.of()))).isFalse();
        assertThat(AlertCorrelationScope.matches(alert("i1", Map.of()), alert("i1", Map.of()))).isTrue();
        // The source side is trimmed before comparison; the candidate side is matched as-is.
        assertThat(AlertCorrelationScope.matches(alert(" i1 ", Map.of()), alert("i1", Map.of()))).isTrue();
        assertThat(AlertCorrelationScope.matches(alert("i1", Map.of()), alert(" i1 ", Map.of()))).isFalse();
    }

    @Test
    void treatsResourceLabelsAsSharedOnlyWhenBothSidesDeclareThem() {
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of("brokerName", "b1")),
                alert("i1", Map.of("brokerName", "b2")))).isFalse();
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of("brokerName", "b1")),
                alert("i1", Map.of()))).isTrue();
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of()),
                alert("i1", Map.of("brokerName", "b2")))).isTrue();
    }

    @Test
    void comparesDeclaredResourceLabelsAfterTrimming() {
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of("brokerName", " b1 ")),
                alert("i1", Map.of("brokerName", "b1")))).isTrue();
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of("brokerName", "b1", "clusterName", "c1")),
                alert("i1", Map.of("brokerName", "b1", "clusterName", "c2")))).isFalse();
        // Whitespace-only label values are treated as absent.
        assertThat(AlertCorrelationScope.matches(
                alert("i1", Map.of("brokerName", " ")),
                alert("i1", Map.of("brokerName", "b2")))).isTrue();
    }
}
