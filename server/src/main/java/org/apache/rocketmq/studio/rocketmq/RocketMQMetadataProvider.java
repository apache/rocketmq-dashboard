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
package org.apache.rocketmq.studio.rocketmq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.MetadataProvider;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
 * consumer, progress and subscription queries stay live through DefaultMQAdminExt, so a record
 * without a broker route surfaces as an empty route list instead of being hidden.
 */
@Service
@Primary
public class RocketMQMetadataProvider implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(RocketMQMetadataProvider.class);

    private static final Set<String> SYSTEM_TOPIC_PREFIXES = Set.of(
            "RMQ_SYS_", "SCHEDULE_TOPIC_", "%RETRY%", "%DLQ%", "CID_"
    );

    private static final Set<String> SYSTEM_TOPICS = Set.of(
            "TBW102", "SELF_TEST_TOPIC", "DefaultCluster", "OFFSET_MOVED_EVENT",
            "broker", "SCHEDULE_TOPIC_XXXX", "RMQ_SYS_TRANS_HALF_TOPIC",
            "RMQ_SYS_TRACE_TOPIC", "RMQ_SYS_TRANS_OP_HALF_TOPIC"
    );

    private final DefaultMQAdminExt adminExt;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;

    @Autowired
    public RocketMQMetadataProvider(@Autowired(required = false) DefaultMQAdminExt adminExt,
                                    RmqTopicMapper topicMapper,
                                    RmqGroupMapper groupMapper) {
        this.adminExt = adminExt;
        this.topicMapper = topicMapper;
        this.groupMapper = groupMapper;
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
        LambdaQueryWrapper<RmqGroup> query = new LambdaQueryWrapper<RmqGroup>()
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
            vo.setRetryMaxTimes(entity.getMaxRetry() == null ? 0 : entity.getMaxRetry());
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setUpdatedAt(entity.getUpdatedAt());

            if (adminExt != null) {
                enrichGroupWithConnectionInfo(vo, entity.getName());
            }
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

    @Override
    public List<BrokerRouteVO> getTopicRoutes(String name) {
        if (adminExt == null) {
            return Collections.emptyList();
        }

        try {
            TopicRouteData routeData = adminExt.examineTopicRouteInfo(name);
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
            return Collections.emptyList();
        }
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String name) {
        if (adminExt == null) {
            return Collections.emptyList();
        }

        try {
            // Ask the broker who consumes this topic instead of scanning every subscription
            // group, which floods the result with system groups.
            GroupList groupList = adminExt.queryTopicConsumeByWho(name);
            Set<String> subscribingGroups = new HashSet<>();
            if (groupList != null && groupList.getGroupList() != null) {
                for (String group : groupList.getGroupList()) {
                    if (!isSystemConsumerGroup(group)) {
                        subscribingGroups.add(group);
                    }
                }
            }

            List<TopicConsumerVO> consumers = new ArrayList<>();
            for (String group : subscribingGroups) {
                try {
                    ConsumeStats stats = adminExt.examineConsumeStats(group, name);
                    long diffTotal = 0;
                    double consumeTps = 0;
                    if (stats != null && stats.getOffsetTable() != null) {
                        for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                            OffsetWrapper ow = entry.getValue();
                            diffTotal += Math.max(0, ow.getBrokerOffset() - ow.getConsumerOffset());
                        }
                        consumeTps = stats.getConsumeTps();
                    }

                    ConsumeType consumeType = ConsumeType.CLUSTERING;
                    String messageModel = "CLUSTERING";
                    try {
                        ConsumerConnection conn = adminExt.examineConsumerConnectionInfo(group);
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
                            .build());
                }
            }
            consumers.sort((a, b) -> a.getGroup().compareToIgnoreCase(b.getGroup()));
            return consumers;
        } catch (Exception e) {
            log.warn("Failed to get consumers for topic {}: {}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isSystemConsumerGroup(String group) {
        if (group == null || group.isEmpty()) {
            return true;
        }
        return group.startsWith("%RETRY%")
                || group.startsWith("%DLQ%")
                || group.startsWith("CID_RMQ_SYS_")
                || group.startsWith("CID_ONS_")
                || group.startsWith("TOOLS_CONSUMER")
                || group.startsWith("FILTERSRV_CONSUMER");
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String name) {
        if (adminExt == null) {
            return Collections.emptyList();
        }

        try {
            ConsumeStats stats = adminExt.examineConsumeStats(name);
            if (stats == null || stats.getOffsetTable() == null) {
                return Collections.emptyList();
            }

            List<QueueProgressVO> progress = new ArrayList<>();
            for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                MessageQueue mq = entry.getKey();
                OffsetWrapper ow = entry.getValue();
                long diff = Math.max(0, ow.getBrokerOffset() - ow.getConsumerOffset());

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
            return Collections.emptyList();
        }
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String name) {
        if (adminExt == null) {
            return Collections.emptyList();
        }

        try {
            ConsumerConnection conn = adminExt.examineConsumerConnectionInfo(name);
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
            return Collections.emptyList();
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private TopicVO buildTopicVO(String topicName) {
        try {
            TopicRouteData routeData = adminExt.examineTopicRouteInfo(topicName);
            if (routeData == null || routeData.getQueueDatas() == null || routeData.getQueueDatas().isEmpty()) {
                // Topic exists in nameserver but has no route data
                TopicVO vo = new TopicVO();
                vo.setId(topicName);
                vo.setName(topicName);
                return vo;
            }

            QueueData firstQd = routeData.getQueueDatas().get(0);
            TopicVO vo = new TopicVO();
            vo.setId(topicName);
            vo.setName(topicName);
            vo.setWriteQueues(firstQd.getWriteQueueNums());
            vo.setReadQueues(firstQd.getReadQueueNums());
            vo.setPerm(mapPerm(firstQd.getPerm()));
            vo.setType(inferTopicType(topicName));
            return vo;
        } catch (Exception e) {
            log.debug("Failed to get route for topic {}: {}", topicName, e.getMessage());
            TopicVO vo = new TopicVO();
            vo.setId(topicName);
            vo.setName(topicName);
            return vo;
        }
    }

    private void enrichGroupWithConnectionInfo(ConsumerGroupVO vo, String groupName) {
        try {
            ConsumerConnection conn = adminExt.examineConsumerConnectionInfo(groupName);
            if (conn != null) {
                if (conn.getConnectionSet() != null) {
                    vo.setOnlineInstances(conn.getConnectionSet().size());
                }
                if (conn.getSubscriptionTable() != null) {
                    vo.setSubscribedTopics(new ArrayList<>(conn.getSubscriptionTable().keySet()));
                }
            }
        } catch (Exception ignored) {
            // Group may be offline, that's fine
        }

        // Try to get lag info
        try {
            ConsumeStats stats = adminExt.examineConsumeStats(groupName);
            if (stats != null && stats.getOffsetTable() != null) {
                long totalLag = 0;
                for (OffsetWrapper ow : stats.getOffsetTable().values()) {
                    totalLag += Math.max(0, ow.getBrokerOffset() - ow.getConsumerOffset());
                }
                vo.setTotalLag(totalLag);
            }
        } catch (Exception ignored) {
            // No stats available
        }
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

    private Set<String> getBrokerNames() {
        Set<String> names = new HashSet<>();
        try {
            ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
            if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
                names.addAll(clusterInfo.getBrokerAddrTable().keySet());
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private boolean isSystemTopic(String topicName, Set<String> brokerNames) {
        if (SYSTEM_TOPICS.contains(topicName)) {
            return true;
        }
        for (String prefix : SYSTEM_TOPIC_PREFIXES) {
            if (topicName.startsWith(prefix)) {
                return true;
            }
        }
        // Skip topics that match broker names
        return brokerNames.contains(topicName);
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

    private org.apache.rocketmq.studio.common.domain.enums.TopicType inferTopicType(String topicName) {
        if (topicName.contains("TRANS") || topicName.contains("trans")) {
            return org.apache.rocketmq.studio.common.domain.enums.TopicType.TRANSACTION;
        }
        if (topicName.contains("DELAY") || topicName.contains("delay") || topicName.contains("SCHEDULE")) {
            return org.apache.rocketmq.studio.common.domain.enums.TopicType.DELAY;
        }
        if (topicName.contains("FIFO") || topicName.contains("fifo") || topicName.contains("ORDER")) {
            return org.apache.rocketmq.studio.common.domain.enums.TopicType.FIFO;
        }
        return org.apache.rocketmq.studio.common.domain.enums.TopicType.NORMAL;
    }
}
