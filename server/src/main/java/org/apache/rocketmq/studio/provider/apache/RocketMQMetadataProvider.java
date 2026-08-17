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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.util.SystemGroupFilter;
import org.apache.rocketmq.studio.common.util.SystemTopicFilter;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real MetadataProvider implementation.
 *
 * <p>Topic and consumer group listings are served from the studio metadata database, which is the
 * source of record: creation writes there and reads never fall back to the broker. Route,
 * consumer, progress and subscription queries stay live through the RocketMQ admin API, so a
 * record without a broker route surfaces as an empty route list instead of being hidden.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQMetadataProvider implements MetadataProvider {

    private static final Set<String> SYSTEM_TOPIC_PREFIXES = Set.of(
            "RMQ_SYS_", "SCHEDULE_TOPIC_", "%RETRY%", "%DLQ%", "CID_"
    );

    private static final Set<String> SYSTEM_TOPICS = Set.of(
            "TBW102", "SELF_TEST_TOPIC", "DefaultCluster", "OFFSET_MOVED_EVENT",
            "broker", "SCHEDULE_TOPIC_XXXX", "RMQ_SYS_TRANS_HALF_TOPIC",
            "RMQ_SYS_TRACE_TOPIC", "RMQ_SYS_TRANS_OP_HALF_TOPIC"
    );

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    /**
     * Default proxy stats source until a real proxy transport is wired in. It reports the unknown
     * sentinel ({@link ConsumerLagResolver#UNKNOWN}) so a {@code -1} gRPC lag is surfaced instead of
     * being silently clamped to zero.
     */
    private final ProxyStatsProvider proxyStatsProvider = new NoopProxyStatsProvider();

    /** Whether a default NameServer is configured and live queries are therefore possible. */
    private boolean hasAdmin() {
        return StringUtils.hasText(properties.getNamesrvAddr());
    }

    private <T> T adminExecute(MqAdminExtFactory.AdminAction<T> action) {
        return adminFactory.execute(properties.getNamesrvAddr(), null, action);
    }

    @Override
    public List<TopicVO> listTopics(String clusterId, String type, String search) {
        LambdaQueryWrapper<RmqTopic> query = new LambdaQueryWrapper<RmqTopic>()
                .eq(StringUtils.hasText(clusterId), RmqTopic::getClusterId, clusterId)
                .eq(StringUtils.hasText(type), RmqTopic::getTopicType, type)
                .like(StringUtils.hasText(search), RmqTopic::getName, search)
                .orderByAsc(RmqTopic::getName);

        List<TopicVO> result = new ArrayList<>();
        for (RmqTopic entity : topicMapper.selectList(query)) {
            if (isSystemTopic(entity.getName(), Collections.emptySet())) {
                continue;
            }
            result.add(toTopicVO(entity));
        }
        return result;
    }

    @Override
    public PageResult<TopicVO> listTopicsPage(String instanceId, String clusterId, String type, String search, int page, int pageSize) {
        LambdaQueryWrapper<RmqTopic> query = new LambdaQueryWrapper<RmqTopic>()
                .eq(StringUtils.hasText(instanceId), RmqTopic::getInstanceId, instanceId)
                .eq(StringUtils.hasText(clusterId), RmqTopic::getClusterId, clusterId)
                .eq(StringUtils.hasText(type), RmqTopic::getTopicType, type)
                .like(StringUtils.hasText(search), RmqTopic::getName, search)
                .notLikeRight(RmqTopic::getName, "RMQ_SYS_")
                .orderByAsc(RmqTopic::getName, RmqTopic::getId);
        Page<RmqTopic> result = topicMapper.selectPage(new Page<>(page, pageSize), query);
        return PageResult.of(result.getRecords().stream().map(this::toTopicVO).toList(), result.getTotal(), page, pageSize);
    }

    private TopicVO toTopicVO(RmqTopic entity) {
        TopicVO vo = new TopicVO();
        vo.setId(entity.getName());
        vo.setName(entity.getName());
        vo.setClusterId(entity.getClusterId());
        vo.setInstanceId(entity.getInstanceId());
        vo.setType(parseTopicType(entity.getTopicType()));
        vo.setReadQueues(entity.getReadQueueNums() == null ? 0 : entity.getReadQueueNums());
        vo.setWriteQueues(entity.getWriteQueueNums() == null ? 0 : entity.getWriteQueueNums());
        vo.setPerm(parseTopicPerm(entity.getPerm()));
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private TopicType parseTopicType(String type) {
        if (!StringUtils.hasText(type)) {
            return TopicType.NORMAL;
        }
        try {
            return TopicType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown topic type {}, falling back to NORMAL", type);
            return TopicType.NORMAL;
        }
    }

    private TopicPerm parseTopicPerm(Integer perm) {
        if (perm == null) {
            return TopicPerm.RW;
        }
        return switch (perm) {
            case 2 -> TopicPerm.WO;
            case 4 -> TopicPerm.RO;
            default -> TopicPerm.RW;
        };
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search) {
        return listConsumerGroups(null, clusterId, search);
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String clusterId, String search) {
        LambdaQueryWrapper<RmqGroup> query = new LambdaQueryWrapper<RmqGroup>()
                .eq(StringUtils.hasText(instanceId), RmqGroup::getInstanceId, instanceId)
                .eq(StringUtils.hasText(clusterId), RmqGroup::getClusterId, clusterId)
                .like(StringUtils.hasText(search), RmqGroup::getName, search)
                .orderByAsc(RmqGroup::getName);

        List<ConsumerGroupVO> result = new ArrayList<>();
        for (RmqGroup entity : groupMapper.selectList(query)) {
            ConsumerGroupVO vo = new ConsumerGroupVO();
            vo.setId(entity.getName());
            vo.setName(entity.getName());
            vo.setClusterId(entity.getClusterId());
            vo.setInstanceId(entity.getInstanceId());
            // consumeType stores the real ConsumeType ("CLUSTERING"/"BROADCASTING"); messageModel
            // holds the subscription mode ("Push"/"Pop") and is not a ConsumeType.
            vo.setConsumeType(parseConsumeType(entity.getConsumeType()));
            // messageModel stores the subscription mode ("Push"/"Pop"); surface it so read paths
            // (web detail, AI rmq.group.list) never see a null subscriptionMode.
            vo.setSubscriptionMode(parseSubscriptionMode(entity.getMessageModel()));
            vo.setRetryMaxTimes(entity.getMaxRetry() == null ? 0 : entity.getMaxRetry());
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setUpdatedAt(entity.getUpdatedAt());

            // Live connection info (online instances, lag) is intentionally NOT fetched
            // during list operations to avoid N+1 admin API calls. It is loaded on
            // demand when viewing a single group's detail page.
            result.add(vo);
        }
        return result;
    }

    private ConsumeType parseConsumeType(String messageModel) {
        if (!StringUtils.hasText(messageModel)) {
            return ConsumeType.CLUSTERING;
        }
        try {
            return ConsumeType.valueOf(messageModel);
        } catch (IllegalArgumentException ex) {
            log.debug("Unknown message model {}, falling back to CLUSTERING", messageModel);
            return ConsumeType.CLUSTERING;
        }
    }

    private SubscriptionMode parseSubscriptionMode(String messageModel) {
        if ("Pop".equalsIgnoreCase(messageModel)) {
            return SubscriptionMode.Pop;
        }
        return SubscriptionMode.Push;
    }

    @Override
    public List<BrokerRouteVO> getTopicRoutes(String instanceId, String name) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, admin -> getTopicRoutes(admin, name));
        }
        if (!hasAdmin()) {
            return Collections.emptyList();
        }
        return adminExecute(admin -> getTopicRoutes(admin, name));
    }

    private List<BrokerRouteVO> getTopicRoutes(MQAdminExt admin, String name) {
        try {
            TopicRouteData routeData = admin.examineTopicRouteInfo(name);
            if (routeData == null) {
                return Collections.emptyList();
            }

            Map<String, BrokerData> brokerDataMap = new HashMap<>();
            if (routeData.getBrokerDatas() != null) {
                for (BrokerData bd : routeData.getBrokerDatas()) {
                    brokerDataMap.put(bd.getBrokerName(), bd);
                }
            }

            List<BrokerRouteVO> routes = new ArrayList<>();
            if (routeData.getQueueDatas() != null) {
                for (QueueData qd : routeData.getQueueDatas()) {
                    BrokerData bd = brokerDataMap.get(qd.getBrokerName());
                    String brokerAddr = "";
                    if (bd != null && bd.getBrokerAddrs() != null && !bd.getBrokerAddrs().isEmpty()) {
                        brokerAddr = bd.getBrokerAddrs().get(MixAll.MASTER_ID);
                        if (brokerAddr == null) {
                            brokerAddr = bd.getBrokerAddrs().values().iterator().next();
                        }
                    }

                    routes.add(BrokerRouteVO.builder()
                            .brokerName(qd.getBrokerName())
                            .brokerAddr(brokerAddr)
                            .writeQueues(qd.getWriteQueueNums())
                            .readQueues(qd.getReadQueueNums())
                            .perm(mapPerm(qd.getPerm()))
                            .build());
                }
            }
            return routes;
        } catch (Exception e) {
            log.warn("Failed to get routes for topic {}: {}", name, e.getMessage());
            throw new BusinessException(502, "Failed to get routes for topic " + name + ": " + e.getMessage());
        }
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String name) {
        return getTopicConsumersPage(instanceId, name, 1, Integer.MAX_VALUE).getItems();
    }

    @Override
    public TopicConsumerPageVO getTopicConsumersPage(String instanceId, String name, int page, int pageSize) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId,
                    admin -> getTopicConsumersPage(admin, name, page, pageSize));
        }
        if (!hasAdmin()) {
            return TopicConsumerPageVO.builder().items(List.of()).total(0).page(page).pageSize(pageSize).build();
        }
        return adminExecute(admin -> getTopicConsumersPage(admin, name, page, pageSize));
    }

    private TopicConsumerPageVO getTopicConsumersPage(MQAdminExt admin, String name, int page, int pageSize) {
        try {
            // Ask the broker who consumes this topic instead of scanning every subscription
            // group, which floods the result with system groups.
            GroupList groupList = admin.queryTopicConsumeByWho(name);
            Set<String> subscribingGroups = new HashSet<>();
            if (groupList != null && groupList.getGroupList() != null) {
                for (String group : groupList.getGroupList()) {
                    if (!isSystemConsumerGroup(group)) {
                        subscribingGroups.add(group);
                    }
                }
            }

            List<String> sortedGroups = new ArrayList<>(subscribingGroups);
            sortedGroups.sort(String::compareToIgnoreCase);
            int total = sortedGroups.size();
            int from = Math.min((page - 1) * pageSize, total);
            int to = Math.min(from + pageSize, total);
            List<TopicConsumerVO> consumers = new ArrayList<>();
            for (String group : sortedGroups.subList(from, to)) {
                try {
                    ConsumeStats stats = admin.examineConsumeStats(group, name);
                    long diffTotal = 0;
                    double consumeTps = 0;
                    if (stats != null && stats.getOffsetTable() != null) {
                        for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                            OffsetWrapper ow = entry.getValue();
                            long queueDiff = resolveDiff(ow.getBrokerOffset(), ow.getConsumerOffset());
                            if (queueDiff == ConsumerLagResolver.UNKNOWN) {
                                diffTotal = ConsumerLagResolver.UNKNOWN;
                                break;
                            }
                            diffTotal += queueDiff;
                        }
                        consumeTps = stats.getConsumeTps();
                    }

                    ConsumeType consumeType = ConsumeType.CLUSTERING;
                    String messageModel = "CLUSTERING";
                    try {
                        ConsumerConnection conn = admin.examineConsumerConnectionInfo(group);
                        if (conn != null && conn.getMessageModel() != null) {
                            messageModel = conn.getMessageModel().name();
                            consumeType = parseConsumeType(messageModel);
                        }
                    } catch (Exception ignored) {
                        // group may be offline
                    }

                    consumers.add(TopicConsumerVO.builder()
                            .group(group)
                            .consumeType(consumeType)
                            .messageModel(messageModel)
                            .consumeTps(consumeTps)
                            .diffTotal(diffTotal)
                            .build());
                } catch (Exception ignored) {
                    // stats unavailable for this group, still list it below without numbers
                    consumers.add(TopicConsumerVO.builder()
                            .group(group)
                            .consumeType(ConsumeType.CLUSTERING)
                            .messageModel("CLUSTERING")
                            .consumeTps(0)
                            .diffTotal(0)
                            .metricsAvailable(false)
                            .build());
                }
            }
            return TopicConsumerPageVO.builder()
                    .items(consumers)
                    .total(total)
                    .page(page)
                    .pageSize(pageSize)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get consumers for topic {}: {}", name, e.getMessage());
            throw new BusinessException(502, "Failed to get consumers for topic " + name + ": " + e.getMessage());
        }
    }

    private boolean isSystemConsumerGroup(String group) {
        return SystemGroupFilter.isSystem(group);
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String instanceId, String name) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, admin -> getGroupProgress(admin, name));
        }
        if (!hasAdmin()) {
            return Collections.emptyList();
        }
        return adminExecute(admin -> getGroupProgress(admin, name));
    }

    private List<QueueProgressVO> getGroupProgress(MQAdminExt admin, String name) {
        try {
            ConsumeStats stats = admin.examineConsumeStats(name);
            if (stats == null || stats.getOffsetTable() == null) {
                return Collections.emptyList();
            }

            List<QueueProgressVO> progress = new ArrayList<>();
            for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                MessageQueue mq = entry.getKey();
                OffsetWrapper ow = entry.getValue();
                long diff = resolveDiff(ow.getBrokerOffset(), ow.getConsumerOffset());

                progress.add(QueueProgressVO.builder()
                        .broker(mq.getBrokerName())
                        .queueId(mq.getQueueId())
                        .brokerOffset(ow.getBrokerOffset())
                        .consumerOffset(ow.getConsumerOffset())
                        .diffTotal(diff)
                        .build());
            }

            progress.sort((a, b) -> {
                int cmp = a.getBroker().compareToIgnoreCase(b.getBroker());
                return cmp != 0 ? cmp : Integer.compare(a.getQueueId(), b.getQueueId());
            });
            return progress;
        } catch (Exception e) {
            log.warn("Failed to get progress for group {}: {}", name, e.getMessage());
            throw new BusinessException(502, "Failed to get progress for group " + name + ": " + e.getMessage());
        }
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String name) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, admin -> getGroupSubscriptions(admin, name));
        }
        if (!hasAdmin()) {
            return Collections.emptyList();
        }
        return adminExecute(admin -> getGroupSubscriptions(admin, name));
    }

    private List<SubscriptionEntryVO> getGroupSubscriptions(MQAdminExt admin, String name) {
        try {
            ConsumerConnection conn = admin.examineConsumerConnectionInfo(name);
            if (conn == null || conn.getSubscriptionTable() == null) {
                return Collections.emptyList();
            }

            List<SubscriptionEntryVO> subscriptions = new ArrayList<>();
            for (Map.Entry<String, SubscriptionData> entry : conn.getSubscriptionTable().entrySet()) {
                SubscriptionData sd = entry.getValue();
                subscriptions.add(SubscriptionEntryVO.builder()
                        .topic(sd.getTopic())
                        .expression(sd.getSubString())
                        .type(sd.getExpressionType())
                        .filterMode(filterMode(sd.getExpressionType()))
                        .build());
            }
            return subscriptions;
        } catch (Exception e) {
            log.warn("Failed to get subscriptions for group {}: {}", name, e.getMessage());
            throw new BusinessException(502,
                    "Failed to get subscriptions for group " + name + ": " + e.getMessage());
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Resolves the lag for a single queue without clamping the broker's {@code -1} "unknown"
     * sentinel to zero. A negative raw diff (typical for RocketMQ 5.0 gRPC consumers) is passed
     * through {@link ConsumerLagResolver} so the unknown state stays visible.
     */
    private long resolveDiff(long brokerOffset, long consumerOffset) {
        return ConsumerLagResolver.resolve(brokerOffset - consumerOffset, proxyStatsProvider);
    }
    private String filterMode(String expressionType) {
        if ("SQL92".equals(expressionType)) {
            return "SQL";
        }
        if ("CLASS_FILTER".equals(expressionType)) {
            return "CLASS_FILTER";
        }
        return "TAG";
    }

    private boolean isSystemTopic(String topicName, Set<String> brokerNames) {
        return SystemTopicFilter.isSystem(topicName, brokerNames);
    }

    private TopicPerm mapPerm(int perm) {
        // RocketMQ perm: 6=RW, 4=R, 2=W
        if (perm == 6) {
            return TopicPerm.RW;
        } else if (perm == 4) {
            return TopicPerm.RO;
        } else if (perm == 2) {
            return TopicPerm.WO;
        }
        return TopicPerm.RW;
    }
}
