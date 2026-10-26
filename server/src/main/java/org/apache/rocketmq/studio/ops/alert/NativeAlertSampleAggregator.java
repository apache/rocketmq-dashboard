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
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/** Applies the same stored snapshot window to live evaluations and read-only rule tests. */
final class NativeAlertSampleAggregator {
    private NativeAlertSampleAggregator() {
    }

    static MetricSample aggregate(AlertRuleVO rule, MetricSample sample, MetricSnapshotRepository snapshots) {
        if (sample.availability() != MetricAvailability.AVAILABLE || rule.getWindowSeconds() <= 0) {
            return sample;
        }
        List<MetricSample> window = snapshots.findRecent(sample,
                sample.collectedAt().minus(Duration.ofSeconds(rule.getWindowSeconds())));
        if (window.isEmpty()) {
            return sample;
        }
        double value = switch (rule.getAggregation() == null ? "LAST" : rule.getAggregation().toUpperCase(Locale.ROOT)) {
            case "MAX" -> window.stream().mapToDouble(item -> item.value()).max().orElse(sample.value());
            case "MIN" -> window.stream().mapToDouble(item -> item.value()).min().orElse(sample.value());
            case "AVG" -> window.stream().mapToDouble(item -> item.value()).average().orElse(sample.value());
            case "SUM" -> window.stream().mapToDouble(item -> item.value()).sum();
            default -> window.get(window.size() - 1).value();
        };
        return new MetricSample(sample.metricKey(), sample.domain(), sample.instanceId(), sample.clusterId(),
                sample.labels(), value, MetricAvailability.AVAILABLE, sample.collectedAt());
    }
}
