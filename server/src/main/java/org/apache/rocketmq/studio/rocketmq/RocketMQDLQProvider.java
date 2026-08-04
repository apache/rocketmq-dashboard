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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQProvider;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real {@link DLQProvider} backed by the RocketMQ admin API. Lists dead-letter groups by scanning
 * {@code %DLQ%} topics and resends dead-letter messages back to a target topic.
 */
@Service
@Primary
public class RocketMQDLQProvider implements DLQProvider {

    private static final Logger log = LoggerFactory.getLogger(RocketMQDLQProvider.class);

    private static final long ONE_HOUR_MILLIS = 3600_000L;
    private static final int RESEND_HARD_CAP = 5000;

    private final ObjectProvider<DefaultMQAdminExt> adminExtProvider;
    private final AuditService auditService;
    private final RocketMQProperties properties;

    public RocketMQDLQProvider(ObjectProvider<DefaultMQAdminExt> adminExtProvider,
                               AuditService auditService,
                               RocketMQProperties properties) {
        this.adminExtProvider = adminExtProvider;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Override
    public List<DLQGroupVO> listDLQGroups(String clusterId) {
        DefaultMQAdminExt adminExt = adminExtProvider.getIfAvailable();
        if (adminExt == null) {
            log.warn("DefaultMQAdminExt is not configured, returning empty DLQ group list");
            return Collections.emptyList();
        }

        Set<String> topics;
        try {
            TopicList topicList = adminExt.fetchAllTopicList();
            topics = topicList == null ? Collections.emptySet() : topicList.getTopicList();
        } catch (Exception e) {
            log.warn("Failed to fetch topic list for DLQ scan: {}", e.getMessage());
            return Collections.emptyList();
        }

        List<DLQGroupVO> groups = new ArrayList<>();
        for (String topic : topics) {
            if (topic == null || !topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)) {
                continue;
            }
            String groupName = topic.substring(MixAll.DLQ_GROUP_TOPIC_PREFIX.length());
            if (!StringUtils.hasText(groupName)) {
                continue;
            }
            groups.add(buildDLQGroup(adminExt, groupName, topic));
        }
        return groups;
    }

