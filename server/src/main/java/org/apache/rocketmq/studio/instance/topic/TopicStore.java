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

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory topic metadata store. It is the single source of truth shared by
 * {@link CloudMetadataProvider} (read paths) and {@link NameSrvAdminClient}
 * (write paths) so that topic create / list / delete stay consistent.
 *
 * <p>In a cloud deployment this would be replaced by calls to the cloud control
 * plane; the API surface here mirrors what the cloud metadata provider exposes.
 */
@Slf4j
@Component
public class TopicStore {

    private final Map<String, TopicVO> topics = new ConcurrentHashMap<>();
    private final Map<String, List<BrokerRouteVO>> routes = new ConcurrentHashMap<>();

    public TopicStore() {
        seed();
    }

    // ── Read paths ─────────────────────────────────────────────────

    public List<TopicVO> listTopics(String clusterId, String type, String search) {
        return topics.values().stream()
                .filter(topic -> clusterId == null || clusterId.equals(topic.getClusterId()))
                .filter(topic -> type == null || type.equalsIgnoreCase(String.valueOf(topic.getType())))
                .filter(topic -> search == null
                        || topic.getName().toLowerCase().contains(search.toLowerCase()))
                .sorted(Comparator.comparing(TopicVO::getName))
                .collect(Collectors.toList());
    }

    public List<TopicVO> fetchAllTopicList() {
        return topics.values().stream()
                .sorted(Comparator.comparing(TopicVO::getName))
                .collect(Collectors.toList());
    }

    public List<BrokerRouteVO> getTopicRoutes(String name) {
        return routes.getOrDefault(name, List.of());
    }

    public TopicRouteExaminationVO examineTopicRouteInfo(String name) {
        List<BrokerRouteVO> topicRoutes = getTopicRoutes(name);
        List<String> findings = new ArrayList<>();

        if (topicRoutes.isEmpty()) {
            return TopicRouteExaminationVO.builder()
                    .topic(name)
                    .brokerCount(0)
                    .totalWriteQueues(0)
                    .totalReadQueues(0)
                    .readWriteBalanced(false)
                    .health("ERROR")
                    .findings(List.of(
                            "No route info found for topic; ensure the topic is created and synced to the NameServer"))
                    .routes(List.of())
                    .build();
        }

        int brokerCount = topicRoutes.size();
        int totalWriteQueues = topicRoutes.stream().mapToInt(BrokerRouteVO::getWriteQueues).sum();
        int totalReadQueues = topicRoutes.stream().mapToInt(BrokerRouteVO::getReadQueues).sum();
        boolean readWriteBalanced = topicRoutes.stream()
                .allMatch(route -> route.getReadQueues() == route.getWriteQueues());

        boolean queueImbalance = topicRoutes.stream()
                .map(BrokerRouteVO::getWriteQueues)
                .distinct()
                .count() > 1;

        if (!readWriteBalanced) {
            topicRoutes.stream()
                    .filter(route -> route.getReadQueues() != route.getWriteQueues())
                    .forEach(route -> findings.add(String.format(
                            "Broker %s has inconsistent read/write queues (write %d / read %d)",
                            route.getBrokerName(), route.getWriteQueues(), route.getReadQueues())));
        }
        if (queueImbalance) {
            findings.add(
                    "Queue counts are unbalanced across brokers, which may cause consumption skew; "
                            + "align queue counts per broker");
        }
        if (brokerCount < 2) {
            findings.add(
                    "Topic is hosted on a single broker, creating a single point of failure; "
                            + "deploy at least 2 brokers");
        }
        if (totalWriteQueues == 0 || totalReadQueues == 0) {
            findings.add("Total queue count is 0; messages cannot be read or written");
        }

        String health;
        if (totalWriteQueues == 0 || totalReadQueues == 0) {
            health = "ERROR";
        } else if (!findings.isEmpty()) {
            health = "WARNING";
        } else {
            health = "HEALTHY";
            findings.add("Route distribution is healthy; read/write queues are consistent across brokers");
        }

        return TopicRouteExaminationVO.builder()
                .topic(name)
                .brokerCount(brokerCount)
                .totalWriteQueues(totalWriteQueues)
                .totalReadQueues(totalReadQueues)
                .readWriteBalanced(readWriteBalanced)
                .health(health)
                .findings(findings)
                .routes(topicRoutes)
                .build();
    }

    // ── Write paths ────────────────────────────────────────────────

    public TopicVO createTopic(TopicVO topic) {
        if (topic.getName() == null || topic.getName().isBlank()) {
            throw new IllegalArgumentException("topic name must not be empty");
        }
        LocalDateTime now = LocalDateTime.now();
        if (topic.getType() == null) {
            topic.setType(TopicType.NORMAL);
        }
        if (topic.getPerm() == null) {
            topic.setPerm(TopicPerm.RW);
        }
        if (topic.getWriteQueues() <= 0) {
            topic.setWriteQueues(8);
        }
        if (topic.getReadQueues() <= 0) {
            topic.setReadQueues(topic.getWriteQueues());
        }
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        topics.put(topic.getName(), topic);
        ensureRoutes(topic);
        log.info("created topic {} in cluster {}", topic.getName(), topic.getClusterId());
        return topic;
    }

