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
package org.apache.rocketmq.studio.cluster.metrics;

import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the invariants of {@link MetricCollectionScope}: a scope always names a domain, an instance
 * and at least one non-blank metric key, the key set is defensively copied, and {@code contains}
 * matches a sample only when all three agree.
 */
class MetricCollectionScopeTest {

    private static MetricSample sample(AlertDomain domain, String instanceId, String metricKey) {
        return new MetricSample(metricKey, domain, instanceId, "cluster-1", null, 1.0,
                MetricAvailability.AVAILABLE, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void rejectsAMissingDomain() {
        assertThatThrownBy(() -> new MetricCollectionScope(null, "instance-1", Set.of("heapUsed")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("domain is required");
    }

    @Test
    void rejectsAMissingInstanceId() {
        assertThatThrownBy(() -> new MetricCollectionScope(AlertDomain.CLUSTER, null, Set.of("heapUsed")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("instanceId is required");
        assertThatThrownBy(() -> new MetricCollectionScope(AlertDomain.CLUSTER, "  ", Set.of("heapUsed")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("instanceId is required");
    }

    @Test
    void rejectsAnEmptyKeySet() {
        assertThatThrownBy(() -> new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKeys must not be empty");
        assertThatThrownBy(() -> new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKeys must not be empty");
    }

    @Test
    void rejectsABlankMetricKey() {
        assertThatThrownBy(() -> new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKeys must not be empty");
    }

    @Test
    void copiesTheKeySetDefensively() {
        Set<String> keys = new HashSet<>();
        keys.add("heapUsed");
        MetricCollectionScope scope = new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", keys);
        keys.add("threadsLive");
        assertThat(scope.contains(sample(AlertDomain.CLUSTER, "instance-1", "threadsLive"))).isFalse();
    }

    @Test
    void containsMatchesASampleInsideTheScope() {
        MetricCollectionScope scope = new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", Set.of("heapUsed"));
        assertThat(scope.contains(sample(AlertDomain.CLUSTER, "instance-1", "heapUsed"))).isTrue();
    }

    @Test
    void containsRejectsEverySampleOutsideTheScope() {
        MetricCollectionScope scope = new MetricCollectionScope(AlertDomain.CLUSTER, "instance-1", Set.of("heapUsed"));
        assertThat(scope.contains(null)).isFalse();
        assertThat(scope.contains(sample(AlertDomain.BUSINESS, "instance-1", "heapUsed"))).isFalse();
        assertThat(scope.contains(sample(AlertDomain.CLUSTER, "instance-2", "heapUsed"))).isFalse();
        assertThat(scope.contains(sample(AlertDomain.CLUSTER, "instance-1", "threadsLive"))).isFalse();
    }
}
