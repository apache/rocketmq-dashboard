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
import java.util.Map;

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
    void rejectsMissingOrBlankCoreFieldsTest() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new MetricSample("", AlertDomain.CLUSTER, "local", null, null,
                1D, MetricAvailability.AVAILABLE, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKey is required");
        assertThatThrownBy(() -> new MetricSample(null, AlertDomain.CLUSTER, "local", null, null,
                1D, MetricAvailability.AVAILABLE, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("metricKey is required");
        assertThatThrownBy(() -> new MetricSample("cpu", null, "local", null, null,
                1D, MetricAvailability.AVAILABLE, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("domain is required");
        assertThatThrownBy(() -> new MetricSample("cpu", AlertDomain.CLUSTER, "  ", null, null,
                1D, MetricAvailability.AVAILABLE, now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("instanceId is required");
        assertThatThrownBy(() -> new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, null,
                1D, null, now))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("availability is required");
        assertThatThrownBy(() -> new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, null,
                1D, MetricAvailability.AVAILABLE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("collectedAt is required");
    }

    @Test
    void availableSamplesCannotCarryAnUnavailableReasonTest() {
        assertThatThrownBy(() -> new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, null,
                1D, MetricAvailability.AVAILABLE, Instant.now(), "not-available"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Available metric samples cannot have an unavailable reason");
    }

    @Test
    void copiesLabelsDefensivelyAndDefaultsNullToEmptyTest() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("broker", "b1");
        MetricSample sample = new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, mutable,
                1D, MetricAvailability.AVAILABLE, Instant.now());

        mutable.put("extra", "e1");
        assertThat(sample.labels()).containsExactlyEntriesOf(Map.of("broker", "b1"));
        assertThatThrownBy(() -> sample.labels().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);

        MetricSample nullLabels = new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, null,
                1D, MetricAvailability.AVAILABLE, Instant.now());
        assertThat(nullLabels.labels()).isEmpty();
    }

    @Test
    void carriesTheUnavailableReasonForUnavailableSamplesTest() {
        MetricSample sample = new MetricSample("cpu", AlertDomain.CLUSTER, "local", null, null,
                null, MetricAvailability.UNAVAILABLE, Instant.now(), "broker unreachable");
        assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
        assertThat(sample.value()).isNull();
        assertThat(sample.unavailableReason()).isEqualTo("broker unreachable");

        MetricSample viaConvenienceCtor = new MetricSample("cpu", AlertDomain.CLUSTER, "local", null,
                null, null, MetricAvailability.UNAVAILABLE, Instant.now());
        assertThat(viaConvenienceCtor.unavailableReason()).isNull();
    }
}