    public TopicVO updateTopic(TopicVO topic) {
        TopicVO existing = topics.get(topic.getName());
        if (existing == null) {
            throw new IllegalArgumentException("topic not found: " + topic.getName());
        }
        if (topic.getNamespace() != null) {
            existing.setNamespace(topic.getNamespace());
        }
        if (topic.getClusterId() != null) {
            existing.setClusterId(topic.getClusterId());
        }
        if (topic.getType() != null) {
            existing.setType(topic.getType());
        }
        if (topic.getPerm() != null) {
            existing.setPerm(topic.getPerm());
        }
        if (topic.getWriteQueues() > 0) {
            existing.setWriteQueues(topic.getWriteQueues());
        }
        if (topic.getReadQueues() > 0) {
            existing.setReadQueues(topic.getReadQueues());
        }
        if (topic.getRemark() != null) {
            existing.setRemark(topic.getRemark());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return existing;
    }

    public void deleteTopic(String name) {
        topics.remove(name);
        routes.remove(name);
        log.info("deleted topic {}", name);
    }

    public TopicVO getTopic(String name) {
        return topics.get(name);
    }

    // ── Seed data (mirrors the web demo dataset) ──────────────────

    private void seed() {
        seedTopic("order-create", "trade", TopicType.NORMAL, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 1_842_350, 1280, 8, "Order creation event notifications");
        seedTopic("user-activity-log", "user", TopicType.NORMAL, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 5_623_100, 3450, 5, "User activity log sync");
        seedTopic("system-log", "message", TopicType.NORMAL, "rmq-cn-v4-prod-02",
                8, 8, TopicPerm.RW, 12_480_000, 8620, 3, "System log collection and distribution");
        seedTopic("notification-email", "message", TopicType.NORMAL, "rmq-cn-v5-prod-01",
                4, 4, TopicPerm.RW, 328_700, 215, 2, "Email notification trigger");
        seedTopic("inventory-sync", "supply", TopicType.FIFO, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 956_400, 680, 4, "Inventory sync sequential messages");
        seedTopic("payment-sequence", "trade", TopicType.FIFO, "rmq-cn-v5-prod-01",
                8, 8, TopicPerm.RW, 412_850, 320, 6, "Payment flow sequential processing");
        seedTopic("notification-push", "message", TopicType.DELAY, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 2_105_600, 1540, 3, "Delayed push notification scheduling");
        seedTopic("scheduled-task", "supply", TopicType.DELAY, "rmq-cn-v4-prod-02",
                4, 4, TopicPerm.RW, 87_300, 56, 2, "Scheduled task trigger");
        seedTopic("payment-callback", "trade", TopicType.TRANSACTION, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 634_200, 420, 5, "Payment callback transaction processing");
        seedTopic("order-confirm", "trade", TopicType.TRANSACTION, "rmq-cn-v5-prod-01",
                8, 8, TopicPerm.RW, 521_800, 360, 4, "Order confirmation transaction messages");
        seedTopic("chat-session", "ai", TopicType.LITE, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 8_920_000, 6200, 1, "AI chat session message dispatch");
        seedTopic("ai-task-dispatch", "ai", TopicType.LITE, "rmq-cn-v5-prod-01",
                16, 16, TopicPerm.RW, 3_450_000, 2380, 2, "AI task scheduling and state sync");
    }

    private void seedTopic(String name, String namespace, TopicType type, String clusterId,
                           int writeQueues, int readQueues, TopicPerm perm,
                           long messageCount, double tps, int consumerGroupCount, String remark) {
        TopicVO topic = new TopicVO();
        topic.setName(name);
        topic.setNamespace(namespace);
        topic.setClusterId(clusterId);
        topic.setType(type);
        topic.setWriteQueues(writeQueues);
        topic.setReadQueues(readQueues);
        topic.setPerm(perm);
        topic.setMessageCount(messageCount);
        topic.setTps(tps);
        topic.setConsumerGroupCount(consumerGroupCount);
        topic.setRemark(remark);
        topic.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        topic.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        topics.put(name, topic);
        ensureRoutes(topic);
    }

    private void ensureRoutes(TopicVO topic) {
        routes.computeIfAbsent(topic.getName(), name -> {
            int perBroker = Math.max(1, topic.getWriteQueues() / 2);
            BrokerRouteVO a = BrokerRouteVO.builder()
                    .brokerName("broker-a-0")
                    .brokerAddr("10.0.1.10:10911")
                    .writeQueues(perBroker)
                    .readQueues(perBroker)
                    .perm(topic.getPerm())
                    .build();
            BrokerRouteVO b = BrokerRouteVO.builder()
                    .brokerName("broker-b-0")
                    .brokerAddr("10.0.1.11:10911")
                    .writeQueues(perBroker)
                    .readQueues(perBroker)
                    .perm(topic.getPerm())
                    .build();
            return List.of(a, b);
        });
    }
}
