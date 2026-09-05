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
import org.apache.rocketmq.common.protocol.body.Connection;
import org.apache.rocketmq.common.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.dashboard.model.ConsumerSubscriptionAuditReport;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.ConsumerSubscriptionAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConsumerSubscriptionAuditServiceImpl implements ConsumerSubscriptionAuditService {

    private final Logger log = LoggerFactory.getLogger(ConsumerSubscriptionAuditServiceImpl.class);

    @Autowired
    private ConsumerService consumerService;

    @Override
    public ConsumerSubscriptionAuditReport auditSubscriptionConsistency(String consumerGroup) {
        ConsumerSubscriptionAuditReport report = new ConsumerSubscriptionAuditReport();
        report.setConsumerGroup(consumerGroup);
        report.setAuditTime(System.currentTimeMillis());

        ConsumerConnection connection;
        try {
            connection = consumerService.getConsumerConnection(consumerGroup);
        } catch (Exception e) {
            log.warn("Failed to retrieve consumer connection for {}: {}", consumerGroup, e.getMessage());
            report.setConsistent(false);
            report.setAuditStatus("ERROR");
            report.setRecommendation("Failed to query consumer group connection: " + e.getMessage());
            return report;
        }

        if (connection == null || CollectionUtils.isEmpty(connection.getConnectionSet())) {
            report.setConsistent(true);
            report.setTotalClients(0);
            report.setAuditStatus("NO_CLIENT_ONLINE");
            report.setRecommendation("No active client instances currently online for this group.");
            return report;
        }

        Set<Connection> clientSet = connection.getConnectionSet();
        report.setTotalClients(clientSet.size());

        List<ConsumerSubscriptionAuditReport.ClientSubscriptionSummary> clientSummaries = new ArrayList<>();
        Map<String, Map<String, String>> topicClientSubMap = new HashMap<>(); // topic -> (clientId -> subString)
        Set<String> allAuditedTopics = new HashSet<>();

        for (Connection conn : clientSet) {
            ConsumerSubscriptionAuditReport.ClientSubscriptionSummary clientSummary =
                    new ConsumerSubscriptionAuditReport.ClientSubscriptionSummary();
            clientSummary.setClientId(conn.getClientId());
            clientSummary.setClientAddr(conn.getClientAddr());
            clientSummary.setLanguage(conn.getLanguage() != null ? conn.getLanguage().name() : "JAVA");
            clientSummary.setVersion(String.valueOf(conn.getVersion()));

            List<String> subTopics = new ArrayList<>();
            if (connection.getSubscriptionTable() != null) {
                for (Map.Entry<String, SubscriptionData> entry : connection.getSubscriptionTable().entrySet()) {
                    String topic = entry.getKey();
                    SubscriptionData subData = entry.getValue();
                    subTopics.add(topic);
                    allAuditedTopics.add(topic);

                    topicClientSubMap.computeIfAbsent(topic, k -> new HashMap<>())
                            .put(conn.getClientId(), subData != null ? subData.getSubString() : "*");
                }
            }
            clientSummary.setSubscribedTopics(subTopics);
            clientSummaries.add(clientSummary);
        }
        report.setClientSummaries(clientSummaries);

        List<ConsumerSubscriptionAuditReport.SubscriptionConflictItem> conflictItems = new ArrayList<>();

        for (String topic : allAuditedTopics) {
            Map<String, String> clientSubs = topicClientSubMap.get(topic);
            if (clientSubs == null) {
                continue;
            }

            if (clientSubs.size() < clientSet.size()) {
                ConsumerSubscriptionAuditReport.SubscriptionConflictItem item =
                        new ConsumerSubscriptionAuditReport.SubscriptionConflictItem();
                item.setTopic(topic);
                item.setConflictType("TOPIC_MISSING");
                item.setDescription("Topic [" + topic + "] is subscribed by only " + clientSubs.size()
                        + " of " + clientSet.size() + " active clients. May cause message loss.");
                item.setClientExpressions(new HashMap<>(clientSubs));
                conflictItems.add(item);
            } else {
                Set<String> distinctExpressions = new HashSet<>(clientSubs.values());
                if (distinctExpressions.size() > 1) {
                    ConsumerSubscriptionAuditReport.SubscriptionConflictItem item =
                            new ConsumerSubscriptionAuditReport.SubscriptionConflictItem();
                    item.setTopic(topic);
                    item.setConflictType("SUB_EXPRESSION_MISMATCH");
                    item.setDescription("Conflict in filter expressions for topic [" + topic
                            + "] across clients: " + distinctExpressions);
                    item.setClientExpressions(new HashMap<>(clientSubs));
                    conflictItems.add(item);
                }
            }
        }

        report.setConflictItems(conflictItems);
        report.setConflictItemCount(conflictItems.size());

        if (conflictItems.isEmpty()) {
            report.setConsistent(true);
            report.setAuditStatus("CONSISTENT");
            report.setRecommendation("All client instances within group [" + consumerGroup
                    + "] share completely homogeneous topic and expression subscriptions.");
        } else {
            report.setConsistent(false);
            report.setAuditStatus("INCONSISTENT_SUBSCRIPTIONS");
            report.setRecommendation("Discrepancies found across group instances. Align @RocketMQMessageListener topics & tags before deployment.");
        }

        return report;
    }
}
