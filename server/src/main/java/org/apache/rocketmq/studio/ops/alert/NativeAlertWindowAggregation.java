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

import java.util.List;
import java.util.Locale;

/**
 * Applies a rule's window aggregation to a sample from the samples stored for the same series.
 *
 * <p>The live evaluation and a rule test run have to agree on what a rule evaluates: the rule editor
 * offers a window aggregation together with a window length, and the console's test button answers
 * "would this rule fire?". A preview over the raw sample would report on a condition the evaluator
 * never applies.
 * Both call sites therefore share this one definition.
 */
final class NativeAlertWindowAggregation {

    private NativeAlertWindowAggregation() {
    }

    /**
     * Returns the sample as the evaluator should see it: aggregated over {@code window} when the rule
     * asks for a window and the sample is available, otherwise the sample itself.
     */
    static MetricSample aggregate(AlertRuleVO rule, MetricSample sample, List<MetricSample> window) {
        if (sample.availability() != MetricAvailability.AVAILABLE || rule.getWindowSeconds() <= 0
                || window.isEmpty()) {
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
