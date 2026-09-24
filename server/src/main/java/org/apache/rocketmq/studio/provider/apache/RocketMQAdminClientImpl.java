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

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.TopicAttributes;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.util.MqResponseCodes;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Kind;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Resource;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsCommand;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real AdminClient implementation backed by the RocketMQ admin API.
 * Provides topic CRUD, message sending, consumer group CRUD, and offset reset.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQAdminClientImpl implements AdminClient {

    private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024; // 4 MB default broker limit
    private static final long RESET_OFFSET_PREVIEW_TIMEOUT_MILLIS = 3_000L;
    private static final String RISK_INFO = "INFO";
    private static final String RISK_WARNING = "WARNING";
    private static final String RISK_ERROR = "ERROR";

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;
    private final AuditService auditService;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final MqClientPool clientPool;
    private final ResourceOwnershipGuard ownershipGuard;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProxyConsumerResolver proxyConsumerResolver;

    @Override
    public TopicVO getTopic(String name) {
        return adminFactory.execute(namesrvAddr(), null, admin -> {
            try {
                var routeData = admin.examineTopicRouteInfo(name);
                if (routeData == null || routeData.getQueueDatas() == null || routeData.getQueueDatas().isEmpty()) {
                    throw new BusinessException(404, "Topic not found: " + name);
                }
                var qd = routeData.getQueueDatas().stream()
                        .filter(queueData -> queueData != null)
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(404, "Topic not found: " + name));
                TopicVO vo = new TopicVO();
                vo.setName(name);
                vo.setWriteQueues(qd.getWriteQueueNums());
                vo.setReadQueues(qd.getReadQueueNums());
                return vo;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(500, "Failed to get topic: " + e.getMessage());
            }
        });
    }

    @Override
    public ConsumerGroupVO getConsumerGroup(String instanceId, String name) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, admin -> getConsumerGroup(admin, instanceId, name));
        }
        return adminFactory.execute(namesrvAddr(), null, admin -> getConsumerGroup(admin, null, name));
    }

    private ConsumerGroupVO getConsumerGroup(MQAdminExt admin, String instanceId, String name) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName(name);
        try {
            var conn = admin.examineConsumerConnectionInfo(name);
            if (conn != null) {
                if (conn.getConnectionSet() != null) {
                    vo.setInstances(ConsumerConnections.toInstances(conn));
                    vo.setOnlineInstances(vo.getInstances().size());
                }
                if (conn.getSubscriptionTable() != null) {
                    vo.setSubscribedTopics(new ArrayList<>(conn.getSubscriptionTable().keySet()));
                }
            }
        } catch (Exception exception) {
            if (isConsumerNotOnline(exception)) {
                log.debug("Consumer group {} is offline on broker", name);
                applyProxyFallback(instanceId, name, vo);
            } else {
                throw new BusinessException(502, "Failed to get consumer group: " + exception.getMessage());
            }
        }
        fillConsumeStats(admin, vo, name);
        return vo;
    }

    /**
     * Fills totalLag and delaySeconds from the broker consume stats. Proxy-connected groups
     * still maintain broker-side offset tables (the proxy forwards offset updates), so this
     * works even when the connection lookup reports the group offline; groups without any
     * offset table (e.g. pure POP) simply keep the zero defaults. A queue whose offsets
     * resolve to the unknown sentinel marks totalLag unknown instead of summing it away as
     * zero lag.
     *
     * <p>delaySeconds is derived from the newest consumed-message timestamp (the consumption
     * frontier). Using the oldest timestamp is misleading for POP groups, where untouched
     * queues keep frozen stale timestamps.
     */
    private void fillConsumeStats(MQAdminExt admin, ConsumerGroupVO vo, String name) {
        try {
            ConsumeStats stats = admin.examineConsumeStats(name);
            if (stats == null) {
                return;
            }
            vo.setConsumeStatsAvailable(true);
            if (stats.getOffsetTable() == null || stats.getOffsetTable().isEmpty()) {
                return;
            }
            long totalLag = 0;
            boolean lagUnknown = false;
            long newestConsumedTimestamp = 0;
            for (OffsetWrapper wrapper : stats.getOffsetTable().values()) {
                long queueDiff = ConsumerLagResolver.resolve(
                        wrapper.getBrokerOffset() - wrapper.getConsumerOffset(), null);
                if (queueDiff == ConsumerLagResolver.UNKNOWN) {
                    // a queue with the -1 sentinel (5.0 gRPC consumers) must not be summed
                    // away as zero lag; report the whole total as unknown instead
                    lagUnknown = true;
                } else {
                    totalLag += queueDiff;
                }
                long lastTimestamp = wrapper.getLastTimestamp();
                if (lastTimestamp > newestConsumedTimestamp) {
                    newestConsumedTimestamp = lastTimestamp;
                }
            }
            vo.setTotalLag(lagUnknown ? ConsumerLagResolver.UNKNOWN : totalLag);
            if (newestConsumedTimestamp > 0) {
                long delaySeconds = (System.currentTimeMillis() - newestConsumedTimestamp) / 1000;
                vo.setDelaySeconds((int) Math.max(delaySeconds, 0));
                vo.setConsumptionTimestampAvailable(true);
            }
        } catch (Exception e) {
            log.debug("No consume stats for group {}: {}", name, e.getMessage());
        }
    }

    /**
     * Groups whose clients connect through a proxy are invisible to broker-side stats; the
     * proxy's own client manager still knows them, so fill the offline detail from there.
     */
    private void applyProxyFallback(String instanceId, String group, ConsumerGroupVO vo) {
        if (proxyConsumerResolver == null) {
            return;
        }
        ProxyConsumerResolver.ConsumerConnectionResolution resolution =
                proxyConsumerResolver.resolveConsumerConnectionStatus(instanceId, group);
        if (!resolution.available()) {
            vo.setOnlineInstances(-1);
            return;
        }
        ConsumerConnection viaProxy = resolution.connection();
        if (viaProxy == null) {
            return;
        }
        if (viaProxy.getConnectionSet() != null) {
            vo.setInstances(ConsumerConnections.toInstances(viaProxy));
            vo.setOnlineInstances(vo.getInstances().size());
        }
        if (viaProxy.getSubscriptionTable() != null) {
            vo.setSubscribedTopics(new ArrayList<>(viaProxy.getSubscriptionTable().keySet()));
        }
    }

    /**
     * examineConsumerConnectionInfo wraps the broker-side CODE 206 into an MQClientException
     * whose response code is lost (the code only survives in the message text), so match on
     * both the typed code and the message.
     */
    private static boolean isConsumerNotOnline(Exception exception) {
        if (exception instanceof MQBrokerException brokerException
                && brokerException.getResponseCode() == ResponseCode.CONSUMER_NOT_ONLINE) {
            return true;
        }
        // rocketmq-tools locates a group through the %RETRY%<group> topic route before any
        // broker call, so a group that never connected fails with TOPIC_NOT_EXIST for that
        // retry topic, and a broadcast group with an empty offset table fails with
        // BROADCAST_CONSUMPTION. Both mean "no live data", not a lookup failure.
        String message = exception.getMessage();
        if (MqResponseCodes.hasResponseCode(exception, ResponseCode.BROADCAST_CONSUMPTION)
                || MqResponseCodes.hasResponseCode(exception, ResponseCode.TOPIC_NOT_EXIST)
                        && message != null && message.contains("%RETRY%")) {
            return true;
        }
        return message != null && (message.contains("not online") || message.contains("CODE: 206"));
    }

    @Override
    public TopicVO createTopic(String instanceId, TopicVO topic) {
        return createTopic(instanceId, topic, false);
    }

    @Override
    public TopicVO importTopic(String instanceId, TopicVO topic) {
        return createTopic(instanceId, topic, true);
    }

    private TopicVO createTopic(String instanceId, TopicVO topic, boolean importing) {
        String target = ownershipGuard.requireInstance(instanceId).getName();
        if (topic == null) {
            throw new BusinessException(400, "Topic request is required");
        }
        topic.setInstanceId(target);
        topic.setName(ResourceOwnershipGuard.requireText(topic.getName(), "topicName"));
        String topicName = topic.getName();
        int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;
        int readQueues = topic.getReadQueues() > 0 ? topic.getReadQueues() : 8;

        return executeResourceWrite(target, Kind.TOPIC, topicName, true, topic.getType(), (admin, clusterName) -> {
            try {
                RmqTopic existing = topicMapper.selectOne(new LambdaQueryWrapper<RmqTopic>()
                        .eq(RmqTopic::getName, topicName));
                TopicPerm effectivePerm = topic.getPerm() != null
                        ? topic.getPerm()
                        : existing == null ? TopicPerm.RW : fromRocketMQPerm(existing.getPerm());
                Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(500, "No broker available to create topic");
                }

                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setWriteQueueNums(writeQueues);
                topicConfig.setReadQueueNums(readQueues);
                topicConfig.setPerm(toRocketMQPerm(effectivePerm));
                applyTopicType(topicConfig, topic.getType());

                boolean physicalExists = importing && verifyTopicImport(admin, brokerAddrs, topicConfig);
                if (!physicalExists) {
                    for (String addr : brokerAddrs) {
                        admin.createAndUpdateTopicConfig(addr, topicConfig);
                    }
                }

                // The global reservation was already committed independently before the RPC; here we only update the unique record.
                RmqTopic entity = topicMapper.selectOne(new LambdaQueryWrapper<RmqTopic>()
                        .eq(RmqTopic::getName, topicName));
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new RmqTopic();
                    entity.setName(topicName);
                    entity.setClusterId(clusterName);
                    entity.setInstanceId(target);
                    entity.setGmtCreate(LocalDateTime.now());
                }
                if (topic.getType() != null) {
                    entity.setTopicType(topic.getType().name());
                } else if (isNew) {
                    entity.setTopicType(TopicType.NORMAL.name());
                }
                entity.setReadQueueNums(readQueues);
                entity.setWriteQueueNums(writeQueues);
                entity.setPerm(topicConfig.getPerm());
                if (StringUtils.hasText(topic.getRemark())) {
                    entity.setRemark(topic.getRemark());
                }
                entity.setStatus("ACTIVE");
                entity.setGmtModified(LocalDateTime.now());
                if (isNew) {
                    topicMapper.insert(entity);
                } else {
                    topicMapper.updateById(entity);
                }

                recordAudit("CREATE_TOPIC", topicName,
                        "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

                topic.setId(entity.getId());
                topic.setWriteQueues(writeQueues);
                topic.setReadQueues(readQueues);
                return topic;
            } catch (BusinessException e) {
                recordAudit("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw classifyBrokerFailure(e, "create topic");
            }
        });
    }

    @Override
    public TopicVO updateTopic(String instanceId, TopicVO topic) {
        String target = ownershipGuard.requireInstance(instanceId).getName();
        topic.setInstanceId(target);
        topic.setName(ResourceOwnershipGuard.requireText(topic.getName(), "topicName"));
        String topicName = topic.getName();

        return executeResourceWrite(target, Kind.TOPIC, topicName, false, topic.getType(), (admin, clusterName) -> {
            try {
                RmqTopic existing = topicMapper.selectOne(new LambdaQueryWrapper<RmqTopic>()
                        .eq(RmqTopic::getName, topicName));
                if (existing == null) {
                    throw new BusinessException(404, "Topic not found: " + topicName);
                }
                // Preserve the existing queue counts when the update request does not change them,
                // matching the perm semantics below; defaulting to 8 would silently resize the
                // topic on partial updates (e.g. perm or remark only).
                int writeQueues = topic.getWriteQueues() > 0
                        ? topic.getWriteQueues()
                        : existing != null && existing.getWriteQueueNums() != null
                                && existing.getWriteQueueNums() > 0 ? existing.getWriteQueueNums() : 8;
                int readQueues = topic.getReadQueues() > 0
                        ? topic.getReadQueues()
                        : existing != null && existing.getReadQueueNums() != null
                                && existing.getReadQueueNums() > 0 ? existing.getReadQueueNums() : 8;
                TopicPerm effectivePerm = topic.getPerm() != null
                        ? topic.getPerm()
                        : existing == null ? TopicPerm.RW : fromRocketMQPerm(existing.getPerm());
                Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(500, "No broker available to update topic");
                }

                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setWriteQueueNums(writeQueues);
                topicConfig.setReadQueueNums(readQueues);
                topicConfig.setPerm(toRocketMQPerm(effectivePerm));
                applyTopicType(topicConfig, topic.getType());

                for (String addr : brokerAddrs) {
                    admin.createAndUpdateTopicConfig(addr, topicConfig);
                }

                // Update DB record
                if (existing != null) {
                    existing.setWriteQueueNums(writeQueues);
                    existing.setReadQueueNums(readQueues);
                    existing.setPerm(topicConfig.getPerm());
                    if (topic.getType() != null) {
                        existing.setTopicType(topic.getType().name());
                    }
                    // A null remark was not submitted and keeps the stored value; a submitted blank
                    // remark is an explicit clear, so it must persist as an absent remark.
                    boolean clearRemark = topic.getRemark() != null && !StringUtils.hasText(topic.getRemark());
                    if (topic.getRemark() != null) {
                        existing.setRemark(clearRemark ? null : topic.getRemark());
                    }
                    existing.setGmtModified(LocalDateTime.now());
                    topicMapper.updateById(existing);
                    if (clearRemark) {
                        // updateById omits null entity fields, so the cleared remark has to be
                        // assigned explicitly instead of silently retaining the stored value.
                        topicMapper.update(null, new UpdateWrapper<RmqTopic>()
                                .eq("id", existing.getId())
                                .set("remark", null));
                    }
                }

                recordAudit("UPDATE_TOPIC", topicName,
                        "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

                topic.setId(existing == null ? null : existing.getId());
                topic.setWriteQueues(writeQueues);
                topic.setReadQueues(readQueues);
                if (existing != null) {
                    // Report the persisted remark so a clear or an omitted remark cannot be
                    // mistaken for a value the update did not write.
                    topic.setRemark(existing.getRemark());
                }
                return topic;
            } catch (BusinessException e) {
                recordAudit("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw classifyBrokerFailure(e, "update topic");
            }
        });
    }

    private void applyTopicType(TopicConfig topicConfig, TopicType topicType) {
        if (topicType == null) {
            return;
        }
        topicConfig.setAttributes(Map.of(
                "+" + TopicAttributes.TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName(),
                topicType.name()));
    }

    @Override
    public void deleteTopic(String instanceId, String rawName) {
        String name = ResourceOwnershipGuard.requireText(rawName, "topicName");
        InstanceVO instance = ownershipGuard.requireInstance(instanceId);
        Resource resource = ownershipGuard.topicResource(name);
        if (resource.kind() != Kind.TOPIC) {
            ownershipGuard.withOwned(instance, List.of(resource), () -> executeForInstance(instance.getName(), admin -> {
                var owner = ownershipGuard.check(instance, resource, true);
                var target = ApacheWriteTargetResolver.resolve(admin, instance, owner.clusterId());
                deletePhysicalTopic(admin, instance.getName(), name, target.cluster(), target.masters());
                return null;
            }));
            return;
        }
        executeResourceWrite(instance.getName(), Kind.TOPIC, name, false, null, (admin, clusterName) -> {
            try {
                Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);

                deletePhysicalTopic(admin, instance.getName(), name, clusterName, brokerAddrs);
                topicMapper.delete(new LambdaQueryWrapper<RmqTopic>().eq(RmqTopic::getName, name));

                recordAudit("DELETE_TOPIC", name, "", "SUCCESS");
                return null;
            } catch (BusinessException e) {
                recordAudit("DELETE_TOPIC", name, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("DELETE_TOPIC", name, e.getMessage(), "FAILED");
                throw classifyBrokerFailure(e, "delete topic");
            }
        });
    }

    @Override
    public SendMessageVO sendMessage(SendMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Send message request is required");
        }
        ResourceOwnershipGuard.requireText(request.getInstanceId(), "instanceId");
        validateMessageSize(request);
        InstanceVO instance = ownershipGuard.requireInstance(request.getInstanceId());
        request.setInstanceId(instance.getName());
        request.setTopic(ResourceOwnershipGuard.requireText(request.getTopic(), "topicName"));
        Resource resource = ownershipGuard.topicResource(request.getTopic());
        ownershipGuard.check(instance, resource, true);
        return ownershipGuard.withOwned(instance, List.of(resource), () -> {
            executeForInstance(instance.getName(), admin -> {
                var owner = ownershipGuard.check(instance, resource, true);
                var target = ApacheWriteTargetResolver.resolve(admin, instance, owner.clusterId());
                ApacheWriteTargetResolver.requireTopicRoute(admin, target, request.getTopic());
                return null;
            });
            return sendOwnedMessage(request);
        });
    }

    private SendMessageVO sendOwnedMessage(SendMessageDTO request) {
        String topic = request.getTopic();
        String tag = request.getTag() != null ? request.getTag() : "";
        String key = request.getKey() != null ? request.getKey() : "";
        String body = request.getBody() != null ? request.getBody() : "";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        MqClientPool.ClientAction<DefaultMQProducer, SendMessageVO> sendAction = producer -> {
            Message msg = new Message(topic, bodyBytes);
            if (StringUtils.hasText(tag)) {
                msg.setTags(tag);
            }
            if (StringUtils.hasText(key)) {
                msg.setKeys(key);
            }

            // Custom properties must skip system-reserved keys (KEYS/TAGS/UNIQ_KEY/WAIT/
            // TIMER_*/RETRY_TOPIC/...): putUserProperty rejects them with
            // "The Property<X> is used by system", which is how redelivering a keyed
            // message used to fail when the source properties were copied verbatim.
            if (request.getProperties() != null) {
                for (Map.Entry<String, String> entry : request.getProperties().entrySet()) {
                    if (isSystemReservedProperty(entry.getKey())) {
                        log.debug("Skipping system-reserved message property: {}", entry.getKey());
                        continue;
                    }
                    msg.putUserProperty(entry.getKey(), entry.getValue());
                }
            }

            // Timer delivery is a system property; it must be set through the typed API,
            // never forwarded via user properties.
            if (request.getDeliveryTimestamp() != null) {
                msg.setDeliverTimeMs(request.getDeliveryTimestamp());
            }

            SendResult sendResult = StringUtils.hasText(request.getMessageGroup())
                    ? producer.send(msg, (queues, message, arg) ->
                            queues.get(Math.floorMod(arg.hashCode(), queues.size())), request.getMessageGroup())
                    : producer.send(msg);
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                String status = sendResult == null ? "null" : String.valueOf(sendResult.getSendStatus());
                throw new BusinessException(502, "Message send did not succeed: " + status);
            }

            // The message is already delivered by now; an audit write failure must not turn a
            // successful send into an error, or callers would retry and duplicate the message.
            recordAudit("SEND_MESSAGE", topic,
                    "tag=" + tag + ", key=" + key + ", msgId=" + sendResult.getMsgId(), "SUCCESS");

            return SendMessageVO.builder()
                    .msgId(sendResult.getMsgId())
                    .sendTime(System.currentTimeMillis())
                    .offsetMsgId(sendResult.getOffsetMsgId())
                    .build();
        };
        try {
            return runtimeAdminClientResolver.executeProducer(request.getInstanceId(), sendAction);
        } catch (BusinessException e) {
            recordAudit("SEND_MESSAGE", request.getTopic(), e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("SEND_MESSAGE", request.getTopic(), e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to send message: " + e.getMessage());
        }
    }

    private void validateMessageSize(SendMessageDTO request) {
        int size = request.getBody() == null ? 0 : request.getBody().getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_MESSAGE_SIZE) {
            String message = "Message body size " + size
                    + " exceeds the maximum of " + MAX_MESSAGE_SIZE + " bytes";
            recordAudit("SEND_MESSAGE", request.getTopic(), message, "FAILED");
            throw new BusinessException(400, message);
        }
    }

    private static boolean isSystemReservedProperty(String key) {
        return key == null
                || MessageConst.STRING_HASH_SET.contains(key)
                || key.startsWith("%RETRY%")
                || key.startsWith("%DLQ%");
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        return createConsumerGroup(group, false);
    }

    @Override
    public ConsumerGroupVO importConsumerGroup(ConsumerGroupVO group) {
        return createConsumerGroup(group, true);
    }

    private ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group, boolean importing) {
        String instanceId = ownershipGuard.requireInstance(group == null ? null : group.getInstanceId()).getName();
        group.setInstanceId(instanceId);
        group.setName(ResourceOwnershipGuard.requireText(group.getName(), "groupName"));
        return executeResourceWrite(instanceId, Kind.GROUP, group.getName(), true, null,
                (admin, cluster) -> createConsumerGroup(admin, group, cluster, importing));
    }

    @Override
    public ConsumerGroupVO updateConsumerGroup(ConsumerGroupVO group) {
        String instanceId = ownershipGuard.requireInstance(group == null ? null : group.getInstanceId()).getName();
        group.setInstanceId(instanceId);
        group.setName(ResourceOwnershipGuard.requireText(group.getName(), "groupName"));
        return executeResourceWrite(instanceId, Kind.GROUP, group.getName(), false, null, (admin, clusterName) -> {
            String groupName = group.getName();
            int totalBrokers = 0;
            int updatedBrokers = 0;
            try {
                Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(502, "No broker available to update consumer group");
                }
                totalBrokers = brokerAddrs.size();
                // Read every broker before writing so missing or unreadable configurations
                // cannot cause a partial update or fall back to creation defaults.
                Map<String, SubscriptionGroupConfig> configs = new LinkedHashMap<>();
                for (String addr : brokerAddrs) {
                    SubscriptionGroupConfig config = admin.examineSubscriptionGroupConfig(addr, groupName);
                    if (config == null) {
                        throw new BusinessException(404, "Consumer group not found on broker " + addr + ": " + groupName);
                    }
                    configs.put(addr, config);
                }
                for (Map.Entry<String, SubscriptionGroupConfig> entry : configs.entrySet()) {
                    // Preserve each broker's other settings; zero is an explicit retry limit.
                    SubscriptionGroupConfig config = entry.getValue();
                    config.setRetryMaxTimes(group.getRetryMaxTimes());
                    admin.createAndUpdateSubscriptionGroupConfig(entry.getKey(), config);
                    updatedBrokers++;
                }

                persistConsumerGroup(group, clusterName, group.getRetryMaxTimes());
                recordAudit("UPDATE_GROUP", groupName, "retryMaxTimes=" + group.getRetryMaxTimes()
                        + ", brokersUpdated=" + updatedBrokers + "/" + totalBrokers, "SUCCESS");
                return group;
            } catch (BusinessException e) {
                recordAudit("UPDATE_GROUP", groupName, "updated " + updatedBrokers + "/" + totalBrokers
                        + " brokers before failure: " + e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("UPDATE_GROUP", groupName, "updated " + updatedBrokers + "/" + totalBrokers
                        + " brokers before failure: " + e.getMessage(), "FAILED");
                throw classifyBrokerFailure(e, "update consumer group");
            }
        });
    }

    @Override
    public ConsumerGroupSettingsVO getConsumerGroupSettings(String instanceId, String name) {
        return executeForInstance(instanceId, admin -> {
            try {
                InstanceVO instance = ownershipGuard.requireInstance(instanceId);
                var owner = ownershipGuard.check(instance, new Resource(Kind.GROUP, name), true);
                Set<String> brokerAddrs = ApacheWriteTargetResolver.resolve(admin, instance, owner.clusterId()).masters();
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(502, "No broker available to get consumer group settings");
                }
                SubscriptionGroupConfig config = admin.examineSubscriptionGroupConfig(brokerAddrs.iterator().next(), name);
                if (config == null) {
                    throw new BusinessException(404, "Consumer group not found: " + name);
                }
                return ConsumerGroupSettingsVO.builder().groupName(name).retryQueueNums(config.getRetryQueueNums())
                        .retryMaxTimes(config.getRetryMaxTimes()).consumeEnable(config.isConsumeEnable())
                        .consumeMessageOrderly(config.isConsumeMessageOrderly())
                        .consumeBroadcastEnable(config.isConsumeBroadcastEnable()).build();
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw classifyBrokerFailure(exception, "get consumer group settings");
            }
        });
    }

    @Override
    public ConsumerGroupSettingsVO updateConsumerGroupSettings(String instanceId, String rawName,
                                                                 ConsumerGroupSettingsCommand command) {
        String name = ResourceOwnershipGuard.requireText(rawName, "groupName");
        return executeResourceWrite(instanceId, Kind.GROUP, name, false, null, (admin, clusterName) -> {
            int totalBrokers = 0;
            int updatedBrokers = 0;
            try {
                Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(502, "No broker available to update consumer group settings");
                }
                totalBrokers = brokerAddrs.size();
                Map<String, SubscriptionGroupConfig> configsByBroker = new LinkedHashMap<>();
                for (String brokerAddr : brokerAddrs) {
                    SubscriptionGroupConfig config = admin.examineSubscriptionGroupConfig(brokerAddr, name);
                    if (config == null) {
                        throw new BusinessException(404, "Consumer group not found: " + name);
                    }
                    configsByBroker.put(brokerAddr, config);
                }
                SubscriptionGroupConfig applied = null;
                for (Map.Entry<String, SubscriptionGroupConfig> entry : configsByBroker.entrySet()) {
                    String brokerAddr = entry.getKey();
                    SubscriptionGroupConfig config = entry.getValue();
                    config.setRetryQueueNums(command.retryQueueNums());
                    config.setRetryMaxTimes(command.retryMaxTimes());
                    if (command.consumeEnable() != null) {
                        config.setConsumeEnable(command.consumeEnable());
                    }
                    if (command.consumeMessageOrderly() != null) {
                        config.setConsumeMessageOrderly(command.consumeMessageOrderly());
                    }
                    if (command.consumeBroadcastEnable() != null) {
                        config.setConsumeBroadcastEnable(command.consumeBroadcastEnable());
                    }
                    admin.createAndUpdateSubscriptionGroupConfig(brokerAddr, config);
                    updatedBrokers++;
                    if (applied == null) {
                        applied = config;
                    }
                }
                RmqGroup group = groupMapper.selectOne(new LambdaQueryWrapper<RmqGroup>()
                        .eq(RmqGroup::getName, name));
                if (group != null) {
                    group.setMaxRetry(command.retryMaxTimes());
                    group.setGmtModified(LocalDateTime.now());
                    groupMapper.updateById(group);
                }
                recordAudit("UPDATE_GROUP_SETTINGS", name, "retryQueueNums=" + command.retryQueueNums()
                        + ", retryMaxTimes=" + command.retryMaxTimes()
                        + ", consumeEnable=" + describeSwitch(command.consumeEnable())
                        + ", consumeMessageOrderly=" + describeSwitch(command.consumeMessageOrderly())
                        + ", consumeBroadcastEnable=" + describeSwitch(command.consumeBroadcastEnable())
                        + ", brokersUpdated=" + updatedBrokers + "/" + totalBrokers, "SUCCESS");
                return ConsumerGroupSettingsVO.builder().groupName(name)
                        .retryQueueNums(applied.getRetryQueueNums())
                        .retryMaxTimes(applied.getRetryMaxTimes())
                        .consumeEnable(applied.isConsumeEnable())
                        .consumeMessageOrderly(applied.isConsumeMessageOrderly())
                        .consumeBroadcastEnable(applied.isConsumeBroadcastEnable())
                        .build();
            } catch (BusinessException exception) {
                recordAudit("UPDATE_GROUP_SETTINGS", name, "updated " + updatedBrokers + "/" + totalBrokers
                        + " brokers before failure: " + exception.getMessage(), "FAILED");
                throw exception;
            } catch (Exception exception) {
                recordAudit("UPDATE_GROUP_SETTINGS", name, "updated " + updatedBrokers + "/" + totalBrokers
                        + " brokers before failure: " + exception.getMessage(), "FAILED");
                throw classifyBrokerFailure(exception, "update consumer group settings");
            }
        });
    }

    private static String describeSwitch(Boolean value) {
        return value == null ? "preserved" : value.toString();
    }

    private ConsumerGroupVO createConsumerGroup(MQAdminExt admin, ConsumerGroupVO group, String groupClusterName,
                                                boolean importing) {
        String groupName = group.getName();

        try {
            Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, groupClusterName);
            if (brokerAddrs.isEmpty()) {
                throw new BusinessException(500, "No broker available to create consumer group");
            }

            SubscriptionGroupConfig config = new SubscriptionGroupConfig();
            config.setGroupName(groupName);
            config.setConsumeEnable(true);
            config.setConsumeBroadcastEnable(true);
            config.setRetryQueueNums(1);
            config.setRetryMaxTimes(group.getRetryMaxTimes() > 0 ? group.getRetryMaxTimes() : 16);

            boolean physicalExists = importing && verifyGroupImport(admin, brokerAddrs, config);
            if (!physicalExists) {
                for (String addr : brokerAddrs) {
                    admin.createAndUpdateSubscriptionGroupConfig(addr, config);
                }
            }

            persistConsumerGroup(group, groupClusterName, config.getRetryMaxTimes());
            recordAudit("CREATE_GROUP", groupName,
                    "retryMaxTimes=" + config.getRetryMaxTimes(), "SUCCESS");
            return group;
        } catch (BusinessException e) {
            recordAudit("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw classifyBrokerFailure(e, "create consumer group");
        }
    }

    private void persistConsumerGroup(ConsumerGroupVO group, String clusterName, int retryMaxTimes) {
        // Ownership is determined by the global reservation guard; a three-column filter must not mask cross-instance conflicts.
        RmqGroup entity = groupMapper.selectOne(new LambdaQueryWrapper<RmqGroup>()
                .eq(RmqGroup::getName, group.getName()));
        boolean isNewGroup = entity == null;
        if (isNewGroup) {
            entity = new RmqGroup();
            entity.setName(group.getName());
            entity.setClusterId(clusterName);
            entity.setInstanceId(group.getInstanceId());
            entity.setGmtCreate(LocalDateTime.now());
        }
        entity.setConsumeType(group.getConsumeType() != null ? group.getConsumeType().name() : "CLUSTERING");
        entity.setMessageModel(group.getSubscriptionMode() != null ? group.getSubscriptionMode().name() : "Push");
        entity.setMaxRetry(retryMaxTimes);
        entity.setStatus("ACTIVE");
        entity.setGmtModified(LocalDateTime.now());
        if (isNewGroup) {
            groupMapper.insert(entity);
        } else {
            groupMapper.updateById(entity);
        }
        group.setId(entity.getId());
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String rawName) {
        String target = ownershipGuard.requireInstance(instanceId).getName();
        String name = ResourceOwnershipGuard.requireText(rawName, "groupName");
        executeResourceWrite(target, Kind.GROUP, name, false, null, (admin, clusterName) -> {
            doDeleteConsumerGroup(target, admin, name, clusterName);
            return null;
        });
    }

    private void doDeleteConsumerGroup(String instanceId, MQAdminExt admin, String name, String clusterName) {
        try {
            Set<String> brokerAddrs = getMasterBrokerAddrsForCluster(admin, clusterName);

            resolveNameservers(instanceId);
            for (String addr : brokerAddrs) {
                admin.deleteSubscriptionGroup(addr, name, true);
            }

            // Delete derived resources while holding group ownership; on failure keep the reservation so the original instance can retry.
            deletePhysicalTopic(admin, instanceId, "%DLQ%" + name, clusterName, brokerAddrs);
            deletePhysicalTopic(admin, instanceId, "%RETRY%" + name, clusterName, brokerAddrs);
            groupMapper.delete(new LambdaQueryWrapper<RmqGroup>().eq(RmqGroup::getName, name));

            recordAudit("DELETE_GROUP", name, "", "SUCCESS");
        } catch (BusinessException e) {
            recordAudit("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw classifyBrokerFailure(e, "delete consumer group");
        }
    }

    @Override
    public ResetConsumerOffsetPreviewVO previewResetOffset(String instanceId, String name,
                                                           long timestamp, String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "topic is required for offset reset preview");
        }
        String topicName = topic.trim();
        try {
            return executeForInstance(instanceId, admin -> doPreviewResetOffset(
                    instanceId, admin, name, timestamp, topicName));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "Failed to preview reset offset: " + e.getMessage());
        }
    }

    private ResetConsumerOffsetPreviewVO doPreviewResetOffset(String instanceId, MQAdminExt admin,
                                                              String name, long timestamp, String topic) {
        try {
            ConsumeStats stats = admin.examineConsumeStats(name);
            if (stats == null || stats.getOffsetTable() == null || stats.getOffsetTable().isEmpty()) {
                return emptyResetOffsetPreview(instanceId, name, timestamp, topic,
                        "No consume offset data found for consumer group " + name);
            }

            List<ResetConsumerOffsetQueuePreviewVO> queues = new ArrayList<>();
            for (Map.Entry<MessageQueue, OffsetWrapper> entry : stats.getOffsetTable().entrySet()) {
                MessageQueue queue = entry.getKey();
                if (queue == null || !topic.equals(queue.getTopic())) {
                    continue;
                }
                queues.add(previewResetOffsetQueue(admin, queue, entry.getValue(), timestamp));
            }
            queues.sort(Comparator
                    .comparing((ResetConsumerOffsetQueuePreviewVO queue) ->
                                    queue.getBroker() == null ? "" : queue.getBroker(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(ResetConsumerOffsetQueuePreviewVO::getQueueId));
            if (queues.isEmpty()) {
                return emptyResetOffsetPreview(instanceId, name, timestamp, topic,
                        "No consume offset data found for topic " + topic);
            }

            long currentTotalLag = aggregateResetPreviewLag(queues, false);
            long projectedTotalLag = aggregateResetPreviewLag(queues, true);
            long totalOffsetDelta = queues.stream().mapToLong(ResetConsumerOffsetQueuePreviewVO::getOffsetDelta).sum();
            int rewindQueueCount = (int) queues.stream().filter(queue -> queue.getOffsetDelta() < 0).count();
            int fastForwardQueueCount = (int) queues.stream().filter(queue -> queue.getOffsetDelta() > 0).count();
            List<String> warnings = buildResetOffsetPreviewWarnings(queues, rewindQueueCount, fastForwardQueueCount);
            boolean complete = queues.stream().noneMatch(queue -> RISK_ERROR.equals(queue.getRiskLevel()));

            return ResetConsumerOffsetPreviewVO.builder()
                    .instanceId(instanceId)
                    .groupName(name)
                    .topic(topic)
                    .timestamp(timestamp)
                    .complete(complete)
                    .allowReset(complete)
                    .queueCount(queues.size())
                    .warningCount(warnings.size())
                    .rewindQueueCount(rewindQueueCount)
                    .fastForwardQueueCount(fastForwardQueueCount)
                    .currentTotalLag(currentTotalLag)
                    .projectedTotalLag(projectedTotalLag)
                    .totalOffsetDelta(totalOffsetDelta)
                    .warnings(warnings)
                    .queues(queues)
                    .build();
        } catch (Exception e) {
            if (isConsumerNotOnline(e)) {
                return emptyResetOffsetPreview(instanceId, name, timestamp, topic,
                        "Consumer group is not online and no consume offset data is available");
            }
            throw new BusinessException(500, "Failed to preview reset offset: " + e.getMessage());
        }
    }

    private ResetConsumerOffsetQueuePreviewVO previewResetOffsetQueue(MQAdminExt admin, MessageQueue queue,
                                                                       OffsetWrapper wrapper, long timestamp) {
        long brokerOffset = wrapper == null ? 0L : wrapper.getBrokerOffset();
        long consumerOffset = wrapper == null ? 0L : wrapper.getConsumerOffset();
        long currentLag = resolveLag(brokerOffset, consumerOffset);
        try {
            long minOffset = admin.minOffset(queue);
            long maxOffset = admin.maxOffset(queue);
            String brokerAddr = resolveBrokerAddress(admin, queue.getBrokerName());
            long targetOffset = admin.searchOffset(brokerAddr, queue.getTopic(), queue.getQueueId(),
                    timestamp, RESET_OFFSET_PREVIEW_TIMEOUT_MILLIS);
            targetOffset = clampOffset(targetOffset, minOffset, maxOffset);
            long offsetDelta = targetOffset - consumerOffset;
            long projectedLag = resolveLag(brokerOffset, targetOffset);
            return ResetConsumerOffsetQueuePreviewVO.builder()
                    .topic(queue.getTopic())
                    .broker(queue.getBrokerName())
                    .queueId(queue.getQueueId())
                    .minOffset(minOffset)
                    .maxOffset(maxOffset)
                    .brokerOffset(brokerOffset)
                    .consumerOffset(consumerOffset)
                    .targetOffset(targetOffset)
                    .currentLag(currentLag)
                    .projectedLag(projectedLag)
                    .offsetDelta(offsetDelta)
                    .riskLevel(resetOffsetRiskLevel(offsetDelta, targetOffset, minOffset, maxOffset))
                    .message(resetOffsetPreviewMessage(offsetDelta, targetOffset, minOffset, maxOffset))
                    .build();
        } catch (Exception e) {
            return ResetConsumerOffsetQueuePreviewVO.builder()
                    .topic(queue.getTopic())
                    .broker(queue.getBrokerName())
                    .queueId(queue.getQueueId())
                    .minOffset(-1L)
                    .maxOffset(-1L)
                    .brokerOffset(brokerOffset)
                    .consumerOffset(consumerOffset)
                    .targetOffset(consumerOffset)
                    .currentLag(currentLag)
                    .projectedLag(currentLag)
                    .offsetDelta(0L)
                    .riskLevel(RISK_ERROR)
                    .message("Failed to preview queue offset: " + e.getMessage())
                    .build();
        }
    }

    private ResetConsumerOffsetPreviewVO emptyResetOffsetPreview(String instanceId, String name,
                                                                 long timestamp, String topic, String warning) {
        return ResetConsumerOffsetPreviewVO.builder()
                .instanceId(instanceId)
                .groupName(name)
                .topic(topic)
                .timestamp(timestamp)
                .complete(false)
                .allowReset(false)
                .queueCount(0)
                .warningCount(1)
                .rewindQueueCount(0)
                .fastForwardQueueCount(0)
                .currentTotalLag(0)
                .projectedTotalLag(0)
                .totalOffsetDelta(0)
                .warnings(List.of(warning))
                .queues(List.of())
                .build();
    }

    private String resolveBrokerAddress(MQAdminExt admin, String brokerName) throws Exception {
        ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            throw new IllegalStateException("Broker cluster info is unavailable");
        }
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerName.equals(brokerData.getBrokerName())) {
                String addr = brokerData.getBrokerAddrs().get(0L);
                if (addr == null || addr.isBlank()) {
                    addr = brokerData.getBrokerAddrs().values().stream()
                            .findFirst().orElse(null);
                }
                if (addr == null || addr.isBlank()) {
                    throw new IllegalStateException("No broker address found for broker: " + brokerName);
                }
                return addr;
            }
        }
        throw new IllegalStateException("Broker not found in cluster info: " + brokerName);
    }

    private List<String> buildResetOffsetPreviewWarnings(List<ResetConsumerOffsetQueuePreviewVO> queues,
                                                         int rewindQueueCount, int fastForwardQueueCount) {
        List<String> warnings = new ArrayList<>();
        long failedQueueCount = queues.stream().filter(queue -> RISK_ERROR.equals(queue.getRiskLevel())).count();
        if (failedQueueCount > 0) {
            warnings.add("Failed to preview " + failedQueueCount + " queue(s); retry before applying the reset");
        }
        if (fastForwardQueueCount > 0) {
            warnings.add(fastForwardQueueCount + " queue(s) will move forward and may skip unconsumed messages");
        }
        if (rewindQueueCount > 0) {
            warnings.add(rewindQueueCount + " queue(s) will move backward and may replay consumed messages");
        }
        if (queues.stream().anyMatch(queue -> queue.getCurrentLag() == ConsumerLagResolver.UNKNOWN
                || queue.getProjectedLag() == ConsumerLagResolver.UNKNOWN)) {
            warnings.add("At least one queue has unavailable lag; affected backlog totals are unavailable");
        }
        if (queues.stream().anyMatch(queue -> queue.getMinOffset() >= 0
                && queue.getTargetOffset() == queue.getMinOffset())) {
            warnings.add("At least one queue will reset to the minimum retained offset");
        }
        if (queues.stream().anyMatch(queue -> queue.getMaxOffset() >= 0
                && queue.getTargetOffset() == queue.getMaxOffset())) {
            warnings.add("At least one queue will reset to the latest offset");
        }
        return warnings;
    }

    private long aggregateResetPreviewLag(List<ResetConsumerOffsetQueuePreviewVO> queues, boolean projected) {
        long total = 0L;
        for (ResetConsumerOffsetQueuePreviewVO queue : queues) {
            long lag = projected ? queue.getProjectedLag() : queue.getCurrentLag();
            if (lag == ConsumerLagResolver.UNKNOWN) {
                return ConsumerLagResolver.UNKNOWN;
            }
            total += lag;
        }
        return total;
    }

    private long resolveLag(long brokerOffset, long consumerOffset) {
        return ConsumerLagResolver.resolve(brokerOffset - consumerOffset, null);
    }

    private long clampOffset(long offset, long minOffset, long maxOffset) {
        return Math.max(minOffset, Math.min(offset, maxOffset));
    }

    private String resetOffsetRiskLevel(long offsetDelta, long targetOffset, long minOffset, long maxOffset) {
        if (offsetDelta != 0 || targetOffset == minOffset || targetOffset == maxOffset) {
            return RISK_WARNING;
        }
        return RISK_INFO;
    }

    private String resetOffsetPreviewMessage(long offsetDelta, long targetOffset, long minOffset, long maxOffset) {
        List<String> messages = new ArrayList<>();
        if (offsetDelta < 0) {
            messages.add("Replays " + Math.abs(offsetDelta) + " message(s)");
        } else if (offsetDelta > 0) {
            messages.add("Skips " + offsetDelta + " unconsumed message(s)");
        } else {
            messages.add("Offset unchanged");
        }
        if (targetOffset == minOffset) {
            messages.add("target is the minimum retained offset");
        }
        if (targetOffset == maxOffset) {
            messages.add("target is the latest offset");
        }
        return String.join("; ", messages);
    }

    @Override
    public void resetOffset(String instanceId, String rawName, long timestamp, String rawTopic) {
        if (!StringUtils.hasText(rawTopic)) {
            throw new BusinessException(400, "topic is required for offset reset");
        }
        String name = ResourceOwnershipGuard.requireText(rawName, "groupName");
        String topic = rawTopic.trim();
        try {
            // resetOffsetNew applies the searched offset even when it is ahead of the
            // current offset (the preview offers "skip unconsumed messages") and falls
            // back to direct offset writes when the group is offline; the non-forcing
            // resetOffsetByTimestamp silently capped forward targets to the current
            // offset and failed with CONSUMER_NOT_ONLINE for offline groups.
            InstanceVO instance = ownershipGuard.requireInstance(instanceId);
            Resource group = new Resource(Kind.GROUP, ResourceOwnershipGuard.requireText(name, "groupName"));
            Resource topicResource = ownershipGuard.topicResource(topic);
            ownershipGuard.check(instance, group, true);
            ownershipGuard.check(instance, topicResource, true);
            ownershipGuard.withOwned(instance, List.of(group, topicResource), () ->
                    executeForInstance(instance.getName(), admin -> {
                        var groupOwner = ownershipGuard.check(instance, group, true);
                        var topicOwner = ownershipGuard.check(instance, topicResource, true);
                        if (!groupOwner.clusterId().equals(topicOwner.clusterId())) {
                            throw new BusinessException(409, "Group and topic are not in the same target cluster");
                        }
                        var target = ApacheWriteTargetResolver.resolve(admin, instance, groupOwner.clusterId());
                        ApacheWriteTargetResolver.requireTopicRoute(admin, target, topic);
                        admin.resetOffsetNew(name, topic, timestamp);
                        return null;
                    }));
            recordAudit("RESET_OFFSET", name,
                    "instanceId=" + instanceId + ", topic=" + topic + ", timestamp=" + timestamp, "SUCCESS");
        } catch (BusinessException e) {
            recordAudit("RESET_OFFSET", name, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("RESET_OFFSET", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to reset offset: " + e.getMessage());
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Classifies a broker failure, surfacing a clear "not supported in proxy mode" error when the
     * broker rejects a request code (e.g. {@code 106} / {@code 206}) that the 5.0 proxy does not
     * forward. This is the guarded seam for proxy fallback: instead of crashing on the raw broker
     * exception, callers get an actionable message pointing at the proxy endpoint.
     */
    private BusinessException classifyBrokerFailure(Exception e, String operation) {
        if (e instanceof MQBrokerException mbe && ProxyFallbackPolicy.isUnsupportedRequestCode(mbe)) {
            log.warn("Broker request not supported during {} ({}); connect via the RocketMQ 5.0 proxy",
                    operation, mbe.getErrorMessage());
            return new BusinessException(501, "Operation '" + operation
                    + "' is not supported when connecting through a RocketMQ 5.0 proxy "
                    + "(request code not supported). Use the proxy endpoint. Cause: " + mbe.getErrorMessage());
        }
        return new BusinessException(500, "Failed to " + operation + ": " + e.getMessage());
    }

    private void recordAudit(String action, String resource, String detail, String result) {
        try {
            auditService.record(action, auditResourceType(action), resource, null, detail, result);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit action={} resource={}: {}", action, resource,
                    auditFailure.getMessage());
        }
    }

    private String auditResourceType(String action) {
        if (action.endsWith("_TOPIC")) {
            return "TOPIC";
        }
        if ("SEND_MESSAGE".equals(action)) {
            return "MESSAGE";
        }
        return "GROUP";
    }

    /**
     * Returns the configured default NameServer address, failing fast when the studio has no
     * RocketMQ endpoint configured (equivalent to the former absent admin bean).
     */
    private String namesrvAddr() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        return namesrvAddr;
    }

    private String namesrvAddr(String instanceId) {
        return runtimeAdminClientResolver.resolveEndpoint(ResourceOwnershipGuard.requireText(instanceId, "instanceId"));
    }

    private <T> T executeForInstance(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        return runtimeAdminClientResolver.execute(ResourceOwnershipGuard.requireText(instanceId, "instanceId"), action);
    }

    private <T> T executeResourceWrite(String instanceId, Kind kind, String name, boolean create, TopicType type,
                                       java.util.function.BiFunction<MQAdminExt, String, T> action) {
        InstanceVO instance = ownershipGuard.requireInstance(instanceId);
        Resource resource = new Resource(kind, ResourceOwnershipGuard.requireText(name, "resourceName"));
        if (create && kind == Kind.TOPIC && ownershipGuard.topicResource(name).kind() != Kind.TOPIC) {
            throw new BusinessException(400, "Derived resources cannot be registered as a regular topic");
        }
        var owner = ownershipGuard.check(instance, resource, !create);
        ownershipGuard.requireSupportedProvider(instance);
        return executeForInstance(instance.getName(), admin -> {
            var target = ApacheWriteTargetResolver.resolve(admin, instance, owner == null ? null : owner.clusterId());
            return ownershipGuard.write(instance, resource, target.cluster(), create,
                    type == null ? null : type.name(), () -> action.apply(admin, target.cluster()));
        });
    }

    /** Import reads all target masters within the ownership row lock; existing config is only registered, never overwritten. */
    private boolean verifyTopicImport(MQAdminExt admin, Set<String> masters, TopicConfig requested) throws Exception {
        boolean found = false;
        for (String master : masters) {
            TopicConfig current;
            try {
                current = admin.examineTopicConfig(master, requested.getTopicName());
            } catch (Exception failure) {
                if (MqResponseCodes.hasResponseCode(failure, ResponseCode.TOPIC_NOT_EXIST)) {
                    continue;
                }
                throw failure;
            }
            if (current == null) {
                continue;
            }
            found = true;
            if (!requested.getTopicName().equals(current.getTopicName())
                    || requested.getReadQueueNums() != current.getReadQueueNums()
                    || requested.getWriteQueueNums() != current.getWriteQueueNums()
                    || requested.getPerm() != current.getPerm()
                    // The write protocol's +message.type is not the bare key used when reading config; do not read the request's type default directly.
                    || !requested.getAttributes().getOrDefault("+" + TopicAttributes.TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName(),
                            requested.getTopicMessageType().name()).equals(current.getTopicMessageType().name())) {
                throw new BusinessException(409, "Import config conflicts with the existing physical topic; refusing to overwrite: " + requested.getTopicName());
            }
        }
        return found;
    }

    private boolean verifyGroupImport(MQAdminExt admin, Set<String> masters, SubscriptionGroupConfig requested)
            throws Exception {
        boolean found = false;
        for (String master : masters) {
            SubscriptionGroupConfig current;
            try {
                current = admin.examineSubscriptionGroupConfig(master, requested.getGroupName());
            } catch (Exception failure) {
                if (MqResponseCodes.hasResponseCode(failure, ResponseCode.SUBSCRIPTION_GROUP_NOT_EXIST)) {
                    continue;
                }
                throw failure;
            }
            if (current == null) {
                continue;
            }
            found = true;
            if (!requested.getGroupName().equals(current.getGroupName())
                    || requested.getRetryMaxTimes() != current.getRetryMaxTimes()) {
                throw new BusinessException(409, "Import config conflicts with the existing physical group; refusing to overwrite: " + requested.getGroupName());
            }
        }
        return found;
    }

    private Set<String> getMasterBrokerAddrsForCluster(MQAdminExt admin, String clusterName) throws Exception {
        return ApacheWriteTargetResolver.target(admin.examineBrokerClusterInfo(), clusterName).masters();
    }

    private void deletePhysicalTopic(MQAdminExt admin, String instanceId, String name, String cluster,
                                      Set<String> brokers) throws Exception {
        Set<String> nameservers = resolveNameservers(instanceId);
        admin.deleteTopicInBroker(brokers, name);
        admin.deleteTopicInNameServer(nameservers, cluster, name);
    }

    private Set<String> resolveNameservers(String instanceId) {
        Set<String> nameservers = new HashSet<>();
        String endpoint = namesrvAddr(instanceId);
        if (!StringUtils.hasText(endpoint)) {
            throw new BusinessException(409, "Target instance has no available NameServer");
        }
        for (String address : endpoint.split("[;,]")) {
            if (StringUtils.hasText(address)) {
                nameservers.add(address.trim());
            }
        }
        if (nameservers.isEmpty()) {
            throw new BusinessException(409, "Target instance has no available NameServer");
        }
        return nameservers;
    }

    private int toRocketMQPerm(TopicPerm perm) {
        if (perm == TopicPerm.RO) {
            return 4;
        }
        if (perm == TopicPerm.WO) {
            return 2;
        }
        return 6;
    }

    private TopicPerm fromRocketMQPerm(Integer perm) {
        if (perm != null && perm == 4) {
            return TopicPerm.RO;
        }
        if (perm != null && perm == 2) {
            return TopicPerm.WO;
        }
        return TopicPerm.RW;
    }
}
