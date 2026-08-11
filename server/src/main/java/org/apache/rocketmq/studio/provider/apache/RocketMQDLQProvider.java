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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQProvider;
import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@RequiredArgsConstructor
@Service
@Primary
public class RocketMQDLQProvider implements DLQProvider {

    private static final long ONE_HOUR_MILLIS = 3600_000L;
    private static final int RESEND_HARD_CAP = 5000;
    private static final int MAX_CONSECUTIVE_OFFSET_ILLEGAL = 3;
    private static final String ORIGIN_MESSAGE_ID_PROPERTY = "studio_dlq_origin_message_id";
    private static final String ORIGIN_TOPIC_PROPERTY = "studio_dlq_origin_topic";

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final AuditService auditService;

    @Override
    public List<DLQGroupVO> listDLQGroups(String instanceId) {
        return runtimeAdminClientResolver.execute(instanceId, this::listDLQGroups);
    }

    private List<DLQGroupVO> listDLQGroups(MQAdminExt adminExt) throws Exception {
        Set<String> topics;
        TopicList topicList = adminExt.fetchAllTopicList();
        topics = topicList == null ? Collections.emptySet() : topicList.getTopicList();

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

    private DLQGroupVO buildDLQGroup(MQAdminExt adminExt, String groupName, String dlqTopic) {
        long messageCount = 0L;
        LocalDateTime lastEnqueueTime = null;
        boolean statsAvailable = true;
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
            statsAvailable = false;
            log.warn("Failed to examine stats for DLQ topic {}: {}", dlqTopic, e.getMessage());
        }

        return DLQGroupVO.builder()
                .groupName(groupName)
                .dlqTopic(dlqTopic)
                .messageCount(messageCount)
                .lastEnqueueTime(lastEnqueueTime)
                .retryCount(0)
                .status(statsAvailable ? (messageCount > 0 ? "ACTIVE" : "EMPTY") : "UNAVAILABLE")
                .statsAvailable(statsAvailable)
                .build();
    }

