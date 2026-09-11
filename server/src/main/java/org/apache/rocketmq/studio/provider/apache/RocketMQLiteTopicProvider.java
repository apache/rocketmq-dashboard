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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.common.lite.LiteUtil;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.GetBrokerLiteInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetLiteClientInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetLiteGroupInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetParentTopicInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.LiteTopicQuota;
import org.apache.rocketmq.studio.model.LiteTopicSession;
import org.apache.rocketmq.studio.model.LiteTopicSummary;
import org.apache.rocketmq.studio.provider.LiteTopicProvider;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Live {@link LiteTopicProvider} backed by the RocketMQ broker lite admin RPCs.
 *
 * <p>Everything reported here comes from the broker: parent topics (and their TTL) from
 * {@code GET_BROKER_LITE_INFO}, per-parent lite topic counts from {@code GET_PARENT_TOPIC_INFO},
 * sessions from the consumer connections plus {@code GET_LITE_CLIENT_INFO}, and backlog from
 * {@code GET_LITE_GROUP_INFO}. No name-prefix guessing is involved — a lite topic is identified
 * through {@link LiteUtil}, which is the same helper the broker uses.
 *
 * <p>The console reaches lite endpoints without an instance id, so the queries run against the
 * default configured NameServer, exactly like the other legacy cluster-scoped metadata calls.
 *
 * <p>Data the broker does not expose is left {@code null} rather than fabricated: a session has no
 * broker-side creation timestamp, and there is no per-parent "last active" clock, so the newest
 * client {@code lastAccessTime} under the parent is used as the closest real signal.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQLiteTopicProvider implements LiteTopicProvider {

    /** Namespace label used for broker-created resources, which carry no namespace of their own. */
    static final String DEFAULT_NAMESPACE = "DEFAULT";

    /** Separator inside the opaque session id; no topic or consumer group name may contain it. */
    static final String SESSION_SEPARATOR = "~";

    /** Upper bound on parent topics resolved per list call, to keep one page load bounded. */
    static final int MAX_LITE_TOPIC_SCAN = 200;

    /** Upper bound on sessions resolved per parent topic. */
    static final int MAX_LITE_SESSION_SCAN = 500;

    /** Upper bound on per-lite-topic offset lookups for one session detail. */
    static final int MAX_SESSION_LITE_TOPIC_SCAN = 200;

    /** Mirror of {@code TopicAttributes.LITE_EXPIRATION_ATTRIBUTE}'s upper bound (30 days). */
    static final int MAX_LITE_TTL_MINUTES = (int) TimeUnit.DAYS.toMinutes(30);

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;

    // ─── Capability ───────────────────────────────────────────────────

    @Override
    public boolean isSupported() {
        if (!hasAdmin()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(adminFactory.execute(properties.getNamesrvAddr(), null, admin -> {
                List<String> masters = masterAddresses(admin);
                return !masters.isEmpty() && admin.getBrokerLiteInfo(masters.get(0)) != null;
            }));
        } catch (Exception probeFailure) {
            // An older broker answers the lite RPC with an unsupported-code error; that is the
            // signal the console degrades on, so report it as unsupported instead of failing.
            log.debug("LiteTopic capability probe failed: {}", probeFailure.getMessage());
            return false;
        }
    }

    // ─── List ─────────────────────────────────────────────────────────

    @Override
    public List<LiteTopicSummary> listLiteTopics(String pattern, String namespace) {
        requireAdmin();
        return execute(admin -> {
            List<String> masters = masterAddresses(admin);
            if (masters.isEmpty()) {
                throw new BusinessException(503, "No broker master is available for LiteTopic queries");
            }
            Map<String, ParentTopicAccumulator> parents = discoverParentTopics(admin, masters);
            List<LiteTopicSummary> summaries = new ArrayList<>();
            int scanned = 0;
            for (ParentTopicAccumulator parent : parents.values()) {
                if (!matchesPattern(parent.parentTopic, pattern)) {
                    continue;
                }
                if (StringUtils.hasText(namespace)
                        && !DEFAULT_NAMESPACE.equalsIgnoreCase(namespace.trim())) {
                    continue;
                }
                if (scanned++ >= MAX_LITE_TOPIC_SCAN) {
                    log.warn("LiteTopic list truncated at {} parent topics", MAX_LITE_TOPIC_SCAN);
                    break;
                }
                summaries.add(buildSummary(admin, masters, parent));
            }
            summaries.sort(Comparator.comparing(LiteTopicSummary::getTopicPattern,
                    Comparator.nullsLast(String::compareTo)));
            return summaries;
        });
    }

    private Map<String, ParentTopicAccumulator> discoverParentTopics(MQAdminExt admin, List<String> masters)
            throws Exception {
        Map<String, ParentTopicAccumulator> parents = new LinkedHashMap<>();
        for (String master : masters) {
            GetBrokerLiteInfoResponseBody info = admin.getBrokerLiteInfo(master);
            if (info == null || info.getTopicMeta() == null) {
                continue;
            }
            info.getTopicMeta().forEach((parent, ttlMinutes) -> parents
                    .computeIfAbsent(parent, key -> new ParentTopicAccumulator(key, master))
                    .merge(master, ttlMinutes));
            if (info.getGroupMeta() != null) {
                info.getGroupMeta().forEach((parent, groups) -> {
                    if (groups == null || groups.isEmpty()) {
                        return;
                    }
                    // A parent topic can be sharded across brokers; the group binding is reported
                    // by whichever broker owns the lite topic, so union across masters.
                    parents.computeIfAbsent(parent, key -> new ParentTopicAccumulator(key, master))
                            .groups.addAll(groups);
                });
            }
        }
        return parents;
    }

    private LiteTopicSummary buildSummary(MQAdminExt admin, List<String> masters, ParentTopicAccumulator parent) {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setTopicPattern(parent.parentTopic);
        summary.setNamespace(DEFAULT_NAMESPACE);

        Long ttlMillis = toMillis(parent.ttlMinutes);
        summary.setAverageTTL(ttlMillis);
        summary.setMinTTL(ttlMillis);
        summary.setMaxTTL(ttlMillis);

        int topicCount = 0;
        try {
            GetParentTopicInfoResponseBody parentInfo = admin.getParentTopicInfo(parent.brokerAddr, parent.parentTopic);
            if (parentInfo != null) {
                topicCount = Math.max(parentInfo.getLiteTopicCount(), 0);
            }
        } catch (Exception failure) {
            log.debug("Failed to read parent topic info for {}: {}", parent.parentTopic, failure.getMessage());
        }

        long totalBacklog = 0;
        Long lastActive = null;
        Set<String> sessionIds = new LinkedHashSet<>();
        int consumerCount = 0;
        int sessionBudget = MAX_LITE_SESSION_SCAN;
        for (String group : parent.groups) {
            totalBacklog += groupLag(admin, parent.brokerAddr, group);
            for (Connection connection : consumerConnections(admin, group)) {
                if (sessionBudget-- <= 0) {
                    log.warn("LiteTopic session scan for {} truncated at {} sessions",
                            parent.parentTopic, MAX_LITE_SESSION_SCAN);
                    break;
                }
                consumerCount++;
                sessionIds.add(encodeSessionId(parent.parentTopic, group, connection.getClientId()));
                Long active = clientLastAccess(admin, masters, parent.parentTopic, group,
                        connection.getClientId());
                if (active != null && (lastActive == null || active > lastActive)) {
                    lastActive = active;
                }
            }
        }

        summary.setTopicCount(topicCount);
        summary.setConsumerCount(consumerCount);
        summary.setTotalBacklog(totalBacklog);
        summary.setSessionIds(new ArrayList<>(sessionIds));
        summary.setActive(consumerCount > 0);
        if (lastActive != null) {
            summary.setLastActiveTime(new Date(lastActive));
        }
        return summary;
    }

    // ─── Session detail ───────────────────────────────────────────────

    @Override
    public LiteTopicSession getSession(String sessionId) {
        requireAdmin();
        String[] parts = decodeSessionId(sessionId);
        String parentTopic = parts[0];
        String group = parts[1];
        String clientId = parts[2];
        return execute(admin -> {
            List<String> masters = masterAddresses(admin);
            LocatedClient located = locateClient(admin, masters, parentTopic, group, clientId);
            if (located == null) {
                throw new BusinessException(404, "LiteTopic session not found: " + sessionId);
            }
            return buildSession(admin, located, sessionId, parentTopic, group, clientId);
        });
    }

    private LiteTopicSession buildSession(MQAdminExt admin, LocatedClient located, String sessionId,
                                          String parentTopic, String group, String clientId) {
        GetLiteClientInfoResponseBody clientInfo = located.body;
        LiteTopicSession session = new LiteTopicSession();
        session.setSessionId(sessionId);
        session.setClientId(clientId);
        session.setClientAddress(clientAddress(admin, group, clientId));
        session.setParentTopic(parentTopic);
        session.setConsumerGroup(group);
        session.setLiteTopicCreationCount(clientInfo.getLiteTopicCount() >= 0
                ? clientInfo.getLiteTopicCount() : null);

        Long ttlMillis = toMillis(parentTtlMinutes(admin, located.master, parentTopic));
        session.setTtl(ttlMillis);

        Set<String> lmqSet = clientInfo.getLiteTopicSet() == null
                ? Set.of() : clientInfo.getLiteTopicSet();
        List<String> liteTopics = lmqSet.stream()
                .map(LiteUtil::getLiteTopic)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        session.setLiteTopics(new LinkedHashSet<>(liteTopics));

        long pending = groupLag(admin, located.master, group);
        long consumed = consumedMessages(admin, located.master, group, liteTopics);
        session.setPendingMessages(pending);
        session.setConsumedMessages(consumed);
        session.setTotalMessages(consumed + pending);
        session.setConsumptionRate(session.getConsumptionProgress());

        long lastAccess = clientInfo.getLastAccessTime();
        if (lastAccess > 0) {
            session.setLastActiveTime(new Date(lastAccess));
        }
        applyTtlState(session, ttlMillis, lastAccess > 0 ? lastAccess : null);
        return session;
    }

    private void applyTtlState(LiteTopicSession session, Long ttlMillis, Long lastAccess) {
        if (ttlMillis == null || lastAccess == null) {
            // No expiration attribute means the broker never expires this session's lite topics.
            session.setStatus("ACTIVE");
            return;
        }
        long remaining = ttlMillis - (System.currentTimeMillis() - lastAccess);
        session.setTtlRemaining(Math.max(remaining, 0));
        session.setStatus(remaining > 0 ? "ACTIVE" : "EXPIRED");
    }

    private long consumedMessages(MQAdminExt admin, String brokerAddr, String group, List<String> liteTopics) {
        long consumed = 0;
        int scanned = 0;
        for (String liteTopic : liteTopics) {
            if (scanned++ >= MAX_SESSION_LITE_TOPIC_SCAN) {
                log.warn("LiteTopic session consumed-offset scan truncated at {} lite topics",
                        MAX_SESSION_LITE_TOPIC_SCAN);
                break;
            }
            try {
                GetLiteGroupInfoResponseBody body = admin.getLiteGroupInfo(brokerAddr, group, liteTopic, 1);
                OffsetWrapper wrapper = body == null ? null : body.getLiteTopicOffsetWrapper();
                if (wrapper != null && wrapper.getConsumerOffset() > 0) {
                    consumed += wrapper.getConsumerOffset();
                }
            } catch (Exception failure) {
                log.debug("Failed to read lite offset for {}|{}: {}", group, liteTopic, failure.getMessage());
            }
        }
        return consumed;
    }

    // ─── TTL update ───────────────────────────────────────────────────

    @Override
    public void extendTTL(String topicPattern, long ttlMillis) {
        requireAdmin();
        if (!StringUtils.hasText(topicPattern)) {
            throw new BusinessException(400, "topicPattern is required");
        }
        if (ttlMillis <= 0) {
            throw new BusinessException(400, "newTTL must be positive");
        }
        long minutes = Math.min(Math.max(Math.round(ttlMillis / 60000.0), 1), MAX_LITE_TTL_MINUTES);
        execute(admin -> {
            int updated = 0;
            for (String master : masterAddresses(admin)) {
                TopicConfig config = liteTopicConfig(admin, master, topicPattern);
                if (config == null) {
                    continue;
                }
                // Attributes read back from the broker use bare keys ("lite.topic.expiration"),
                // while the update protocol only accepts change entries ("+key=value"); a bare
                // key is rejected with "add/alter attribute format is wrong". The broker merges
                // this change set into the stored attributes, so re-sending message.type is
                // unnecessary - and message.type is validated as immutable on alter anyway.
                // Only the TTL is altered.
                Map<String, String> change = new HashMap<>();
                change.put("+lite.topic.expiration", String.valueOf(minutes));
                config.setAttributes(change);
                admin.createAndUpdateTopicConfig(master, config);
                updated++;
            }
            if (updated == 0) {
                throw new BusinessException(404, "Lite parent topic not found: " + topicPattern);
            }
            log.info("Extended LiteTopic TTL to {}ms ({} min) for parent topic {} on {} broker(s)",
                    ttlMillis, minutes, topicPattern, updated);
            return null;
        });
    }

    private TopicConfig liteTopicConfig(MQAdminExt admin, String brokerAddr, String topic) {
        try {
            TopicConfig config = admin.examineTopicConfig(brokerAddr, topic);
            if (config == null || !TopicMessageType.LITE.equals(config.getTopicMessageType())) {
                return null;
            }
            return config;
        } catch (Exception failure) {
            log.debug("Parent topic {} is not configured on {}: {}", topic, brokerAddr, failure.getMessage());
            return null;
        }
    }

    // ─── Quota ────────────────────────────────────────────────────────

    @Override
    public LiteTopicQuota getQuota(String namespace) {
        requireAdmin();
        return execute(admin -> {
            List<String> masters = masterAddresses(admin);
            if (masters.isEmpty()) {
                throw new BusinessException(503, "No broker master is available for LiteTopic queries");
            }
            long currentTopics = 0;
            long maxTopics = 0;
            long currentSessions = 0;
            long maxSessions = 0;
            long minLiteTtlMillis = -1;
            for (String master : masters) {
                GetBrokerLiteInfoResponseBody info = admin.getBrokerLiteInfo(master);
                if (info != null) {
                    currentTopics += Math.max(info.getCurrentLmqNum(), 0);
                    maxTopics += Math.max(info.getMaxLmqNum(), 0);
                    currentSessions += Math.max(info.getLiteSubscriptionCount(), 0);
                }
                Properties brokerConfig = brokerConfig(admin, master);
                if (brokerConfig != null) {
                    maxSessions += parsePositiveLong(brokerConfig.getProperty("maxLiteSubscriptionCount"));
                    long minTtl = parsePositiveLong(brokerConfig.getProperty("minLiteTTl"));
                    if (minTtl > 0) {
                        minLiteTtlMillis = minLiteTtlMillis < 0 ? minTtl : Math.min(minLiteTtlMillis, minTtl);
                    }
                }
            }

            LiteTopicQuota quota = new LiteTopicQuota();
            quota.setCurrentTopicCount(toInt(currentTopics));
            quota.setMaxTopicCount(toInt(maxTopics));
            quota.setCurrentSessionCount(toInt(currentSessions));
            quota.setMaxSessionCount(toInt(maxSessions));
            // The protocol caps lite.topic.expiration at 30 days; the broker floor (minLiteTTl)
            // is the effective TTL applied when a parent topic leaves the attribute unset.
            quota.setMaxTTL(TimeUnit.MINUTES.toMillis(MAX_LITE_TTL_MINUTES));
            if (minLiteTtlMillis > 0) {
                quota.setDefaultTTL(minLiteTtlMillis);
            }
            // No broker-side creation-rate quota exists; report zero so the console renders a
            // defined value instead of a blank gauge.
            quota.setCurrentCreationRate(0.0);
            quota.setMaxCreationRate(0.0);
            return quota;
        });
    }

    private Properties brokerConfig(MQAdminExt admin, String brokerAddr) {
        try {
            return admin.getBrokerConfig(brokerAddr);
        } catch (Exception failure) {
            log.debug("Failed to read broker config for {}: {}", brokerAddr, failure.getMessage());
            return null;
        }
    }

    // ─── Admin plumbing ───────────────────────────────────────────────

    private void requireAdmin() {
        if (!hasAdmin()) {
            throw new BusinessException(NOT_IMPLEMENTED, UNSUPPORTED);
        }
    }

    private boolean hasAdmin() {
        return StringUtils.hasText(properties.getNamesrvAddr());
    }

    private <T> T execute(MqAdminExtFactory.AdminAction<T> action) {
        return adminFactory.execute(properties.getNamesrvAddr(), null, action);
    }

    private List<String> masterAddresses(MQAdminExt admin) throws Exception {
        ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return List.of();
        }
        List<String> masters = new ArrayList<>();
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null || brokerData.getBrokerAddrs() == null
                    || brokerData.getBrokerAddrs().isEmpty()) {
                continue;
            }
            String master = brokerData.getBrokerAddrs().get(MixAll.MASTER_ID);
            if (master == null) {
                master = brokerData.getBrokerAddrs().values().iterator().next();
            }
            if (master != null) {
                masters.add(master);
            }
        }
        return masters;
    }

    /**
     * A client's lite subscription lives on the broker that owns its channel, so the session has to
     * be resolved against the master that actually reports it (a negative topic count means the
     * broker does not hold that client's subscription).
     */
    private LocatedClient locateClient(MQAdminExt admin, List<String> masters, String parentTopic,
                                      String group, String clientId) {
        for (String master : masters) {
            try {
                GetLiteClientInfoResponseBody body = admin.getLiteClientInfo(master, parentTopic, group, clientId);
                if (body != null && body.getLiteTopicCount() >= 0) {
                    return new LocatedClient(master, body);
                }
            } catch (Exception failure) {
                log.debug("Lite client {} not resolvable on {}: {}", clientId, master, failure.getMessage());
            }
        }
        return null;
    }

    private Long clientLastAccess(MQAdminExt admin, List<String> masters, String parentTopic,
                                  String group, String clientId) {
        LocatedClient located = locateClient(admin, masters, parentTopic, group, clientId);
        if (located == null || located.body.getLastAccessTime() <= 0) {
            return null;
        }
        return located.body.getLastAccessTime();
    }

    private Set<Connection> consumerConnections(MQAdminExt admin, String group) {
        try {
            ConsumerConnection connection = admin.examineConsumerConnectionInfo(group);
            if (connection == null || connection.getConnectionSet() == null) {
                return Set.of();
            }
            return connection.getConnectionSet();
        } catch (Exception failure) {
            log.debug("Failed to read consumer connections for group {}: {}", group, failure.getMessage());
            return Set.of();
        }
    }

    private String clientAddress(MQAdminExt admin, String group, String clientId) {
        for (Connection connection : consumerConnections(admin, group)) {
            if (clientId != null && clientId.equals(connection.getClientId())) {
                return connection.getClientAddr();
            }
        }
        return null;
    }

    private long groupLag(MQAdminExt admin, String brokerAddr, String group) {
        try {
            GetLiteGroupInfoResponseBody body = admin.getLiteGroupInfo(brokerAddr, group, null, 1);
            if (body == null) {
                return 0;
            }
            return Math.max(body.getTotalLagCount(), 0);
        } catch (Exception failure) {
            log.debug("Failed to read lite backlog for group {} on {}: {}", group, brokerAddr, failure.getMessage());
            return 0;
        }
    }

    private Integer parentTtlMinutes(MQAdminExt admin, String brokerAddr, String parentTopic) {
        try {
            GetParentTopicInfoResponseBody body = admin.getParentTopicInfo(brokerAddr, parentTopic);
            return body == null ? null : body.getTtl();
        } catch (Exception failure) {
            log.debug("Failed to read TTL for parent topic {}: {}", parentTopic, failure.getMessage());
            return null;
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    static String encodeSessionId(String parentTopic, String group, String clientId) {
        return parentTopic + SESSION_SEPARATOR + group + SESSION_SEPARATOR + clientId;
    }

    static String[] decodeSessionId(String sessionId) {
        String[] parts = sessionId == null ? new String[0] : sessionId.split(SESSION_SEPARATOR, 3);
        if (parts.length != 3 || !StringUtils.hasText(parts[0])
                || !StringUtils.hasText(parts[1]) || !StringUtils.hasText(parts[2])) {
            throw new BusinessException(400, "Malformed LiteTopic session id");
        }
        return parts;
    }

    private static boolean matchesPattern(String value, String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT)
                .contains(pattern.trim().toLowerCase(Locale.ROOT));
    }

    private static Long toMillis(Integer minutes) {
        return minutes == null || minutes <= 0 ? null : TimeUnit.MINUTES.toMillis(minutes);
    }

    private static long parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Long.parseLong(raw.trim()), 0);
        } catch (NumberFormatException invalidNumber) {
            return 0;
        }
    }

    private static Integer toInt(long value) {
        return (int) Math.min(Math.max(value, 0), Integer.MAX_VALUE);
    }

    /** One parent topic observed across broker masters, with its TTL and bound consumer groups. */
    private static final class ParentTopicAccumulator {
        private final String parentTopic;
        private String brokerAddr;
        private final Set<String> groups = new LinkedHashSet<>();
        private int ttlMinutes = -1;

        private ParentTopicAccumulator(String parentTopic, String brokerAddr) {
            this.parentTopic = parentTopic;
            this.brokerAddr = brokerAddr;
        }

        private void merge(String brokerAddr, Integer ttlMinutes) {
            this.brokerAddr = brokerAddr;
            if (ttlMinutes != null && ttlMinutes > this.ttlMinutes) {
                this.ttlMinutes = ttlMinutes;
            }
        }
    }

    /** The broker master that reported a client's lite subscription, plus that client's info. */
    private record LocatedClient(String master, GetLiteClientInfoResponseBody body) {
    }
}
