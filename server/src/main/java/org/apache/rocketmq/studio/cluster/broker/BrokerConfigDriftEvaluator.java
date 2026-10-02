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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO.ConfigDifferenceVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BrokerConfigDriftEvaluator {

    public static final String POSTURE_SYNCHRONIZED = "SYNCHRONIZED";
    public static final String POSTURE_DRIFT_DETECTED = "DRIFT_DETECTED";
    public static final String POSTURE_CRITICAL_DRIFT = "CRITICAL_DRIFT";

    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";

    public EvaluationResult evaluate(List<ConfigDifferenceVO> differences, int totalComparedFields) {
        if (differences == null || differences.isEmpty()) {
            return new EvaluationResult(
                    100.0,
                    POSTURE_SYNCHRONIZED,
                    List.of("All audited broker configuration properties are completely synchronized across the cluster."));
        }

        boolean hasCritical = false;
        List<String> suggestions = new ArrayList<>();

        for (ConfigDifferenceVO diff : differences) {
            String severity = classifySeverity(diff.getField());
            diff.setSeverity(severity);
            diff.setImpactDescription(describeImpact(diff.getField()));
            diff.setRemediationAdvice("Align property [" + diff.getBrokerProperty() + "] to match designated baseline broker.");

            if (SEVERITY_CRITICAL.equals(severity)) {
                hasCritical = true;
            }
            suggestions.add(String.format("Property [%s] divergence (%s): %s",
                    diff.getField(), severity, diff.getRemediationAdvice()));
        }

        int diffCount = differences.size();
        double rawScore = totalComparedFields > 0
                ? (1.0 - (double) diffCount / totalComparedFields) * 100.0
                : 0.0;
        double consistencyScore = Math.max(0.0, Math.round(rawScore * 10.0) / 10.0);

        String posture = hasCritical ? POSTURE_CRITICAL_DRIFT : POSTURE_DRIFT_DETECTED;

        return new EvaluationResult(consistencyScore, posture, suggestions);
    }

    public String classifySeverity(String field) {
        if ("flushDiskType".equals(field)) {
            return SEVERITY_CRITICAL;
        }
        if ("autoCreateTopicEnable".equals(field)
                || "autoCreateSubscriptionGroup".equals(field)
                || "maxMessageSize".equals(field)
                || "brokerPermission".equals(field)) {
            return SEVERITY_HIGH;
        }
        return SEVERITY_MEDIUM;
    }

    public String describeImpact(String field) {
        if (field == null) {
            return "Property divergence across cluster brokers.";
        }
        return switch (field) {
            case "flushDiskType" -> "Discrepancy in disk flush mode (SYNC vs ASYNC) creates unpredictable replica durability guarantees.";
            case "autoCreateTopicEnable" -> "Uncontrolled automatic topic creation causes topic proliferation and inconsistent partition allocation.";
            case "autoCreateSubscriptionGroup" -> "Inconsistent consumer group auto-creation permits unmanaged consumer subscription registration.";
            case "maxMessageSize" -> "Different maximum message sizes cause unexpected MESSAGE_ILLEGAL or rejected payloads when routing to specific brokers.";
            case "brokerPermission" -> "Different broker permissions (Read/Write vs ReadOnly) cause intermittent producer rejection.";
            case "fileReservedTime" -> "Different file retention durations lead to asymmetric storage reclamation across broker nodes.";
            case "writeQueueNums", "readQueueNums" -> "Divergent default topic queue counts lead to unbalanced partition allocation across brokers.";
            case "deleteWhen" -> "Different scheduled deletion windows cause uneven I/O spikes across brokers.";
            case "msgTraceTopicName" -> "Divergent trace topic names result in fragmented message trace spans.";
            default -> "Property divergence across cluster brokers.";
        };
    }

    public record EvaluationResult(
            double consistencyScore,
            String clusterPosture,
            List<String> operationalSuggestions
    ) {
    }
}
