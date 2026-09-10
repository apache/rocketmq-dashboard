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
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricSampleTest {

    @Test
    void unavailableSamplesCannotCarryAValueTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 0D, MetricAvailability.UNAVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only available metric samples may have a value");
    }

    @Test
    void availableSamplesRequireAValueTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, null, MetricAvailability.AVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Available metric samples require a value");
    }

    @Test
    void blankMetricKeyAndInstanceIdAreRejectedTest() {
        assertThatThrownBy(() -> new MetricSample("  ", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKey is required");
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, null, null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("instanceId is required");
    }

    @Test
    void nullDomainAvailabilityAndClockAreRejectedTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", null, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLabelsAreNormalizedToAnEmptyMapTest() {
        MetricSample sample = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());

        assertThat(sample.labels()).isEmpty();
    }

    @Test
    void labelsAreCopiedDefensivelyTest() {
        HashMap<String, String> labels = new HashMap<>();
        labels.put("brokerName", "broker-a");
        MetricSample sample = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                labels, 1D, MetricAvailability.AVAILABLE, Instant.now());

        labels.put("brokerName", "broker-b");
        labels.put("extra", "late");

        assertThat(sample.labels())
                .containsExactly(java.util.Map.entry("brokerName", "broker-a"));
    }

    @Test
    void availableSampleCannotCarryAnUnavailableReasonTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now(), "probe failed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Available metric samples cannot have an unavailable reason");
    }

    @Test
    void unavailableSampleWithoutAValueConstructsWithOptionalReasonTest() {
        MetricSample plain = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, null, MetricAvailability.UNAVAILABLE, Instant.now());
        MetricSample withReason = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, null, MetricAvailability.UNAVAILABLE, Instant.now(), "probe failed");

        assertThat(plain.unavailableReason()).isNull();
        assertThat(withReason.unavailableReason()).isEqualTo("probe failed");
    }
}
