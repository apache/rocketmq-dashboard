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
package org.apache.rocketmq.studio.instance.group;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.studio.instance.group.SubscriptionConsistencyReportVO.ClientSubscriptionDetailVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionConsistencyReportVO.TopicSubscriptionMismatchVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class ConsumerSubscriptionConsistencyValidator {

    /**
     * Inspects subscription tables across all client instances of a consumer group.
     * In RocketMQ, all consumer clients within the same consumer group MUST subscribe to identical
     * topics and identical tag/SQL92 filter expressions. Inconsistent subscriptions lead to message loss,
     * rebalance flapping, and erratic message delivery.
     */
    public SubscriptionConsistencyReportVO validate(String groupName,
                                                    Map<String, ConsumerRunningInfo> clientRunningInfos) {
        SubscriptionConsistencyReportVO report = SubscriptionConsistencyReportVO.builder()
                .groupName(groupName)
                .totalClientsChecked(clientRunningInfos == null ? 0 : clientRunningInfos.size())
                .consistent(true)
                .mismatches(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();

        if (clientRunningInfos == null || clientRunningInfos.isEmpty()) {
            report.getWarnings().add("No consumer client instances available for group: " + groupName);
            return report;
        }

        if (clientRunningInfos.size() == 1) {
            report.setConsistent(true);
            report.getWarnings().add("Single client instance in consumer group; subscription is inherently consistent.");
            return report;
        }

        // Collect all subscribed topics across all clients
        Set<String> allTopics = new TreeSet<>();
        Map<String, Map<String, SubscriptionData>> clientTopicMap = new HashMap<>();

        for (Map.Entry<String, ConsumerRunningInfo> entry : clientRunningInfos.entrySet()) {
            String clientId = entry.getKey();
            ConsumerRunningInfo info = entry.getValue();
            Map<String, SubscriptionData> subMap = new HashMap<>();

            if (info != null && info.getSubscriptionTable() != null) {
                for (SubscriptionData subData : info.getSubscriptionTable()) {
                    String topic = subData.getTopic();
                    if (StringUtils.isNotBlank(topic) && !topic.startsWith("%RETRY%") && !topic.startsWith("%DLQ%")) {
                        allTopics.add(topic);
                        subMap.put(topic, subData);
                    }
                }
            }
            clientTopicMap.put(clientId, subMap);
        }

        int mismatchCount = 0;

        for (String topic : allTopics) {
            List<ClientSubscriptionDetailVO> details = new ArrayList<>();
            Set<String> distinctSubStrings = new HashSet<>();
            Set<String> distinctExpTypes = new HashSet<>();
            int clientSubscribedCount = 0;

            for (Map.Entry<String, Map<String, SubscriptionData>> entry : clientTopicMap.entrySet()) {
                String clientId = entry.getKey();
                SubscriptionData subData = entry.getValue().get(topic);

                if (subData != null) {
                    clientSubscribedCount++;
                    String subStr = subData.getSubString() == null ? "" : subData.getSubString().trim();
                    String expType = subData.getExpressionType() == null ? "TAG" : subData.getExpressionType().trim();
                    distinctSubStrings.add(subStr);
                    distinctExpTypes.add(expType);

                    List<String> tags = subData.getTagsSet() != null
                            ? new ArrayList<>(subData.getTagsSet()) : Collections.emptyList();

                    details.add(ClientSubscriptionDetailVO.builder()
                            .clientId(clientId)
                            .subString(subStr)
                            .expressionType(expType)
                            .tags(tags)
                            .build());
                } else {
                    details.add(ClientSubscriptionDetailVO.builder()
                            .clientId(clientId)
                            .subString("<NOT_SUBSCRIBED>")
                            .expressionType("<NONE>")
                            .tags(Collections.emptyList())
                            .build());
                }
            }

            // Check 1: Partial subscription (some clients subscribe to topic, others don't)
            if (clientSubscribedCount < clientRunningInfos.size()) {
                mismatchCount++;
                report.getMismatches().add(TopicSubscriptionMismatchVO.builder()
                        .topic(topic)
                        .issueType("PARTIAL_SUBSCRIPTION")
                        .severity("CRITICAL")
                        .description(String.format("Topic [%s] is subscribed by only %d of %d consumer clients.",
                                topic, clientSubscribedCount, clientRunningInfos.size()))
                        .clientDetails(details)
                        .build());
                continue;
            }

            // Check 2: Expression type mismatch (e.g. TAG vs SQL92)
            if (distinctExpTypes.size() > 1) {
                mismatchCount++;
                report.getMismatches().add(TopicSubscriptionMismatchVO.builder()
                        .topic(topic)
                        .issueType("FILTER_TYPE_MISMATCH")
                        .severity("CRITICAL")
                        .description(String.format("Divergent filter expression types detected for topic [%s]: %s",
                                topic, distinctExpTypes))
                        .clientDetails(details)
                        .build());
                continue;
            }

            // Check 3: Expression / Tag set mismatch
            if (distinctSubStrings.size() > 1) {
                mismatchCount++;
                report.getMismatches().add(TopicSubscriptionMismatchVO.builder()
                        .topic(topic)
                        .issueType("EXPRESSION_MISMATCH")
                        .severity("HIGH")
                        .description(String.format("Divergent subscription expressions detected for topic [%s]: %s",
                                topic, distinctSubStrings))
                        .clientDetails(details)
                        .build());
            }
        }

        report.setInconsistentTopicCount(mismatchCount);
        report.setConsistent(mismatchCount == 0);

        if (mismatchCount > 0) {
            report.getWarnings().add(String.format(
                    "CRITICAL: Found %d topic(s) with inconsistent subscription configurations across group clients. " +
                            "This violates RocketMQ design invariants and causes message loss or erratic consumption.",
                    mismatchCount));
        } else {
            report.getWarnings().add("All consumer clients have 100% identical subscription topics and filter expressions.");
        }

        return report;
    }
}
