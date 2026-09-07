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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * A normalized native metric collected by Studio. A non-available sample intentionally has no
 * numeric value so callers cannot mistake a failed collection for a healthy zero.
 */
public record MetricSample(
        String metricKey,
        AlertDomain domain,
        String instanceId,
        String clusterId,
        Map<String, String> labels,
        Double value,
        MetricAvailability availability,
        Instant collectedAt,
        String unavailableReason) {

    public MetricSample(String metricKey, AlertDomain domain, String instanceId, String clusterId,
            Map<String, String> labels, Double value, MetricAvailability availability, Instant collectedAt) {
        this(metricKey, domain, instanceId, clusterId, labels, value, availability, collectedAt, null);
    }

    public MetricSample {
        requireText(metricKey, "metricKey");
        Objects.requireNonNull(domain, "domain is required");
        requireText(instanceId, "instanceId");
        labels = labels == null ? Map.of() : Map.copyOf(labels);
        Objects.requireNonNull(availability, "availability is required");
        Objects.requireNonNull(collectedAt, "collectedAt is required");
        if (availability != MetricAvailability.AVAILABLE && value != null) {
            throw new IllegalArgumentException("Only available metric samples may have a value");
        }
        if (availability == MetricAvailability.AVAILABLE && value == null) {
            throw new IllegalArgumentException("Available metric samples require a value");
        }
        if (availability == MetricAvailability.AVAILABLE && unavailableReason != null) {
            throw new IllegalArgumentException("Available metric samples cannot have an unavailable reason");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
