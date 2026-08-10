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

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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

    private static final String MESSAGE_SENDER_GROUP_PREFIX = "studio-msg-sender";
    private static final int MAX_MESSAGE_SIZE = 4 * 1024 * 1024; // 4 MB default broker limit

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;
    private final AuditService auditService;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Override
    public TopicVO getTopic(String name) {
        return adminFactory.execute(namesrvAddr(), null, admin -> {
            try {
                var routeData = admin.examineTopicRouteInfo(name);
                if (routeData == null || routeData.getQueueDatas() == null || routeData.getQueueDatas().isEmpty()) {
                    throw new BusinessException(404, "Topic not found: " + name);
                }
                var qd = routeData.getQueueDatas().get(0);
                TopicVO vo = new TopicVO();
                vo.setId(name);
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
            return runtimeAdminClientResolver.execute(instanceId, admin -> getConsumerGroup(admin, name));
        }
        return adminFactory.execute(namesrvAddr(), null, admin -> getConsumerGroup(admin, name));
    }

    private ConsumerGroupVO getConsumerGroup(MQAdminExt admin, String name) {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setId(name);
        vo.setName(name);
        try {
            var conn = admin.examineConsumerConnectionInfo(name);
            if (conn != null) {
                if (conn.getConnectionSet() != null) {
                    vo.setOnlineInstances(conn.getConnectionSet().size());
                }
                if (conn.getSubscriptionTable() != null) {
                    vo.setSubscribedTopics(new ArrayList<>(conn.getSubscriptionTable().keySet()));
                }
            }
        } catch (MQClientException exception) {
            if (exception.getResponseCode() == ResponseCode.CONSUMER_NOT_ONLINE) {
                log.debug("Consumer group {} is offline", name);
                return vo;
            }
            throw new BusinessException(502, "Failed to get consumer group: " + exception.getMessage());
        } catch (Exception exception) {
            throw new BusinessException(502, "Failed to get consumer group: " + exception.getMessage());
        }
        return vo;
    }

    @Override
    public TopicVO createTopic(TopicVO topic) {
        String topicName = topic.getName();
        int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;
        int readQueues = topic.getReadQueues() > 0 ? topic.getReadQueues() : 8;

        return executeForInstance(topic.getInstanceId(), admin -> {
            try {
                String clusterName = getClusterName(admin);
                // Match on the (cluster_id, name) key: the same topic name can exist in several
                // clusters, and a name-only lookup would blow up with TooManyResultsException.
                RmqTopic existing = topicMapper.selectOne(
                        new LambdaQueryWrapper<RmqTopic>()
                                .eq(RmqTopic::getClusterId, clusterName)
                                .eq(RmqTopic::getName, topicName));
                TopicPerm effectivePerm = topic.getPerm() != null
                        ? topic.getPerm()
                        : existing == null ? TopicPerm.RW : fromRocketMQPerm(existing.getPerm());
                Set<String> brokerAddrs = getAllMasterBrokerAddrs(admin);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(500, "No broker available to create topic");
                }

                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setWriteQueueNums(writeQueues);
                topicConfig.setReadQueueNums(readQueues);
                topicConfig.setPerm(toRocketMQPerm(effectivePerm));

                for (String addr : brokerAddrs) {
                    admin.createAndUpdateTopicConfig(addr, topicConfig);
                }

                // Persist to DB. Re-creating a topic that already has a record (for example when
                // rebuilding a broker route from the console) must update it instead of failing on
                // the unique (cluster_id, name) key.
                RmqTopic entity = topicMapper.selectOne(new LambdaQueryWrapper<RmqTopic>()
                        .eq(RmqTopic::getClusterId, clusterName)
                        .eq(RmqTopic::getName, topicName));
                boolean isNew = entity == null;
                if (isNew) {
                    entity = new RmqTopic();
                    entity.setName(topicName);
                    entity.setClusterId(clusterName);
                    entity.setCreatedAt(LocalDateTime.now());
                }
                if (StringUtils.hasText(topic.getInstanceId())) {
                    entity.setInstanceId(topic.getInstanceId());
                }
                entity.setTopicType(topic.getType() != null ? topic.getType().name() : "NORMAL");
                entity.setReadQueueNums(readQueues);
                entity.setWriteQueueNums(writeQueues);
                entity.setPerm(topicConfig.getPerm());
                if (StringUtils.hasText(topic.getRemark())) {
                    entity.setRemark(topic.getRemark());
                }
                entity.setStatus("ACTIVE");
                entity.setUpdatedAt(LocalDateTime.now());
                if (isNew) {
                    topicMapper.insert(entity);
                } else {
                    topicMapper.updateById(entity);
                }

                recordAudit("CREATE_TOPIC", topicName,
                        "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

                topic.setId(topicName);
                topic.setWriteQueues(writeQueues);
                topic.setReadQueues(readQueues);
                return topic;
            } catch (BusinessException e) {
                recordAudit("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw new BusinessException(500, "Failed to create topic: " + e.getMessage());
            }
        });
    }

    @Override
    public TopicVO updateTopic(TopicVO topic) {
        String topicName = topic.getName();
        int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;
        int readQueues = topic.getReadQueues() > 0 ? topic.getReadQueues() : 8;

        return executeForInstance(topic.getInstanceId(), admin -> {
            try {
                // Match on the (cluster_id, name) key to avoid ambiguity when several clusters share
                // the same topic name (a name-only lookup would throw TooManyResultsException).
                String clusterName = getClusterName(admin);
                RmqTopic existing = topicMapper.selectOne(
                        new LambdaQueryWrapper<RmqTopic>()
                                .eq(RmqTopic::getClusterId, clusterName)
                                .eq(RmqTopic::getName, topicName));
                TopicPerm effectivePerm = topic.getPerm() != null
                        ? topic.getPerm()
                        : existing == null ? TopicPerm.RW : fromRocketMQPerm(existing.getPerm());
                Set<String> brokerAddrs = getAllMasterBrokerAddrs(admin);
                if (brokerAddrs.isEmpty()) {
                    throw new BusinessException(500, "No broker available to update topic");
                }

                TopicConfig topicConfig = new TopicConfig();
                topicConfig.setTopicName(topicName);
                topicConfig.setWriteQueueNums(writeQueues);
                topicConfig.setReadQueueNums(readQueues);
                topicConfig.setPerm(toRocketMQPerm(effectivePerm));

                for (String addr : brokerAddrs) {
                    admin.createAndUpdateTopicConfig(addr, topicConfig);
                }

                // Update DB record
                if (existing != null) {
                    existing.setWriteQueueNums(writeQueues);
                    existing.setReadQueueNums(readQueues);
                    existing.setPerm(topicConfig.getPerm());
                    existing.setUpdatedAt(LocalDateTime.now());
                    topicMapper.updateById(existing);
                }

                recordAudit("UPDATE_TOPIC", topicName,
                        "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

                topic.setId(topicName);
                topic.setWriteQueues(writeQueues);
                topic.setReadQueues(readQueues);
                return topic;
            } catch (BusinessException e) {
                recordAudit("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
                throw new BusinessException(500, "Failed to update topic: " + e.getMessage());
            }
        });
    }

    @Override
    public void deleteTopic(String instanceId, String name) {
        String namesrvAddr = namesrvAddr(instanceId);
        executeForInstance(instanceId, admin -> {
            try {
                Set<String> brokerAddrs = getAllMasterBrokerAddrs(admin);

                // Delete from brokers
                if (!brokerAddrs.isEmpty()) {
                    admin.deleteTopicInBroker(brokerAddrs, name);
                }

                // Delete from nameserver
                Set<String> nsAddrs = new HashSet<>();
                for (String addr : namesrvAddr.split("[;,]")) {
                    String trimmed = addr.trim();
                    if (!trimmed.isEmpty()) {
                        nsAddrs.add(trimmed);
                    }
                }
                admin.deleteTopicInNameServer(nsAddrs, getClusterName(admin), name);

                // Topic names may be shared by several clusters managed by this Studio instance.
                topicMapper.delete(new LambdaQueryWrapper<RmqTopic>()
                        .eq(RmqTopic::getClusterId, getClusterName(admin))
                        .eq(RmqTopic::getName, name));

                recordAudit("DELETE_TOPIC", name, "", "SUCCESS");
                return null;
            } catch (BusinessException e) {
                recordAudit("DELETE_TOPIC", name, e.getMessage(), "FAILED");
                throw e;
            } catch (Exception e) {
                recordAudit("DELETE_TOPIC", name, e.getMessage(), "FAILED");
                throw new BusinessException(500, "Failed to delete topic: " + e.getMessage());
            }
        });
    }

    @Override
    public SendMessageVO sendMessage(SendMessageDTO request) {
        String namesrvAddr = namesrvAddr(request.getInstanceId());

        DefaultMQProducer producer = new DefaultMQProducer(nextMessageSenderGroup());
        producer.setNamesrvAddr(namesrvAddr);
        producer.setSendMsgTimeout(5000);

        try {
            producer.start();

            String topic = request.getTopic();
            String tag = request.getTag() != null ? request.getTag() : "";
            String key = request.getKey() != null ? request.getKey() : "";
            String body = request.getBody() != null ? request.getBody() : "";
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            if (bodyBytes.length > MAX_MESSAGE_SIZE) {
                throw new BusinessException(400, "Message body size " + bodyBytes.length
                        + " exceeds the maximum of " + MAX_MESSAGE_SIZE + " bytes");
            }

            Message msg = new Message(topic, tag, key, bodyBytes);

            // Add custom properties
            if (request.getProperties() != null) {
                for (Map.Entry<String, String> entry : request.getProperties().entrySet()) {
                    msg.putUserProperty(entry.getKey(), entry.getValue());
                }
            }

            SendResult sendResult = producer.send(msg);
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
        } catch (BusinessException e) {
            recordAudit("SEND_MESSAGE", request.getTopic(), e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("SEND_MESSAGE", request.getTopic(), e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to send message: " + e.getMessage());
        } finally {
            producer.shutdown();
        }
    }

    static String nextMessageSenderGroup() {
        return ShortLivedClientName.next(MESSAGE_SENDER_GROUP_PREFIX);
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        if (group != null && StringUtils.hasText(group.getInstanceId())) {
            return runtimeAdminClientResolver.execute(group.getInstanceId(),
                    admin -> createConsumerGroup(admin, group));
        }
        return adminFactory.execute(namesrvAddr(), null, admin -> createConsumerGroup(admin, group));
    }

    private ConsumerGroupVO createConsumerGroup(MQAdminExt admin, ConsumerGroupVO group) {
        String groupName = group.getName();

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs(admin);
            if (brokerAddrs.isEmpty()) {
                throw new BusinessException(500, "No broker available to create consumer group");
            }

            SubscriptionGroupConfig config = new SubscriptionGroupConfig();
            config.setGroupName(groupName);
            config.setConsumeEnable(true);
            config.setConsumeBroadcastEnable(true);
            config.setRetryQueueNums(1);
            config.setRetryMaxTimes(group.getRetryMaxTimes() > 0 ? group.getRetryMaxTimes() : 16);

            for (String addr : brokerAddrs) {
                admin.createAndUpdateSubscriptionGroupConfig(addr, config);
            }

            // Persist to DB, upserting so re-creating an existing group does not violate the
            // unique (cluster_id, name) key.
            String groupClusterName = getClusterName(admin);
            RmqGroup entity = groupMapper.selectOne(new LambdaQueryWrapper<RmqGroup>()
                    .eq(RmqGroup::getClusterId, groupClusterName)
                    .eq(RmqGroup::getName, groupName));
            boolean isNewGroup = entity == null;
            if (isNewGroup) {
                entity = new RmqGroup();
                entity.setName(groupName);
                entity.setClusterId(groupClusterName);
                entity.setCreatedAt(LocalDateTime.now());
            }
            if (StringUtils.hasText(group.getInstanceId())) {
                entity.setInstanceId(group.getInstanceId());
            }
            entity.setConsumeType(group.getConsumeType() != null ? group.getConsumeType().name() : "CLUSTERING");
            entity.setMessageModel(group.getSubscriptionMode() != null ? group.getSubscriptionMode().name() : "Push");
            entity.setMaxRetry(config.getRetryMaxTimes());
            entity.setStatus("ACTIVE");
            entity.setUpdatedAt(LocalDateTime.now());
            if (isNewGroup) {
                groupMapper.insert(entity);
            } else {
                groupMapper.updateById(entity);
            }

            recordAudit("CREATE_GROUP", groupName,
                    "retryMaxTimes=" + config.getRetryMaxTimes(), "SUCCESS");

            group.setId(groupName);
            return group;
        } catch (BusinessException e) {
            recordAudit("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to create consumer group: " + e.getMessage());
        }
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String name) {
        if (StringUtils.hasText(instanceId)) {
            runtimeAdminClientResolver.execute(instanceId, admin -> {
                doDeleteConsumerGroup(admin, name);
                return null;
            });
            return;
        }
        adminFactory.execute(namesrvAddr(), null, admin -> {
            doDeleteConsumerGroup(admin, name);
            return null;
        });
    }

    private void doDeleteConsumerGroup(MQAdminExt admin, String name) {
        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs(admin);

            for (String addr : brokerAddrs) {
                admin.deleteSubscriptionGroup(addr, name, true);
            }

            // Consumer group names may be shared by several clusters managed by this Studio instance.
            groupMapper.delete(new LambdaQueryWrapper<RmqGroup>()
                    .eq(RmqGroup::getClusterId, getClusterName(admin))
                    .eq(RmqGroup::getName, name));

            recordAudit("DELETE_GROUP", name, "", "SUCCESS");
        } catch (BusinessException e) {
            recordAudit("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            recordAudit("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to delete consumer group: " + e.getMessage());
        }
    }

    @Override
    public void resetOffset(String instanceId, String name, long timestamp, String topic) {
        try {
            if (StringUtils.hasText(instanceId)) {
                runtimeAdminClientResolver.execute(instanceId, admin -> {
                    admin.resetOffsetByTimestamp(getClusterName(admin), topic, name, timestamp, false);
                    return null;
                });
            } else {
                adminFactory.execute(namesrvAddr(), null, admin -> {
                    admin.resetOffsetByTimestamp(getClusterName(admin), topic, name, timestamp, false);
                    return null;
                });
            }
            recordAudit("RESET_OFFSET", name,
                    "instanceId=" + instanceId + ", topic=" + topic + ", timestamp=" + timestamp, "SUCCESS");
        } catch (Exception e) {
            recordAudit("RESET_OFFSET", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to reset offset: " + e.getMessage());
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private void recordAudit(String action, String resource, String detail, String result) {
        try {
            auditService.record(action, resource, detail, result);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit action={} resource={}: {}", action, resource,
                    auditFailure.getMessage());
        }
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
        return StringUtils.hasText(instanceId)
                ? runtimeAdminClientResolver.resolveEndpoint(instanceId)
                : namesrvAddr();
    }

    private <T> T executeForInstance(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, action);
        }
        return adminFactory.execute(namesrvAddr(), null, action);
    }

    private Set<String> getAllMasterBrokerAddrs(MQAdminExt admin) throws Exception {
        Set<String> addrs = new HashSet<>();
        ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return addrs;
        }

        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData.getBrokerAddrs() == null) {
                continue;
            }
            // Use master address (brokerId = 0) preferentially
            String masterAddr = brokerData.getBrokerAddrs().get(0L);
            if (masterAddr == null && !brokerData.getBrokerAddrs().isEmpty()) {
                masterAddr = brokerData.getBrokerAddrs().values().iterator().next();
            }
            if (masterAddr != null) {
                addrs.add(masterAddr);
            }
        }
        return addrs;
    }

    private String getClusterName(MQAdminExt admin) {
        try {
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            if (clusterInfo != null && clusterInfo.getClusterAddrTable() != null
                    && !clusterInfo.getClusterAddrTable().isEmpty()) {
                return clusterInfo.getClusterAddrTable().keySet().iterator().next();
            }
        } catch (Exception ignored) {
        }
        return "DefaultCluster";
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
