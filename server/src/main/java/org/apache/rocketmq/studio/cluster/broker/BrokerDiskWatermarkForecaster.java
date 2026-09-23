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

import org.apache.rocketmq.studio.cluster.broker.BrokerDiskForecasterReportVO.BrokerDiskAssessmentVO;
import org.apache.rocketmq.studio.cluster.broker.BrokerDiskForecasterReportVO.CapacityPlanActionVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class BrokerDiskWatermarkForecaster {

    public static final double WATERMARK_CLEAN = 0.75;
    public static final double WATERMARK_WARN = 0.85;
    public static final double WATERMARK_DANGEROUS = 0.90;
    public static final double WATERMARK_SHUTDOWN = 0.95;

    public BrokerDiskForecasterReportVO forecast(String clusterId, List<BrokerVO> brokers) {
        BrokerDiskForecasterReportVO report = BrokerDiskForecasterReportVO.builder()
                .clusterId(clusterId)
                .forecastTimestamp(LocalDateTime.now())
                .totalBrokers(brokers == null ? 0 : brokers.size())
                .brokerAssessments(new ArrayList<>())
                .operationalAlerts(new ArrayList<>())
                .capacityPlanActions(new ArrayList<>())
                .build();

        if (brokers == null || brokers.isEmpty()) {
            report.getOperationalAlerts().add("No broker instances available in cluster: " + clusterId);
            return report;
        }

        int criticalCount = 0;
        boolean anyBlocked = false;

        for (BrokerVO broker : brokers) {
            double usage = broker.getDiskUsage();
            // diskUsage in RocketMQ can be returned as percentage (0..100) or ratio (0..1)
            double ratio = usage > 1.0 ? usage / 100.0 : usage;
            ratio = Math.max(0.0, Math.min(1.0, ratio));

            String level;
            boolean blocked = false;
            String recommendation;
            long hoursToShutdown = -1L;

            if (ratio >= WATERMARK_SHUTDOWN) {
                level = "SHUTDOWN";
                blocked = true;
                criticalCount++;
                hoursToShutdown = 0L;
                recommendation = "CRITICAL EMERGENCY: Physical disk usage >= 95%. CommitLog writes will be refused! Free up disk space immediately.";
                report.getCapacityPlanActions().add(CapacityPlanActionVO.builder()
                        .brokerName(broker.getName())
                        .targetAction("EXPAND_STORAGE_VOLUME")
                        .urgency("IMMEDIATE")
                        .actionDetail("Emergency: storage capacity exhausted. Mount additional volume or delete non-essential logs.")
                        .build());
            } else if (ratio >= WATERMARK_DANGEROUS) {
                level = "DANGEROUS";
                blocked = true;
                criticalCount++;
                hoursToShutdown = 2L;
                recommendation = "DANGER: Disk usage >= 90%. Flow control is actively rejecting producer writes. Delete archived logs or expand disk.";
                report.getCapacityPlanActions().add(CapacityPlanActionVO.builder()
                        .brokerName(broker.getName())
                        .targetAction("PURGE_EXPIRED_COMMITLOG")
                        .urgency("WITHIN_24_HOURS")
                        .actionDetail("Invoke manual cleanExpiredFiles on broker to reclaim expired commitLog segments.")
                        .build());
            } else if (ratio >= WATERMARK_WARN) {
                level = "WARN";
                hoursToShutdown = 12L;
                recommendation = "WARNING: Disk usage >= 85%. Force clean of expired commitLogs will be triggered. Monitor disk growth closely.";
                report.getCapacityPlanActions().add(CapacityPlanActionVO.builder()
                        .brokerName(broker.getName())
                        .targetAction("TIGHTEN_RETENTION")
                        .urgency("ROUTINE")
                        .actionDetail("Review fileReservedTime and schedule disk volume expansion.")
                        .build());
            } else if (ratio >= WATERMARK_CLEAN) {
                level = "CLEAN_RESOURCE";
                hoursToShutdown = 48L;
                recommendation = "ELEVATED: Disk usage >= 75%. Regular commitLog cleanResource execution in progress.";
            } else {
                level = "NORMAL";
                hoursToShutdown = 168L; // 7+ days
                recommendation = "Disk watermark is healthy. Storage utilization within normal operating margins.";
            }

            if (blocked) {
                anyBlocked = true;
            }

            long dailyGrowth = Math.max(0, broker.getPutMessagesToday() - broker.getPutMessagesYesterday());
            long tps = broker.getTpsIn();

            // Refine hours to shutdown if daily message growth and current storage delta are positive
            if (hoursToShutdown > 0 && dailyGrowth > 100_000L && tps > 50L) {
                double remainingRatio = Math.max(0.01, WATERMARK_SHUTDOWN - ratio);
                long projectedHours = Math.max(1L, Math.round(remainingRatio * 100.0 / (tps * 0.05)));
                hoursToShutdown = Math.min(hoursToShutdown, projectedHours);
            }

            report.getBrokerAssessments().add(BrokerDiskAssessmentVO.builder()
                    .brokerName(broker.getName())
                    .brokerAddress(broker.getAddr())
                    .currentDiskUsageRatio(Math.round(ratio * 1000.0) / 1000.0)
                    .watermarkLevel(level)
                    .writeBlocked(blocked)
                    .estimatedHoursToShutdown(hoursToShutdown)
                    .dailyMessageIngestGrowth(dailyGrowth)
                    .recommendation(recommendation)
                    .build());
        }

        report.setCriticalWatermarkBrokerCount(criticalCount);
        report.setAnyBrokerBlocked(anyBlocked);

        if (anyBlocked) {
            report.getOperationalAlerts().add(String.format(
                    "BLOCKING ALERT: %d broker(s) have reached DANGEROUS/SHUTDOWN watermarks (>=90%% disk ratio). Producer message writes are impacted!",
                    criticalCount));
            report.getOperationalAlerts().add("Immediately trigger commitLog clean execution or expand volume mount size.");
        } else if (criticalCount > 0) {
            report.getOperationalAlerts().add(String.format(
                    "CAPACITY WARNING: %d broker(s) have elevated disk watermarks. Prepare disk volume expansion.",
                    criticalCount));
            report.getOperationalAlerts().add("Check fileReservedTime configuration to ensure old files can be purged gracefully.");
        } else {
            report.getOperationalAlerts().add("All broker disk watermarks are currently within normal operating safety limits.");
        }

        return report;
    }
}
