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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Evaluates the existing structured rule operators against one native metric sample. */
@Component
public class AlertRuleEvaluator {
    public AlertEvaluationResult evaluate(AlertRuleVO rule, MetricSample sample) {
        if (rule == null || sample == null
                || ruleDomain(rule) != sample.domain()
                || !StringUtils.hasText(rule.getMetric())
                || !rule.getMetric().trim().equals(sample.metricKey())) {
            return new AlertEvaluationResult(false, false, null, sample == null ? null : sample.availability());
        }
        if (sample.availability() != MetricAvailability.AVAILABLE) {
            boolean unavailableCondition = sample.availability() == MetricAvailability.UNAVAILABLE
                    && "UNAVAILABLE".equals(rule.getOperator());
            return new AlertEvaluationResult(true, unavailableCondition, null, sample.availability());
        }
        double value = sample.value();
        return new AlertEvaluationResult(true, compare(value, rule.getOperator(),
                AlertRuleSemanticFingerprint.normalizedThreshold(rule)), value,
                sample.availability());
    }

    private static AlertDomain ruleDomain(AlertRuleVO rule) {
        return rule.getDomain() == null ? AlertDomain.BUSINESS : rule.getDomain();
    }

    private static boolean compare(double value, String operator, double threshold) {
        if (operator == null) {
            return false;
        }
        return switch (operator.trim()) {
            case ">" -> value > threshold;
            case ">=" -> value >= threshold;
            case "<" -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            case "!=" -> Double.compare(value, threshold) != 0;
            default -> false;
        };
    }

}
