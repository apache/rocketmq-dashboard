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
 * Pins the correlation rules of {@link AlertCorrelationScope}: alerts must sit on the same
 * instance (compared after trimming), conflicting resource labels block a match, a label present
 * on only one side does not, and labels outside the resource set are ignored.
 */
class AlertCorrelationScopeTest {

    private SystemAlertVO alert(String instanceId, Map<String, String> labels) {
        return SystemAlertVO.builder().instanceId(instanceId).labels(labels).build();
    }

    @Test
    void aBlankSourceInstanceIdMatchesNothing() {
        assertThat(AlertCorrelationScope.matches(alert(null, null), alert(null, null))).isFalse();
        assertThat(AlertCorrelationScope.matches(alert("  ", null), alert("  ", null))).isFalse();
    }

    @Test
    void alertsOnDifferentInstancesNeverMatch() {
        assertThat(AlertCorrelationScope.matches(alert("instance-1", null), alert("instance-2", null))).isFalse();
    }

    @Test
    void theInstanceIdIsComparedAfterTrimming() {
        assertThat(AlertCorrelationScope.matches(alert("instance-1 ", null), alert("instance-1", null))).isTrue();
    }

    @Test
    void nullLabelsAreTreatedAsEmptyAndStillMatch() {
        assertThat(AlertCorrelationScope.matches(alert("instance-1", null), alert("instance-1", null))).isTrue();
    }

    @Test
    void conflictingResourceLabelsBlockTheMatch() {
        SystemAlertVO source = alert("instance-1", Map.of("brokerName", "broker-a"));
        SystemAlertVO otherBroker = alert("instance-1", Map.of("brokerName", "broker-b"));
        assertThat(AlertCorrelationScope.matches(source, otherBroker)).isFalse();

        SystemAlertVO otherCluster = alert("instance-1", Map.of("clusterName", "cluster-b"));
        SystemAlertVO sourceWithCluster = alert("instance-1", Map.of("clusterName", "cluster-a"));
        assertThat(AlertCorrelationScope.matches(sourceWithCluster, otherCluster)).isFalse();
    }

    @Test
    void matchingResourceLabelsAllowTheMatch() {
        SystemAlertVO source = alert("instance-1", Map.of("brokerName", "broker-a "));
        SystemAlertVO candidate = alert("instance-1", Map.of("brokerName", " broker-a"));
        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }

    @Test
    void aLabelPresentOnOnlyOneSideDoesNotBlockTheMatch() {
        SystemAlertVO labelled = alert("instance-1", Map.of("brokerName", "broker-a"));
        SystemAlertVO unlabelled = alert("instance-1", Map.of());
        assertThat(AlertCorrelationScope.matches(labelled, unlabelled)).isTrue();
        assertThat(AlertCorrelationScope.matches(unlabelled, labelled)).isTrue();
    }

    @Test
    void labelsOutsideTheResourceSetAreIgnored() {
        SystemAlertVO source = alert("instance-1", Map.of("topic", "topic-a"));
        SystemAlertVO candidate = alert("instance-1", Map.of("topic", "topic-b"));
        assertThat(AlertCorrelationScope.matches(source, candidate)).isTrue();
    }
}