    @Override
    public DLQResendResultVO resendMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                             String targetTopic) {
        String endpoint = runtimeAdminClientResolver.resolveEndpoint(instanceId);
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;

        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;

        List<MessageExt> deadLetters = collectDeadLetters(endpoint, dlqTopic, begin, end);
        int resent = 0;
        int failed = 0;
        if (!deadLetters.isEmpty()) {
            DefaultMQProducer producer = newProducer(endpoint);
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

        String detail = String.format("instanceId=%s, group=%s, dlqTopic=%s, targetTopic=%s, matched=%d, resent=%d, failed=%d",
                instanceId, groupName, dlqTopic, StringUtils.hasText(targetTopic) ? targetTopic : "<original>",
                deadLetters.size(), resent, failed);
        recordAudit(groupName, detail, classifyOutcome(deadLetters.size(), resent, failed));
        log.info("DLQ resend completed: {}", detail);
        return DLQResendResultVO.builder()
                .matched(deadLetters.size())
                .resent(resent)
                .failed(failed)
                .outcome(classifyOutcome(deadLetters.size(), resent, failed))
                .build();
    }

    private List<MessageExt> collectDeadLetters(String endpoint, String dlqTopic, long begin, long end) {
        DefaultMQPullConsumer consumer = newPullConsumer(endpoint);
        List<MessageExt> result = new ArrayList<>();
        try {
            consumer.start();
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(dlqTopic);
            if (queues == null || queues.isEmpty()) {
                return result;
            }
            outer:
            for (MessageQueue queue : queues) {
                // A single queue failing (e.g. an illegal offset under concurrent consumption)
                // must not abort the whole DLQ scan silently; skip it and keep collecting the rest.
                try {
                    long minOffset = consumer.searchOffset(queue, begin);
                    long maxOffset = consumer.searchOffset(queue, end);
                    int consecutiveIllegalOffsets = 0;
                    for (long offset = minOffset; offset <= maxOffset; ) {
                        if (result.size() >= RESEND_HARD_CAP) {
                            break outer;
                        }
                        PullResult pullResult = consumer.pull(queue, "*", offset, 32);
                        if (pullResult == null) {
                            log.warn("Stop DLQ scan for {} because queue {} returned no pull result", dlqTopic, queue);
                            break;
                        }
                        long nextOffset = pullResult.getNextBeginOffset();
                        if (nextOffset <= offset) {
                            log.warn("Stop DLQ scan for {} because queue {} did not advance offset {}",
                                    dlqTopic, queue, offset);
                            break;
                        }
                        offset = nextOffset;
                        if (pullResult.getPullStatus() == PullStatus.OFFSET_ILLEGAL) {
                            // The broker returned a corrected offset in nextBeginOffset because
                            // the requested offset is no longer valid (expired, compacted, or
                            // before the queue's minimum offset). Retry from the corrected
                            // position instead of abandoning the queue -- otherwise dead-letter
                            // messages that still exist after the corrected offset are silently
                            // dropped and never resent.
                            if (++consecutiveIllegalOffsets > MAX_CONSECUTIVE_OFFSET_ILLEGAL) {
                                log.warn("Stop DLQ scan for {} because queue {} returned OFFSET_ILLEGAL "
                                        + "{} times consecutively, giving up at offset {}", dlqTopic, queue,
                                        consecutiveIllegalOffsets, offset);
                                break;
                            }
                            log.debug("Offset was illegal for queue {} in DLQ {}, retrying from {}",
                                    queue, dlqTopic, offset);
                            continue;
                        }
                        if (pullResult.getPullStatus() != PullStatus.FOUND
                                || pullResult.getMsgFoundList() == null) {
                            break;
                        }
                        consecutiveIllegalOffsets = 0;
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
                } catch (Exception e) {
                    log.warn("Failed to scan DLQ queue {} in {}: {}", queue, dlqTopic, e.getMessage());
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
            // Copy user properties from the original message, skipping system-reserved
            // keys to avoid conflicts with broker-internal properties.
            Map<String, String> userProperties = deadLetter.getProperties();
            if (userProperties != null) {
                for (Map.Entry<String, String> entry : userProperties.entrySet()) {
                    String key = entry.getKey();
                    if (!MessageConst.STRING_HASH_SET.contains(key)) {
                        message.putUserProperty(key, entry.getValue());
                    }
                }
            }
            message.putUserProperty(ORIGIN_MESSAGE_ID_PROPERTY, deadLetter.getMsgId());
            message.putUserProperty(ORIGIN_TOPIC_PROPERTY, deadLetter.getTopic());
            SendResult sendResult = producer.send(message);
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                log.warn("DLQ resend was not accepted: msgId={} topic={} sendStatus={}",
                        deadLetter.getMsgId(), destination,
                        sendResult == null ? "<null>" : sendResult.getSendStatus());
                return false;
            }
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

    private DefaultMQPullConsumer newPullConsumer(String endpoint) {
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("studio-dlq-query-group");
        consumer.setInstanceName(ShortLivedClientName.next("studio-dlq-query"));
        consumer.setNamesrvAddr(endpoint);
        return consumer;
    }

    private DefaultMQProducer newProducer(String endpoint) {
        DefaultMQProducer producer = new DefaultMQProducer(nextResendProducerGroup());
        producer.setInstanceName(ShortLivedClientName.next("studio-dlq-resend"));
        producer.setRetryTimesWhenSendFailed(2);
        producer.setNamesrvAddr(endpoint);
        return producer;
    }

    static String nextResendProducerGroup() {
        return ShortLivedClientName.next("studio-dlq-resend");
    }

    private String classifyOutcome(int matched, int resent, int failed) {
        if (matched == 0) {
            return "NO_MESSAGES";
        }
        if (resent == 0 && failed > 0) {
            return "FAILED";
        }
        if (failed > 0) {
            return "PARTIAL";
        }
        return "SUCCESS";
    }

    private void recordAudit(String groupName, String detail, String result) {
        try {
            auditService.record("RESEND_DLQ", groupName, detail, result);
        } catch (Exception e) {
            log.warn("Failed to record DLQ resend audit: {}", e.getMessage());
        }
    }
}
