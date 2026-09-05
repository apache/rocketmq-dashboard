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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.model.ConsumerDiagnosticReport;
import org.apache.rocketmq.dashboard.model.ConsumerDiagnosticReport.BottleneckQueueInfo;
import org.apache.rocketmq.dashboard.model.ConsumerDiagnosticReport.ClientConsumeStatus;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.QueueStatInfo;
import org.apache.rocketmq.dashboard.model.TopicConsumerInfo;
import org.apache.rocketmq.dashboard.service.ConsumerDiagnosticService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConsumerDiagnosticServiceImpl implements ConsumerDiagnosticService {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerDiagnosticServiceImpl.class);

    @Autowired
    private ConsumerService consumerService;

    @Override
    public ConsumerDiagnosticReport diagnoseConsumerGroup(String groupName, String topicName) {
        ConsumerDiagnosticReport report = new ConsumerDiagnosticReport();
        report.setGroupName(groupName);
        report.setTopicName(topicName);

        try {
            GroupConsumeInfo groupInfo = consumerService.queryGroup(groupName, null);
            if (groupInfo != null) {
                report.setTotalDiff(groupInfo.getDiffTotal());
                report.setTotalTps(groupInfo.getConsumeTps());
            }

            List<TopicConsumerInfo> consumeStatsList;
            if (StringUtils.isNotBlank(topicName)) {
                consumeStatsList = consumerService.queryConsumeStatsList(topicName, groupName);
            } else {
                consumeStatsList = consumerService.queryConsumeStatsListByGroupName(groupName, null);
            }

            long maxQueueDiff = 0;
            long totalCalculatedDiff = 0;
            Map<String, ClientConsumeStatus> clientMap = new HashMap<>();
            List<BottleneckQueueInfo> bottleneckQueues = new ArrayList<>();

            if (CollectionUtils.isNotEmpty(consumeStatsList)) {
                for (TopicConsumerInfo topicConsumerInfo : consumeStatsList) {
                    ConsumeStats consumeStats = topicConsumerInfo.getConsumeStats();
                    if (consumeStats == null || consumeStats.getOffsetTable() == null) {
                        continue;
                    }

                    for (Map.Entry<MessageQueue, OffsetWrapper> entry : consumeStats.getOffsetTable().entrySet()) {
                        MessageQueue mq = entry.getKey();
                        OffsetWrapper offsetWrapper = entry.getValue();
                        if (offsetWrapper == null) {
                            continue;
                        }

                        long diff = offsetWrapper.getBrokerOffset() - offsetWrapper.getConsumerOffset();
                        diff = Math.max(0, diff);
                        totalCalculatedDiff += diff;

                        if (diff > maxQueueDiff) {
                            maxQueueDiff = diff;
                        }

                        String clientAddr = offsetWrapper.getClientAddr() != null ? offsetWrapper.getClientAddr() : "UNKNOWN";
                        ClientConsumeStatus status = clientMap.computeIfAbsent(clientAddr, k -> new ClientConsumeStatus(k, k, 0, 0, 0.0));
                        status.setAssignedQueueCount(status.getAssignedQueueCount() + 1);
                        status.setTotalClientDiff(status.getTotalClientDiff() + diff);

                        if (diff >= 500) {
                            bottleneckQueues.add(new BottleneckQueueInfo(
                                    mq.getBrokerName(),
                                    mq.getQueueId(),
                                    offsetWrapper.getBrokerOffset(),
                                    offsetWrapper.getConsumerOffset(),
                                    diff,
                                    clientAddr
                            ));
                        }
                    }
                }
            }

            if (report.getTotalDiff() == 0 && totalCalculatedDiff > 0) {
                report.setTotalDiff(totalCalculatedDiff);
            }

            // Sort bottleneck queues descending by diff
            bottleneckQueues.sort((a, b) -> Long.compare(b.getDiff(), a.getDiff()));
            report.setBottleneckQueues(bottleneckQueues.stream().limit(20).collect(Collectors.toList()));

            // Analyze skew and client variance
            List<ClientConsumeStatus> clientList = new ArrayList<>(clientMap.values());
            report.setClientStatuses(clientList);

            double avgDiffPerClient = clientList.isEmpty() ? 0 : (double) totalCalculatedDiff / clientList.size();
            double varianceSum = 0;
            for (ClientConsumeStatus status : clientList) {
                double delta = status.getTotalClientDiff() - avgDiffPerClient;
                varianceSum += delta * delta;
            }
            double variance = clientList.isEmpty() ? 0 : Math.sqrt(varianceSum / clientList.size());
            report.setClientSkewVariance(Math.round(variance * 100.0) / 100.0);

            // Determine accumulation skew level
            if (report.getTotalDiff() > 100000 || maxQueueDiff > 20000) {
                report.setAccumulationSkewLevel("CRITICAL");
            } else if (report.getTotalDiff() > 10000 || maxQueueDiff > 2000) {
                report.setAccumulationSkewLevel("MODERATE");
            } else {
                report.setAccumulationSkewLevel("NORMAL");
            }

            // Generate actionable diagnostic suggestions
            List<String> suggestions = new ArrayList<>();
            if ("CRITICAL".equals(report.getAccumulationSkewLevel())) {
                suggestions.add("Critical backlog detected! Consider increasing consumer client instances to accelerate processing.");
            }
            if (variance > 5000 && clientList.size() > 1) {
                suggestions.add("Severe imbalance detected across consumer clients (Variance: " + report.getClientSkewVariance() + "). Check if certain queues are bound to slow thread pools.");
            }
            if (bottleneckQueues.size() > 5) {
                suggestions.add("Found " + bottleneckQueues.size() + " bottleneck queues exceeding threshold diff (500). Inspect slow consumer logic or database lock contention.");
            }
            if (clientList.isEmpty()) {
                suggestions.add("No active consumer connection detected for group [" + groupName + "]. Backlog will continue accumulating.");
            }
            if (suggestions.isEmpty()) {
                suggestions.add("Consumer group status is healthy. No action required.");
            }
            report.setDiagnosticSuggestions(suggestions);

        } catch (Exception e) {
            logger.error("Failed to diagnose consumer group: {}", groupName, e);
            report.setAccumulationSkewLevel("UNKNOWN");
            report.getDiagnosticSuggestions().add("Diagnostic error: " + e.getMessage());
        }

        return report;
    }
}
