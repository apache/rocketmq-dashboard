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

class AlertCorrelationScopeTest {

    @Test
    void shouldMatchAlertsFromTheSameInstanceWithEqualResourceLabelsTest() {
        SystemAlertVO source = alert("instance-a", Map.of("brokerName", "broker-1"));
        SystemAlertVO candidate = alert("instance-a", Map.of("brokerName", "broker-1"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }

    @Test
    void shouldRejectAlertsFromDifferentInstancesTest() {
        SystemAlertVO source = alert("instance-a", Map.of("brokerName", "broker-1"));
        SystemAlertVO candidate = alert("instance-b", Map.of("brokerName", "broker-1"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isFalse();
    }

    @Test
    void shouldRejectSourceWithoutInstanceIdTest() {
        SystemAlertVO source = alert("  ", Map.of());
        SystemAlertVO candidate = alert("instance-a", Map.of());

        assertThat(AlertCorrelationScope.matches(source, candidate)).isFalse();
    }

    @Test
    void shouldRejectConflictingResourceLabelsTest() {
        SystemAlertVO source = alert("instance-a", Map.of("brokerName", "broker-1"));
        SystemAlertVO candidate = alert("instance-a", Map.of("brokerName", "broker-2"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isFalse();
    }

    @Test
    void shouldIgnoreBlankResourceLabelValuesTest() {
        SystemAlertVO source = alert("instance-a", Map.of("brokerName", "  "));
        SystemAlertVO candidate = alert("instance-a", Map.of("brokerName", "broker-2"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }

    @Test
    void shouldIgnoreLabelsOutsideTheResourceScopeTest() {
        SystemAlertVO source = alert("instance-a", Map.of("topic", "TopicA"));
        SystemAlertVO candidate = alert("instance-a", Map.of("topic", "TopicB"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }

    @Test
    void shouldTrimResourceLabelValuesBeforeComparisonTest() {
        SystemAlertVO source = alert("instance-a", Map.of("brokerName", " broker-1 "));
        SystemAlertVO candidate = alert("instance-a", Map.of("brokerName", "broker-1"));

        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }

    private static SystemAlertVO alert(String instanceId, Map<String, String> labels) {
        return SystemAlertVO.builder()
                .instanceId(instanceId)
                .labels(labels)
                .build();
    }
}
