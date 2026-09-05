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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.admin.TopicStatsTable;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.model.TopicTrafficSkewReport;
import org.apache.rocketmq.dashboard.service.TopicTrafficSkewService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class TopicTrafficSkewServiceImpl implements TopicTrafficSkewService {

    private final Logger log = LoggerFactory.getLogger(TopicTrafficSkewServiceImpl.class);

    @Autowired
    private MQAdminExt mqAdminExt;

    @Override
    public TopicTrafficSkewReport inspectTopicTrafficSkew(String topic) {
        TopicTrafficSkewReport report = new TopicTrafficSkewReport();
        report.setTopic(topic);
        report.setInspectTime(System.currentTimeMillis());

        TopicStatsTable topicStatsTable;
        try {
            topicStatsTable = mqAdminExt.examineTopicStats(topic);
        } catch (Exception e) {
            log.warn("Examine topic stats error for topic={}: {}", topic, e.getMessage());
            report.setSkewLevel("UNKNOWN");
            report.setSuggestion("Failed to retrieve topic stats: " + e.getMessage());
            return report;
        }

        if (topicStatsTable == null || topicStatsTable.getOffsetTable() == null || topicStatsTable.getOffsetTable().isEmpty()) {
            report.setSkewLevel("EMPTY");
            report.setSuggestion("Topic currently has no active queues or message traffic");
            return report;
        }

        List<TopicTrafficSkewReport.QueueSkewDetail> details = new ArrayList<>();
        long totalMsgCount = 0L;

        for (Map.Entry<MessageQueue, org.apache.rocketmq.common.admin.TopicOffset> entry : topicStatsTable.getOffsetTable().entrySet()) {
            MessageQueue mq = entry.getKey();
            org.apache.rocketmq.common.admin.TopicOffset offset = entry.getValue();

            long count = Math.max(0L, offset.getMaxOffset() - offset.getMinOffset());
            totalMsgCount += count;

            TopicTrafficSkewReport.QueueSkewDetail detail = new TopicTrafficSkewReport.QueueSkewDetail();
            detail.setBrokerName(mq.getBrokerName());
            detail.setQueueId(mq.getQueueId());
            detail.setMinOffset(offset.getMinOffset());
            detail.setMaxOffset(offset.getMaxOffset());
            detail.setMessageCount(count);

            details.add(detail);
        }

        int queueCount = details.size();
        report.setTotalQueues(queueCount);
        report.setTotalMessages(totalMsgCount);

        double mean = queueCount > 0 ? (double) totalMsgCount / queueCount : 0.0;
        double varianceSum = 0.0;
        double[] counts = new double[queueCount];

        for (int i = 0; i < queueCount; i++) {
            TopicTrafficSkewReport.QueueSkewDetail detail = details.get(i);
            double ratio = totalMsgCount > 0 ? ((double) detail.getMessageCount() / totalMsgCount) * 100.0 : 0.0;
            detail.setRatioPercent(Math.round(ratio * 100.0) / 100.0);

            // Flag as hotspot if exceeding 2.5x expected average share
            double expectedShare = 100.0 / queueCount;
            if (ratio > expectedShare * 2.5 && detail.getMessageCount() > 1000) {
                detail.setHotspot(true);
            }

            counts[i] = detail.getMessageCount();
            varianceSum += Math.pow(detail.getMessageCount() - mean, 2);
        }

        double stdDev = queueCount > 1 ? Math.sqrt(varianceSum / (queueCount - 1)) : 0.0;
        report.setStandardDeviation(Math.round(stdDev * 100.0) / 100.0);

        double gini = calculateGini(counts);
        report.setGiniCoefficient(Math.round(gini * 1000.0) / 1000.0);

        if (gini >= 0.5) {
            report.setSkewLevel("SEVERE_SKEW");
            report.setSuggestion("Severe traffic skew detected (Gini >= 0.5). Review producer sharding key distribution or hash hashing collision.");
        } else if (gini >= 0.25) {
            report.setSkewLevel("SLIGHT_SKEW");
            report.setSuggestion("Moderate partition traffic skew observed. Monitor consumer lag divergence on hotspot partitions.");
        } else {
            report.setSkewLevel("BALANCED");
            report.setSuggestion("Partition message distribution is healthy and balanced across queues.");
        }

        report.setQueueDetails(details);
        return report;
    }

    private double calculateGini(double[] values) {
        if (values == null || values.length <= 1) {
            return 0.0;
        }
        Arrays.sort(values);
        int n = values.length;
        double cumulativeSum = 0.0;
        double totalSum = 0.0;

        for (int i = 0; i < n; i++) {
            cumulativeSum += (i + 1) * values[i];
            totalSum += values[i];
        }

        if (totalSum == 0.0) {
            return 0.0;
        }

        return (2.0 * cumulativeSum) / (n * totalSum) - (n + 1.0) / n;
    }
}
