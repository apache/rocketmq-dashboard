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

import org.apache.rocketmq.studio.cluster.broker.BrokerConfigAuditReportVO.BrokerPropertyValueVO;
import org.apache.rocketmq.studio.cluster.broker.BrokerConfigAuditReportVO.DriftedPropertySummaryVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class BrokerConfigDriftEngine {

    private static final Map<String, String> PROPERTY_IMPACT_MAP = Map.of(
            "flushDiskType", "Discrepancy in disk flush mode (SYNC vs ASYNC) creates unpredictable durability guarantees across replicas.",
            "autoCreateTopicEnable", "Uncontrolled automatic topic creation causes topic proliferation and inconsistent queue allocation across nodes.",
            "autoCreateSubscriptionGroup", "Inconsistent consumer group auto-creation permits unmanaged consumer subscription registration.",
            "maxMessageSize", "Different maximum message sizes cause unexpected MESSAGE_ILLEGAL or rejected payloads when routing to specific brokers.",
            "brokerPermission", "Different broker permissions (Read/Write vs ReadOnly) cause intermittent producer rejection.",
            "fileReservedTime", "Different file retention durations lead to asymmetric storage reclamation across broker nodes."
    );

    private static final Map<String, String> PROPERTY_SEVERITY_MAP = Map.of(
            "flushDiskType", "CRITICAL",
            "autoCreateTopicEnable", "HIGH",
            "autoCreateSubscriptionGroup", "HIGH",
            "maxMessageSize", "HIGH",
            "brokerPermission", "HIGH",
            "fileReservedTime", "MEDIUM"
    );

    public BrokerConfigAuditReportVO auditClusterConfigDrift(String clusterId, String instanceId,
                                                             Map<String, ClusterConfigVO> brokerConfigs) {
        BrokerConfigAuditReportVO report = BrokerConfigAuditReportVO.builder()
                .clusterId(clusterId)
                .instanceId(instanceId)
                .auditTime(LocalDateTime.now())
                .totalBrokers(brokerConfigs == null ? 0 : brokerConfigs.size())
                .onlineBrokers(brokerConfigs == null ? 0 : brokerConfigs.size())
                .driftedProperties(new ArrayList<>())
                .operationalRecommendations(new ArrayList<>())
                .build();

        if (brokerConfigs == null || brokerConfigs.isEmpty()) {
            report.setClusterConsistencyScore(0.0);
            report.getOperationalRecommendations().add("No reachable broker configurations available for analysis.");
            return report;
        }

        if (brokerConfigs.size() == 1) {
            report.setClusterConsistencyScore(100.0);
            report.setBaselineBrokerAddress(brokerConfigs.keySet().iterator().next());
            report.getOperationalRecommendations().add("Single broker detected in cluster; no inter-broker drift possible.");
            return report;
        }

        // Establish baseline using the first broker's address
        String baselineAddr = brokerConfigs.keySet().iterator().next();
        ClusterConfigVO baselineConfig = brokerConfigs.get(baselineAddr);
        report.setBaselineBrokerAddress(baselineAddr);

        List<String> propertiesToAudit = List.of(
                "flushDiskType", "autoCreateTopicEnable", "autoCreateSubscriptionGroup",
                "maxMessageSize", "brokerPermission", "fileReservedTime"
        );

        int driftedCount = 0;

        for (String property : propertiesToAudit) {
            String baselineVal = extractValue(baselineConfig, property);
            List<BrokerPropertyValueVO> values = new ArrayList<>();
            int deviantCount = 0;

            for (Map.Entry<String, ClusterConfigVO> entry : brokerConfigs.entrySet()) {
                String brokerAddr = entry.getKey();
                String actualVal = extractValue(entry.getValue(), property);
                boolean matches = Objects.equals(baselineVal, actualVal);
                if (!matches) {
                    deviantCount++;
                }
                values.add(BrokerPropertyValueVO.builder()
                        .brokerAddress(brokerAddr)
                        .brokerName(entry.getValue() != null && entry.getValue().getBrokerName() != null
                                ? entry.getValue().getBrokerName() : brokerAddr)
                        .actualValue(actualVal)
                        .matchesBaseline(matches)
                        .build());
            }

            if (deviantCount > 0) {
                driftedCount++;
                String severity = PROPERTY_SEVERITY_MAP.getOrDefault(property, "MEDIUM");
                String impact = PROPERTY_IMPACT_MAP.getOrDefault(property, "Property divergence across brokers.");
                report.getDriftedProperties().add(DriftedPropertySummaryVO.builder()
                        .propertyName(property)
                        .baselineValue(baselineVal)
                        .deviantBrokerCount(deviantCount)
                        .severity(severity)
                        .impactDescription(impact)
                        .brokerValues(values)
                        .build());

                report.getOperationalRecommendations().add(String.format(
                        "Align configuration property [%s]: baseline=%s, %d broker(s) deviant. %s",
                        property, baselineVal, deviantCount, impact));
            }
        }

        report.setDriftedFieldCount(driftedCount);
        double consistency = Math.max(0.0, 100.0 - (driftedCount * 16.6));
        report.setClusterConsistencyScore(Math.round(consistency * 10.0) / 10.0);

        if (driftedCount == 0) {
            report.getOperationalRecommendations().add("All audited properties are completely synchronized across the cluster.");
        }

        return report;
    }

    private String extractValue(ClusterConfigVO config, String property) {
        if (config == null) {
            return null;
        }
        return switch (property) {
            case "flushDiskType" -> config.getFlushDiskType();
            case "autoCreateTopicEnable" -> String.valueOf(config.isAutoCreateTopicEnable());
            case "autoCreateSubscriptionGroup" -> String.valueOf(config.isAutoCreateSubscriptionGroup());
            case "maxMessageSize" -> config.getMaxMessageSize() != null ? String.valueOf(config.getMaxMessageSize()) : null;
            case "brokerPermission" -> config.getBrokerPermission() != null ? String.valueOf(config.getBrokerPermission()) : null;
            case "fileReservedTime" -> config.getFileReservedTime() != null ? String.valueOf(config.getFileReservedTime()) : null;
            default -> null;
        };
    }
}
