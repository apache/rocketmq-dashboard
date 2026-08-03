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

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.AdminClient;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Real AdminClient implementation backed by DefaultMQAdminExt.
 * Provides topic CRUD, message sending, consumer group CRUD, and offset reset.
 */
@Service
@Primary
public class RocketMQAdminClientImpl implements AdminClient {

    private static final Logger log = LoggerFactory.getLogger(RocketMQAdminClientImpl.class);

    private final DefaultMQAdminExt adminExt;
    private final RocketMQProperties properties;
    private final RmqTopicMapper topicMapper;
    private final RmqGroupMapper groupMapper;
    private final AuditService auditService;

    @Autowired
    public RocketMQAdminClientImpl(
            @Autowired(required = false) DefaultMQAdminExt adminExt,
            RocketMQProperties properties,
            RmqTopicMapper topicMapper,
            RmqGroupMapper groupMapper,
            AuditService auditService) {
        this.adminExt = adminExt;
        this.properties = properties;
        this.topicMapper = topicMapper;
        this.groupMapper = groupMapper;
        this.auditService = auditService;
    }

    @Override
    public TopicVO getTopic(String name) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        try {
            var routeData = adminExt.examineTopicRouteInfo(name);
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
    }

    @Override
    public ConsumerGroupVO getConsumerGroup(String name) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setId(name);
        vo.setName(name);
        try {
            var conn = adminExt.examineConsumerConnectionInfo(name);
            if (conn != null) {
                if (conn.getConnectionSet() != null) {
                    vo.setOnlineInstances(conn.getConnectionSet().size());
                }
                if (conn.getSubscriptionTable() != null) {
                    vo.setSubscribedTopics(new ArrayList<>(conn.getSubscriptionTable().keySet()));
                }
            }
        } catch (Exception ignored) {
            // Group may be offline
        }
        return vo;
    }

    @Override
    public TopicVO createTopic(TopicVO topic) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        String topicName = topic.getName();
        int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;
        int readQueues = topic.getReadQueues() > 0 ? topic.getReadQueues() : 8;

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs();
            if (brokerAddrs.isEmpty()) {
                throw new BusinessException(500, "No broker available to create topic");
            }

            TopicConfig topicConfig = new TopicConfig();
            topicConfig.setTopicName(topicName);
            topicConfig.setWriteQueueNums(writeQueues);
            topicConfig.setReadQueueNums(readQueues);
            topicConfig.setPerm(6); // RW

            for (String addr : brokerAddrs) {
                adminExt.createAndUpdateTopicConfig(addr, topicConfig);
            }

            // Persist to DB. Re-creating a topic that already has a record (for example when
            // rebuilding a broker route from the console) must update it instead of failing on
            // the unique (cluster_id, name) key.
            String clusterName = getClusterName();
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
            entity.setTopicType(topic.getType() != null ? topic.getType().name() : "NORMAL");
            entity.setReadQueueNums(readQueues);
            entity.setWriteQueueNums(writeQueues);
            entity.setPerm(6);
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

            auditService.record("CREATE_TOPIC", topicName,
                    "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

            topic.setId(topicName);
            topic.setWriteQueues(writeQueues);
            topic.setReadQueues(readQueues);
            return topic;
        } catch (BusinessException e) {
            auditService.record("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            auditService.record("CREATE_TOPIC", topicName, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to create topic: " + e.getMessage());
        }
    }

    @Override
    public TopicVO updateTopic(TopicVO topic) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        String topicName = topic.getName();
        int writeQueues = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : 8;
        int readQueues = topic.getReadQueues() > 0 ? topic.getReadQueues() : 8;

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs();
            if (brokerAddrs.isEmpty()) {
                throw new BusinessException(500, "No broker available to update topic");
            }

            TopicConfig topicConfig = new TopicConfig();
            topicConfig.setTopicName(topicName);
            topicConfig.setWriteQueueNums(writeQueues);
            topicConfig.setReadQueueNums(readQueues);
            topicConfig.setPerm(6); // RW

            for (String addr : brokerAddrs) {
                adminExt.createAndUpdateTopicConfig(addr, topicConfig);
            }

            // Update DB record
            RmqTopic existing = topicMapper.selectOne(
                    new LambdaQueryWrapper<RmqTopic>().eq(RmqTopic::getName, topicName));
            if (existing != null) {
                existing.setWriteQueueNums(writeQueues);
                existing.setReadQueueNums(readQueues);
                existing.setUpdatedAt(LocalDateTime.now());
                topicMapper.updateById(existing);
            }

            auditService.record("UPDATE_TOPIC", topicName,
                    "queues=" + writeQueues + "/" + readQueues, "SUCCESS");

            topic.setId(topicName);
            topic.setWriteQueues(writeQueues);
            topic.setReadQueues(readQueues);
            return topic;
        } catch (BusinessException e) {
            auditService.record("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            auditService.record("UPDATE_TOPIC", topicName, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to update topic: " + e.getMessage());
        }
    }

    @Override
    public void deleteTopic(String name) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs();

            // Delete from brokers
            if (!brokerAddrs.isEmpty()) {
                adminExt.deleteTopicInBroker(brokerAddrs, name);
            }

            // Delete from nameserver
            String namesrvAddr = properties.getNamesrvAddr();
            if (namesrvAddr != null && !namesrvAddr.isEmpty()) {
                Set<String> nsAddrs = new HashSet<>();
                for (String addr : namesrvAddr.split("[;,]")) {
                    String trimmed = addr.trim();
                    if (!trimmed.isEmpty()) {
                        nsAddrs.add(trimmed);
                    }
                }
                adminExt.deleteTopicInNameServer(nsAddrs, getClusterName(), name);
            }

            // Delete from DB
            topicMapper.delete(new LambdaQueryWrapper<RmqTopic>().eq(RmqTopic::getName, name));

            auditService.record("DELETE_TOPIC", name, "", "SUCCESS");
        } catch (BusinessException e) {
            auditService.record("DELETE_TOPIC", name, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            auditService.record("DELETE_TOPIC", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to delete topic: " + e.getMessage());
        }
    }

    @Override
    public SendMessageVO sendMessage(SendMessageDTO request) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        String namesrvAddr = properties.getNamesrvAddr();
        if (namesrvAddr == null || namesrvAddr.isEmpty()) {
            throw new BusinessException(500, "Nameserver address not configured");
        }

        DefaultMQProducer producer = new DefaultMQProducer("studio_msg_sender_" + System.currentTimeMillis());
        producer.setNamesrvAddr(namesrvAddr);
        producer.setSendMsgTimeout(5000);

        try {
            producer.start();

            String topic = request.getTopic();
            String tag = request.getTag() != null ? request.getTag() : "";
            String key = request.getKey() != null ? request.getKey() : "";
            String body = request.getBody() != null ? request.getBody() : "";

            String fullTopic = tag.isEmpty() ? topic : topic + ":" + tag;
            Message msg = new Message(topic, tag, key, body.getBytes(StandardCharsets.UTF_8));

            // Add custom properties
            if (request.getProperties() != null) {
                for (Map.Entry<String, String> entry : request.getProperties().entrySet()) {
                    msg.putUserProperty(entry.getKey(), entry.getValue());
                }
            }

            SendResult sendResult = producer.send(msg);

            auditService.record("SEND_MESSAGE", topic,
                    "tag=" + tag + ", key=" + key + ", msgId=" + sendResult.getMsgId(), "SUCCESS");

            return SendMessageVO.builder()
                    .msgId(sendResult.getMsgId())
                    .sendTime(System.currentTimeMillis())
                    .offsetMsgId(sendResult.getOffsetMsgId())
                    .build();
        } catch (Exception e) {
            auditService.record("SEND_MESSAGE", request.getTopic(), e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to send message: " + e.getMessage());
        } finally {
            producer.shutdown();
        }
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        String groupName = group.getName();

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs();
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
                adminExt.createAndUpdateSubscriptionGroupConfig(addr, config);
            }

            // Persist to DB, upserting so re-creating an existing group does not violate the
            // unique (cluster_id, name) key.
            String groupClusterName = getClusterName();
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

            auditService.record("CREATE_GROUP", groupName,
                    "retryMaxTimes=" + config.getRetryMaxTimes(), "SUCCESS");

            group.setId(groupName);
            return group;
        } catch (BusinessException e) {
            auditService.record("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            auditService.record("CREATE_GROUP", groupName, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to create consumer group: " + e.getMessage());
        }
    }

    @Override
    public void deleteConsumerGroup(String name) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        try {
            Set<String> brokerAddrs = getAllMasterBrokerAddrs();

            for (String addr : brokerAddrs) {
                adminExt.deleteSubscriptionGroup(addr, name, true);
            }

            // Delete from DB
            groupMapper.delete(new LambdaQueryWrapper<RmqGroup>().eq(RmqGroup::getName, name));

            auditService.record("DELETE_GROUP", name, "", "SUCCESS");
        } catch (BusinessException e) {
            auditService.record("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw e;
        } catch (Exception e) {
            auditService.record("DELETE_GROUP", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to delete consumer group: " + e.getMessage());
        }
    }

    @Override
    public void resetOffset(String name, long timestamp, String topic) {
        if (adminExt == null) {
            throw new BusinessException(503, "RocketMQ admin not connected");
        }

        try {
            adminExt.resetOffsetByTimestamp(getClusterName(), topic, name, timestamp, false);
            auditService.record("RESET_OFFSET", name,
                    "topic=" + topic + ", timestamp=" + timestamp, "SUCCESS");
        } catch (Exception e) {
            auditService.record("RESET_OFFSET", name, e.getMessage(), "FAILED");
            throw new BusinessException(500, "Failed to reset offset: " + e.getMessage());
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private Set<String> getAllMasterBrokerAddrs() throws Exception {
        Set<String> addrs = new HashSet<>();
        ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
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

    private String getClusterName() {
        try {
            ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
            if (clusterInfo != null && clusterInfo.getClusterAddrTable() != null
                    && !clusterInfo.getClusterAddrTable().isEmpty()) {
                return clusterInfo.getClusterAddrTable().keySet().iterator().next();
            }
        } catch (Exception ignored) {
        }
        return "DefaultCluster";
    }
}
