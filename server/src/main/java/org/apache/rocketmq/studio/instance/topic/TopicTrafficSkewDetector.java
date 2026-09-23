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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.instance.topic.TopicTrafficSkewReportVO.QueueTrafficDistributionVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class TopicTrafficSkewDetector {

    public static final double GINI_MODERATE_THRESHOLD = 0.40;
    public static final double GINI_SEVERE_THRESHOLD = 0.70;
    public static final double HOTSPOT_TRAFFIC_SHARE_RATIO = 2.5; // >2.5x expected average share

    public TopicTrafficSkewReportVO detectSkew(String topic, List<TopicQueueStatsVO> statsList) {
        TopicTrafficSkewReportVO report = TopicTrafficSkewReportVO.builder()
                .topic(topic)
                .skewSeverity("NORMAL")
                .queueDistributions(new ArrayList<>())
                .diagnosticAlerts(new ArrayList<>())
                .build();

        if (statsList == null || statsList.isEmpty()) {
            report.getDiagnosticAlerts().add("No queue offset statistics available for topic: " + topic);
            return report;
        }

        int n = statsList.size();
        report.setTotalQueues(n);

        long totalVolume = 0L;
        long[] volumes = new long[n];
        for (int i = 0; i < n; i++) {
            TopicQueueStatsVO stat = statsList.get(i);
            long min = stat.getMinOffset() == null ? 0L : stat.getMinOffset();
            long max = stat.getMaxOffset() == null ? 0L : stat.getMaxOffset();
            long vol = Math.max(0, max - min);
            volumes[i] = vol;
            totalVolume += vol;
        }
        report.setTotalMessagesAcrossQueues(totalVolume);

        if (n == 1) {
            report.setGiniCoefficient(0.0);
            report.setStandardDeviation(0.0);
            report.setSkewDetected(false);
            report.getDiagnosticAlerts().add("Single queue topic; no inter-queue skew possible.");
            report.getQueueDistributions().add(QueueTrafficDistributionVO.builder()
                    .brokerName(statsList.get(0).getBrokerName())
                    .queueId(statsList.get(0).getQueueId())
                    .minOffset(statsList.get(0).getMinOffset())
                    .maxOffset(statsList.get(0).getMaxOffset())
                    .messageVolume(volumes[0])
                    .trafficSharePercent(100.0)
                    .isHotspot(false)
                    .build());
            return report;
        }

        // Calculate standard deviation and Gini coefficient
        double mean = (double) totalVolume / n;
        double varianceSum = 0.0;
        for (long v : volumes) {
            varianceSum += Math.pow(v - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / n);
        report.setStandardDeviation(Math.round(stdDev * 10.0) / 10.0);

        double gini = calculateGini(volumes, totalVolume);
        report.setGiniCoefficient(Math.round(gini * 1000.0) / 1000.0);

        boolean skew = gini >= GINI_MODERATE_THRESHOLD;
        report.setSkewDetected(skew);

        String severity = "NORMAL";
        if (gini >= GINI_SEVERE_THRESHOLD) {
            severity = "SEVERE_HOTSPOT";
        } else if (gini >= GINI_MODERATE_THRESHOLD) {
            severity = "MODERATE_SKEW";
        }
        report.setSkewSeverity(severity);

        double expectedShare = 100.0 / n;
        double hotspotThreshold = expectedShare * HOTSPOT_TRAFFIC_SHARE_RATIO;
        int hotspotCount = 0;

        for (int i = 0; i < n; i++) {
            TopicQueueStatsVO stat = statsList.get(i);
            long vol = volumes[i];
            double share = totalVolume > 0 ? (double) vol / totalVolume * 100.0 : expectedShare;
            boolean isHot = share >= hotspotThreshold && vol > 100;
            if (isHot) {
                hotspotCount++;
            }

            report.getQueueDistributions().add(QueueTrafficDistributionVO.builder()
                    .brokerName(stat.getBrokerName())
                    .queueId(stat.getQueueId())
                    .minOffset(stat.getMinOffset() == null ? 0L : stat.getMinOffset())
                    .maxOffset(stat.getMaxOffset() == null ? 0L : stat.getMaxOffset())
                    .messageVolume(vol)
                    .trafficSharePercent(Math.round(share * 10.0) / 10.0)
                    .isHotspot(isHot)
                    .build());
        }

        if (gini >= GINI_SEVERE_THRESHOLD) {
            report.getDiagnosticAlerts().add(String.format(
                    "Severe traffic hotspot detected (Gini=%.3f, Hotspots=%d). Producers are directing disproportionate traffic to specific partitions.",
                    gini, hotspotCount));
            report.getDiagnosticAlerts().add("Check MessageQueueSelector hash keys for low entropy or monopolized business entity IDs.");
            report.getDiagnosticAlerts().add("Review producer round-robin vs select-queue routing strategies to rebalance queue ingress.");
        } else if (gini >= GINI_MODERATE_THRESHOLD) {
            report.getDiagnosticAlerts().add(String.format(
                    "Moderate traffic skew detected (Gini=%.3f). Monitor queue consumption rates to prevent consumer lag.",
                    gini));
            report.getDiagnosticAlerts().add("Consider adding salt or random suffixes to partition keys to spread traffic more uniformly.");
        } else {
            report.getDiagnosticAlerts().add("Traffic is evenly balanced across all topic queues.");
        }

        return report;
    }

    private double calculateGini(long[] volumes, long totalVolume) {
        if (totalVolume <= 0) {
            return 0.0;
        }
        long[] sorted = Arrays.copyOf(volumes, volumes.length);
        Arrays.sort(sorted);
        int n = sorted.length;
        double numerator = 0.0;
        for (int i = 0; i < n; i++) {
            numerator += (2.0 * (i + 1) - n - 1) * sorted[i];
        }
        return numerator / ((double) n * totalVolume);
    }
}
