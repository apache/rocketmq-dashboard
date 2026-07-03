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
package org.apache.rocketmq.dashboard.service.client;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.remoting.protocol.heartbeat.ConsumeType;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Remoting channel client collector for RIP-1 CLIENT-01 dual-protocol unified view.
 *
 * Collects client instance data via the existing Remoting-based MQAdminExt APIs:
 * - examineProducerConnectionInfo for Producer clients
 * - examineConsumerConnectionInfo for Consumer clients
 *
 * Wraps the previous inline logic from ClientServiceImpl into a dedicated Collector component
 * that can be independently tested and later extended (e.g., adding metrics collection,
 * caching, or gRPC fallback).
 */
@Slf4j
public class RemotingClientCollector {

    private static final String RETRY_TOPIC_PREFIX = "%RETRY%";

    public RemotingClientCollector() {
        // Relies on MQAdminInstance.threadLocalMQAdminExt() populated by MQAdminAspect.
        // The ThreadLocal is set before controller method execution and cleared afterward.
    }

    /**
     * Collect client instances from the Remoting channel.
     *
     * Implementation strategy:
     * 1. Iterate all known topics, call examineProducerConnectionInfo for each → Producer clients
     * 2. For each ConsumerGroup, call examineConsumerConnectionInfo → Consumer clients
     * 3. Apply topic/group filters if provided
     * 4. Normalize all results into ClientInstance model
     *
     * @param topic optional topic filter
     * @param group optional consumer group filter
     * @return list of ClientInstance collected via Remoting channel
     */
    public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) {
        List<ClientInstance> allClients = new ArrayList<>();

        try {
            // Step 1: Collect Producer clients
            allClients.addAll(collectProducers());

            // Step 2: Collect Consumer clients
            allClients.addAll(collectConsumers(group));

            // Step 3: Apply topic filter if present
            if (topic.isPresent()) {
                String targetTopic = topic.get();
                allClients = allClients.stream()
                    .filter(c -> c.getTopics().contains(targetTopic)
                            || (c.getProducerGroup() != null && c.getProducerGroup().equals(targetTopic)))
                    .collect(Collectors.toList());
            }

            long producerCount = allClients.stream()
                .filter(c -> c.getClientType() == ClientInstance.ClientType.PRODUCER)
                .count();
            long consumerCount = allClients.stream()
                .filter(c -> c.getClientType() == ClientInstance.ClientType.PUSH_CONSUMER
                        || c.getClientType() == ClientInstance.ClientType.PULL_CONSUMER
                        || c.getClientType() == ClientInstance.ClientType.SIMPLE_CONSUMER)
                .count();

            log.info("[REMOTING-CLIENT] Collected {} client instances (producers={}, consumers={})",
                allClients.size(), producerCount, consumerCount);

        } catch (Exception e) {
            log.error("[REMOTING-CLIENT] Failed to collect client instances", e);
        }

        return allClients;
    }

    /**
     * Collect Producer-side online clients.
     * Iterates over all known topics and queries ProducerConnection for each.
     * Returns a deduplicated list (same clientId appearing across multiple topics only once).
     */
    private List<ClientInstance> collectProducers() {
        List<ClientInstance> producers = new ArrayList<>();
        Map<String, ClientInstance> seenByClientId = new LinkedHashMap<>();

        try {
            Set<String> topics = new HashSet<>(MQAdminInstance.threadLocalMQAdminExt().fetchTopicListFromNameServer().getTopicList());

            for (String topic : topics) {
                try {
                    ProducerConnection connection = MQAdminInstance.threadLocalMQAdminExt()
                        .examineProducerConnectionInfo(topic);
                    if (connection == null || connection.getConnectionSet() == null) {
                        continue;
                    }

                    for (Connection conn : connection.getConnectionSet()) {
                        if (seenByClientId.containsKey(conn.getClientId())) {
                            // Merge topic info into existing entry
                            ClientInstance existing = seenByClientId.get(conn.getClientId());
                            if (existing.getTopics() != null && !existing.getTopics().contains(topic)) {
                                existing.getTopics().add(topic);
                            }
                            continue;
                        }

                        ClientInstance client = buildProducerClient(conn, topic);
                        seenByClientId.put(conn.getClientId(), client);
                        producers.add(client);
                    }
                } catch (Exception e) {
                    log.debug("[REMOTING-CLIENT] No producer connection info for topic: {}", topic);
                }
            }
        } catch (Exception e) {
            log.warn("[REMOTING-CLIENT] Failed to fetch topic list for producer collection", e);
        }

        return producers;
    }

    /**
     * Build a ClientInstance from ProducerConnection data.
     */
    private ClientInstance buildProducerClient(Connection conn, String topic) {
        ClientInstance client = new ClientInstance();
        client.setClientId(conn.getClientId());
        client.setClientAddress(conn.getClientAddr());
        client.setClientType(ClientInstance.ClientType.PRODUCER);
        client.setProtocolType(ClientInstance.ProtocolType.REMOTING);

        if (conn.getLanguage() != null) {
            client.setLanguage(toDashboardLanguage(conn.getLanguage()));
        }
        client.setSdkVersion(MQVersion.getVersionDesc(conn.getVersion()));
        client.setConnectTime(new Date());
        client.setActive(true);
        client.setTopics(Collections.singletonList(topic));
        client.setProducerGroup(conn.getGroupId());

        return client;
    }

    /**
     * Collect Consumer-side online clients.
     * For each ConsumerGroup (or the specified group), queries ConsumerConnection for connection info.
     */
    private List<ClientInstance> collectConsumers(Optional<String> group) {
        List<ClientInstance> consumers = new ArrayList<>();
        Map<String, ClientInstance> seenByClientId = new LinkedHashMap<>();

        try {
            Set<String> consumerGroups = getConsumerGroups();

            for (String groupName : consumerGroups) {
                // Apply group filter if specified
                if (group.isPresent() && !group.get().equals(groupName)) {
                    continue;
                }

                try {
                    ConsumerConnection connection = MQAdminInstance.threadLocalMQAdminExt()
                        .examineConsumerConnectionInfo(groupName);
                    if (connection == null || connection.getConnectionSet() == null) {
                        continue;
                    }

                    ConsumeType consumeType = connection.getConsumeType();
                    Map<String, org.apache.rocketmq.common.protocol.topic.SubscriptionData> subscriptionTable =
                        connection.getSubscriptionTable();

                    List<String> topics = (subscriptionTable != null)
                        ? new ArrayList<>(subscriptionTable.keySet())
                        : Collections.emptyList();

                    for (Connection conn : connection.getConnectionSet()) {
                        if (seenByClientId.containsKey(conn.getClientId())) {
                            ClientInstance existing = seenByClientId.get(conn.getClientId());
                            // Merge topics
                            if (topics != null && existing.getTopics() != null) {
                                for (String t : topics) {
                                    if (!existing.getTopics().contains(t)) {
                                        existing.getTopics().add(t);
                                    }
                                }
                            }
                            continue;
                        }

                        ClientInstance client = new ClientInstance();
                        client.setClientId(conn.getClientId());
                        client.setClientAddress(conn.getClientAddr());
                        client.setClientType(determineClientType(consumeType));
                        client.setProtocolType(ClientInstance.ProtocolType.REMOTING);

                        if (conn.getLanguage() != null) {
                            client.setLanguage(toDashboardLanguage(conn.getLanguage()));
                        }
                        client.setSdkVersion(MQVersion.getVersionDesc(conn.getVersion()));
                        client.setConnectTime(new Date());
                        client.setActive(true);
                        client.setConsumerGroup(groupName);
                        client.setTopics(topics);

                        seenByClientId.put(conn.getClientId(), client);
                        consumers.add(client);
                    }
                } catch (Exception e) {
                    log.debug("[REMOTING-CLIENT] No consumer connection info for group: {}", groupName);
                }
            }
        } catch (Exception e) {
            log.warn("[REMOTING-CLIENT] Failed to collect consumer clients", e);
        }

        return consumers;
    }

    /**
     * Determine ClientType based on RocketMQ's ConsumeType enum.
     */
    private ClientInstance.ClientType determineClientType(ConsumeType consumeType) {
        if (consumeType == null) {
            return ClientInstance.ClientType.PUSH_CONSUMER;
        }
        switch (consumeType) {
            case PULL_CONSUME:
                return ClientInstance.ClientType.PULL_CONSUMER;
            case PUSH_CONSUME:
                return ClientInstance.ClientType.PUSH_CONSUMER;
            default:
                return ClientInstance.ClientType.PUSH_CONSUMER;
        }
    }

    /**
     * Convert RocketMQ LanguageCode to Dashboard string representation.
     */
    private String toDashboardLanguage(LanguageCode languageCode) {
        if (languageCode == null) {
            return null;
        }
        switch (languageCode) {
            case JAVA:
                return "JAVA";
            case CPP:
                return "CPP";
            case PHP:
                return "PHP";
            case DOTNET:
                return "DOTNET";
            case PYTHON:
                return "PYTHON";
            case RUST:
                return "RUST";
            case GO:
                return "GO";
            default:
                return languageCode.name();
        }
    }

    /**
     * Get all ConsumerGroup names by deriving them from %RETRY% system topics
     * and normalizing their names.
     */
    private Set<String> getConsumerGroups() {
        Set<String> groups = new LinkedHashSet<>();

        try {
            Set<String> topics = new HashSet<>(
                MQAdminInstance.threadLocalMQAdminExt().fetchTopicListFromNameServer().getTopicList());

            for (String topic : topics) {
                if (topic.startsWith(RETRY_TOPIC_PREFIX) && topic.length() > RETRY_TOPIC_PREFIX.length()) {
                    groups.add(topic.substring(RETRY_TOPIC_PREFIX.length()));
                }
            }
        } catch (Exception e) {
            log.warn("[REMOTING-CLIENT] Failed to derive consumer groups from topic list", e);
        }

        return groups;
    }
}
