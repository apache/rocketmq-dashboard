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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MetricCollectionScope}, the record identifying one successful native
 * metric collection scope: it validates its shape, defensively copies the metric-key set, and
 * decides whether a collected sample belongs to the scope.
 */
class MetricCollectionScopeTest {

    private static final Instant COLLECTED_AT = Instant.parse("2026-07-01T10:00:00Z");

    private static MetricSample sample(String metricKey, AlertDomain domain, String instanceId) {
        return new MetricSample(metricKey, domain, instanceId, "cluster-1", Map.of(),
                1.0, MetricAvailability.AVAILABLE, COLLECTED_AT);
    }

    @Test
    void validatesTheScopeShape() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MetricCollectionScope(null, "i1", Set.of("cpu")))
                .withMessage("domain is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricCollectionScope(AlertDomain.BUSINESS, "  ", Set.of("cpu")))
                .withMessage("instanceId is required");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricCollectionScope(AlertDomain.BUSINESS, "i1", Set.of()))
                .withMessage("metricKeys must not be empty");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MetricCollectionScope(AlertDomain.BUSINESS, "i1", Set.of("cpu", " ")))
                .withMessage("metricKeys must not be empty");
    }

    @Test
    void defensivelyCopiesTheMetricKeySet() {
        Set<String> keys = new HashSet<>(Set.of("cpu", "memory"));
        MetricCollectionScope scope = new MetricCollectionScope(AlertDomain.BUSINESS, "i1", keys);

        keys.add("disk");
        assertThat(scope.metricKeys()).containsExactlyInAnyOrder("cpu", "memory");
        assertThatThrownBy(() -> scope.metricKeys().add("disk"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsOnlySamplesFromItsOwnScope() {
        MetricCollectionScope scope = new MetricCollectionScope(
                AlertDomain.BUSINESS, "i1", Set.of("cpu", "memory"));

        assertThat(scope.contains(sample("cpu", AlertDomain.BUSINESS, "i1"))).isTrue();
        assertThat(scope.contains(sample("memory", AlertDomain.BUSINESS, "i1"))).isTrue();
        assertThat(scope.contains(sample("disk", AlertDomain.BUSINESS, "i1"))).isFalse();
        assertThat(scope.contains(sample("cpu", AlertDomain.CLUSTER, "i1"))).isFalse();
        assertThat(scope.contains(sample("cpu", AlertDomain.BUSINESS, "i2"))).isFalse();
        assertThat(scope.contains(null)).isFalse();
    }
}