    private DLQGroupVO buildDLQGroup(DefaultMQAdminExt adminExt, String groupName, String dlqTopic) {
        long messageCount = 0L;
        LocalDateTime lastEnqueueTime = null;
        try {
            TopicStatsTable statsTable = adminExt.examineTopicStats(dlqTopic);
            if (statsTable != null && statsTable.getOffsetTable() != null) {
                long latestUpdate = 0L;
                for (Map.Entry<MessageQueue, TopicOffset> entry : statsTable.getOffsetTable().entrySet()) {
                    TopicOffset offset = entry.getValue();
                    if (offset == null) {
                        continue;
                    }
                    messageCount += Math.max(0L, offset.getMaxOffset() - offset.getMinOffset());
                    if (offset.getLastUpdateTimestamp() > latestUpdate) {
                        latestUpdate = offset.getLastUpdateTimestamp();
                    }
                }
                if (latestUpdate > 0L) {
                    lastEnqueueTime = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(latestUpdate), ZoneId.systemDefault());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to examine stats for DLQ topic {}: {}", dlqTopic, e.getMessage());
        }

        return DLQGroupVO.builder()
                .groupName(groupName)
                .dlqTopic(dlqTopic)
                .messageCount(messageCount)
                .lastEnqueueTime(lastEnqueueTime)
                .retryCount(0)
                .status(messageCount > 0 ? "ACTIVE" : "EMPTY")
                .build();
    }

    @Override
    public void resendMessages(String groupName, Long startTime, Long endTime, String targetTopic) {
        DefaultMQAdminExt adminExt = adminExtProvider.getIfAvailable();
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;

        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;

        List<MessageExt> deadLetters = collectDeadLetters(dlqTopic, begin, end);
        int resent = 0;
        int failed = 0;
        if (!deadLetters.isEmpty()) {
            DefaultMQProducer producer = newProducer(groupName);
            try {
                producer.start();
                for (MessageExt deadLetter : deadLetters) {
                    if (resendOne(producer, deadLetter, targetTopic)) {
                        resent++;
                    } else {
                        failed++;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to start resend producer for group {}: {}", groupName, e.getMessage());
                failed += deadLetters.size() - resent;
            } finally {
                producer.shutdown();
            }
        }

        String detail = String.format("group=%s, dlqTopic=%s, targetTopic=%s, matched=%d, resent=%d, failed=%d",
                groupName, dlqTopic, StringUtils.hasText(targetTopic) ? targetTopic : "<original>",
                deadLetters.size(), resent, failed);
        recordAudit(groupName, detail, failed == 0 ? "SUCCESS" : "PARTIAL");
        log.info("DLQ resend completed: {}", detail);
    }

    private List<MessageExt> collectDeadLetters(String dlqTopic, long begin, long end) {
        DefaultMQPullConsumer consumer = newPullConsumer();
        List<MessageExt> result = new ArrayList<>();
        try {
            consumer.start();
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(dlqTopic);
            if (queues == null || queues.isEmpty()) {
                return result;
            }
            outer:
            for (MessageQueue queue : queues) {
                long minOffset = consumer.searchOffset(queue, begin);
                long maxOffset = consumer.searchOffset(queue, end);
                for (long offset = minOffset; offset <= maxOffset; ) {
                    if (result.size() >= RESEND_HARD_CAP) {
                        break outer;
                    }
                    PullResult pullResult = consumer.pull(queue, "*", offset, 32);
                    offset = pullResult.getNextBeginOffset();
                    if (pullResult.getPullStatus() != PullStatus.FOUND
                            || pullResult.getMsgFoundList() == null) {
                        break;
                    }
                    for (MessageExt messageExt : pullResult.getMsgFoundList()) {
                        if (messageExt.getStoreTimestamp() >= begin
                                && messageExt.getStoreTimestamp() <= end) {
                            result.add(messageExt);
                            if (result.size() >= RESEND_HARD_CAP) {
                                break outer;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to collect dead letters from {}: {}", dlqTopic, e.getMessage());
        } finally {
            consumer.shutdown();
        }
        return result;
    }

    private boolean resendOne(DefaultMQProducer producer, MessageExt deadLetter, String targetTopic) {
        String destination = resolveTargetTopic(deadLetter, targetTopic);
        if (!StringUtils.hasText(destination)) {
            log.warn("Skip resend of msgId={}: no target topic resolvable", deadLetter.getMsgId());
            return false;
        }
        try {
            Message message = new Message(destination, deadLetter.getBody());
            if (StringUtils.hasText(deadLetter.getTags())) {
                message.setTags(deadLetter.getTags());
            }
            if (StringUtils.hasText(deadLetter.getKeys())) {
                message.setKeys(deadLetter.getKeys());
            }
            message.putUserProperty("DLQ_ORIGIN_MESSAGE_ID", deadLetter.getMsgId());
            message.putUserProperty("DLQ_ORIGIN_TOPIC", deadLetter.getTopic());
            SendResult sendResult = producer.send(message);
            log.debug("Resent dead letter msgId={} to topic={}, sendStatus={}",
                    deadLetter.getMsgId(), destination, sendResult.getSendStatus());
            return true;
        } catch (Exception e) {
            log.warn("Failed to resend dead letter msgId={} to topic={}: {}",
                    deadLetter.getMsgId(), destination, e.getMessage());
            return false;
        }
    }

    private String resolveTargetTopic(MessageExt deadLetter, String targetTopic) {
        if (StringUtils.hasText(targetTopic)) {
            return targetTopic;
        }
        Map<String, String> properties = deadLetter.getProperties();
        if (properties != null) {
            String origin = properties.get(MessageConst.PROPERTY_DLQ_ORIGIN_TOPIC);
            if (StringUtils.hasText(origin)) {
                return origin;
            }
            String retryTopic = properties.get(MessageConst.PROPERTY_RETRY_TOPIC);
            if (StringUtils.hasText(retryTopic)) {
                return retryTopic;
            }
        }
        return null;
    }

    private DefaultMQPullConsumer newPullConsumer() {
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("studio-dlq-query-group");
        consumer.setInstanceName(ShortLivedClientName.next("studio-dlq-query"));
        if (StringUtils.hasText(properties.getNamesrvAddr())) {
            consumer.setNamesrvAddr(properties.getNamesrvAddr());
        }
        return consumer;
    }

    private DefaultMQProducer newProducer(String groupName) {
        DefaultMQProducer producer = new DefaultMQProducer("studio-dlq-resend-" + groupName);
        producer.setInstanceName(ShortLivedClientName.next("studio-dlq-resend"));
        producer.setRetryTimesWhenSendFailed(2);
        if (StringUtils.hasText(properties.getNamesrvAddr())) {
            producer.setNamesrvAddr(properties.getNamesrvAddr());
        }
        return producer;
    }

    private void recordAudit(String groupName, String detail, String result) {
        try {
            auditService.record("RESEND_DLQ", groupName, detail, result);
        } catch (Exception e) {
            log.warn("Failed to record DLQ resend audit: {}", e.getMessage());
        }
    }
}
