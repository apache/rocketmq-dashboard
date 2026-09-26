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

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.trace.TraceConstants;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Kind;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Resource;
import org.apache.rocketmq.studio.common.util.MessagePropertyDisplay;
import org.apache.rocketmq.studio.common.util.MqResponseCodes;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.MessageQueryResult;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageResultVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.QueueOffsetVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Real {@link MessageProvider} backed by the RocketMQ admin API. Supports message lookup by
 * message id, by business key and by topic + time range, as well as message trace retrieval.
 * Falls back to empty results when adminExt is not configured or a query fails.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Primary
public class RocketMQMessageProvider implements MessageProvider {

    private static final String TRACE_TOPIC = "RMQ_SYS_TRACE_TOPIC";
    private static final int KEY_QUERY_MAX = 64;
    private static final int UNIQUE_KEY_QUERY_MAX = 1;
    private static final int TRACE_QUERY_MAX = 64;
    private static final int DEFAULT_TOPIC_LIMIT = 200;
    private static final int TOPIC_QUERY_HARD_CAP = 2000;
    private static final int TOPIC_PULL_BATCH_SIZE = 32;
    private static final int MAX_BODY_DISPLAY_BYTES = 64 * 1024;
    private static final int MAX_BINARY_BODY_DISPLAY_BYTES = 48 * 1024;
    private static final long VIEW_MESSAGE_TIMEOUT_MILLIS = 3000L;
    private static final long ONE_HOUR_MILLIS = 3600_000L;
    private static final long ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS;
    private static final long MAX_TOPIC_QUERY_WINDOW_MILLIS = 7 * ONE_DAY_MILLIS;
    private static final long UNIQUE_KEY_DEFAULT_WINDOW_MILLIS = 3 * ONE_DAY_MILLIS;
    private static final int MAX_PULLS_PER_QUEUE = 32;
    private static final int MAX_CONSECUTIVE_OFFSET_ILLEGAL = 3;
    private static final int MAX_TOPIC_SCAN_MESSAGES_PER_QUEUE = MAX_PULLS_PER_QUEUE * TOPIC_PULL_BATCH_SIZE;
    private static final int MAX_PULL_ATTEMPTS_PER_QUEUE = MAX_PULLS_PER_QUEUE + MAX_CONSECUTIVE_OFFSET_ILLEGAL;
    private static final Comparator<MessageRecordVO> TOPIC_QUERY_ORDER = Comparator
            .comparingLong(MessageRecordVO::getStoreTime)
            .thenComparing(MessageRecordVO::getMsgId, Comparator.nullsFirst(String::compareTo));

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final ResourceOwnershipGuard ownershipGuard;

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId, String tag, String key,
                                               Long startTime, Long endTime) {
        return queryMessagesDetailed(instanceId, topic, msgId, tag, key, startTime, endTime).messages();
    }

    @Override
    public MessageQueryResult queryMessagesDetailed(String instanceId, String topic, String msgId, String tag,
                                                    String key, Long startTime, Long endTime) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> queryMessages(instanceId, (DefaultMQAdminExt) adminExt, topic, msgId, tag, key,
                        startTime, endTime));
    }

    private MessageQueryResult queryMessages(String instanceId, DefaultMQAdminExt adminExt,
                                             String topic, String msgId, String tag, String key,
                                             Long startTime, Long endTime) {

        if (StringUtils.hasText(msgId)) {
            return MessageQueryResult.complete(queryByMsgId(adminExt, topic, msgId));
        }

        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        if (begin >= end) {
            throw new BusinessException(400, "Message query start time must be before end time");
        }
        if (StringUtils.hasText(topic) && StringUtils.hasText(key)) {
            return queryByKey(adminExt, topic, key, tag, begin, end);
        }
        if (StringUtils.hasText(topic)) {
            if (begin >= 0 && end >= 0 && end - begin > MAX_TOPIC_QUERY_WINDOW_MILLIS) {
                throw new BusinessException(400, "Topic message query time range must not exceed 7 days");
            }
            return MessageQueryResult.complete(
                    queryByTopic(instanceId, topic, tag, begin, end, DEFAULT_TOPIC_LIMIT));
        }

        log.warn("queryMessages requires at least one of msgId/topic, returning empty list");
        return MessageQueryResult.complete(Collections.emptyList());
    }

    private List<MessageRecordVO> queryByMsgId(DefaultMQAdminExt adminExt, String topic, String msgId) {
        MessageExt messageExt = null;
        Exception primaryFailure = null;
        if (StringUtils.hasText(topic)) {
            if (!BrokerTopologyGuards.isWithinKnownBrokerTopology(adminExt, msgId)) {
                return Collections.emptyList();
            }
            try {
                messageExt = adminExt.viewMessage(topic, msgId);
            } catch (Exception e) {
                if (isMessageLookupAbsent(e)) {
                    return Collections.emptyList();
                }
                primaryFailure = e;
                log.warn("viewMessage(topic={}, msgId={}) failed: {}", topic, msgId, e.getMessage());
            }
        }
        if (messageExt == null) {
            OffsetMessageLookup lookup = lookupMessageByOffsetId(adminExt, topic, msgId);
            messageExt = lookup.message();
            if (messageExt == null && lookup.failure() != null) {
                throw messageLookupFailure(lookup.failure());
            }
        }
        if (messageExt == null && primaryFailure != null) {
            throw messageLookupFailure(primaryFailure);
        }
        if (messageExt == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toRecordVO(messageExt));
    }

    /**
     * Locate a message by decoding the broker address and physical offset embedded in its offset
     * msgId, then querying that broker directly.
     */
    private MessageExt viewMessageByOffsetId(DefaultMQAdminExt adminExt, String topic, String msgId) {
        OffsetMessageLookup lookup = lookupMessageByOffsetId(adminExt, topic, msgId);
        if (lookup.failure() != null) {
            log.warn("viewMessage by decoded offset id failed for msgId={}: {}",
                    msgId, lookup.failure().getMessage());
        }
        return lookup.message();
    }

    private OffsetMessageLookup lookupMessageByOffsetId(DefaultMQAdminExt adminExt, String topic, String msgId) {
        MessageId messageId;
        try {
            messageId = MessageDecoder.decodeMessageId(msgId);
        } catch (Exception exception) {
            return OffsetMessageLookup.empty();
        }
        try {
            String brokerAddr = BrokerTopologyGuards.validatedBrokerAddr(adminExt, msgId, messageId);
            if (!StringUtils.hasText(brokerAddr)) {
                return OffsetMessageLookup.empty();
            }
            MessageExt message = adminExt.getDefaultMQAdminExtImpl()
                    .getMqClientInstance()
                    .getMQClientAPIImpl()
                    .viewMessage(brokerAddr, topic, messageId.getOffset(), VIEW_MESSAGE_TIMEOUT_MILLIS);
            return new OffsetMessageLookup(message, null);
        } catch (Exception exception) {
            if (isMessageLookupAbsent(exception)) {
                return OffsetMessageLookup.empty();
            }
            return new OffsetMessageLookup(null, exception);
        }
    }

    private static boolean isMessageLookupAbsent(Throwable throwable) {
        return MqResponseCodes.hasResponseCode(throwable, ResponseCode.TOPIC_NOT_EXIST,
                ResponseCode.NO_MESSAGE, ResponseCode.QUERY_NOT_FOUND);
    }

    private static BusinessException messageLookupFailure(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getClass().getSimpleName();
        }
        return new BusinessException(502, "Failed to query message by id: " + message);
    }

    private MessageQueryResult queryByKey(DefaultMQAdminExt adminExt, String topic, String key,
                                          String tag, long begin, long end) {
        try {
            QueryResult queryResult = adminExt.queryMessage(topic, key, KEY_QUERY_MAX, begin, end);
            if (queryResult == null || queryResult.getMessageList() == null) {
                return MessageQueryResult.complete(Collections.emptyList());
            }
            // MQAdminImpl fans the key query out to every route broker with a per-broker
            // budget of KEY_QUERY_MAX and merges the responses without a client-side cap,
            // so a merged count at or above the budget means some broker may have stopped
            // at its cap. Surface that instead of silently dropping further matches.
            boolean mayBeTruncated = queryResult.getMessageList().size() >= KEY_QUERY_MAX;
            List<MessageRecordVO> result = new ArrayList<>();
            for (MessageExt messageExt : queryResult.getMessageList()) {
                if (matchesTag(messageExt, tag)) {
                    result.add(toRecordVO(messageExt));
                }
            }
            return mayBeTruncated ? MessageQueryResult.truncated(result) : MessageQueryResult.complete(result);
        } catch (Exception e) {
            if (MqResponseCodes.hasResponseCode(e, ResponseCode.NO_MESSAGE)) {
                // MQAdminImpl.queryMessage throws MQClientException(NO_MESSAGE) instead of
                // returning an empty QueryResult when the key matches nothing: the query
                // completed, so the correct response is an empty list, not a gateway error.
                log.info("queryMessage(topic={}, key={}) matched nothing", topic, key);
                return MessageQueryResult.complete(Collections.emptyList());
            }
            log.warn("queryMessage(topic={}, key={}) failed: {}", topic, key, e.getMessage());
            throw new BusinessException(502, "Failed to query messages by key: " + e.getMessage());
        }
    }

    @Override
    public List<MessageRecordVO> queryMessageByUniqueKey(String instanceId, String topic, String uniqueKey,
                                                         Long startTime, Long endTime) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> queryMessageByUniqueKey((DefaultMQAdminExt) adminExt, topic, uniqueKey,
                        startTime, endTime));
    }

    private List<MessageRecordVO> queryMessageByUniqueKey(DefaultMQAdminExt adminExt, String topic,
                                                          String uniqueKey, Long startTime, Long endTime) {
        try {
            if (startTime == null && endTime == null) {
                // Two-arg MQAdmin lookup: UNIQ_KEY index over a default recent 3-day window.
                MessageExt messageExt = adminExt.getDefaultMQAdminExtImpl()
                        .getMqClientInstance()
                        .getMQAdminImpl()
                        .queryMessageByUniqKey(topic, uniqueKey);
                return messageExt == null ? Collections.emptyList() : List.of(toRecordVO(messageExt));
            }
            long end = endTime != null ? endTime : System.currentTimeMillis();
            long begin = startTime != null ? startTime : end - UNIQUE_KEY_DEFAULT_WINDOW_MILLIS;
            QueryResult queryResult = adminExt.queryMessageByUniqKey(null, topic, uniqueKey,
                    UNIQUE_KEY_QUERY_MAX, begin, end);
            if (queryResult == null || queryResult.getMessageList() == null
                    || queryResult.getMessageList().isEmpty()) {
                return Collections.emptyList();
            }
            return List.of(toRecordVO(queryResult.getMessageList().getFirst()));
        } catch (Exception e) {
            if (MqResponseCodes.hasResponseCode(e, ResponseCode.NO_MESSAGE, ResponseCode.QUERY_NOT_FOUND)) {
                // The index query completed but matched nothing: empty result, not a gateway error.
                log.info("queryMessageByUniqKey(topic={}, uniqueKey={}) matched nothing", topic, uniqueKey);
                return Collections.emptyList();
            }
            log.warn("queryMessageByUniqKey(topic={}, uniqueKey={}) failed: {}", topic, uniqueKey, e.getMessage());
            throw new BusinessException(502, "Failed to query message by unique key: " + e.getMessage());
        }
    }

    @Override
    public List<QueueOffsetVO> getQueueOffsets(String instanceId, String topic) {
        return runtimeAdminClientResolver.execute(instanceId, adminExt -> {
            List<QueueOffsetVO> result = new ArrayList<>();
            try {
                TopicRouteData route = adminExt.examineTopicRouteInfo(topic);
                if (route == null || route.getQueueDatas() == null) {
                    return Collections.emptyList();
                }
                for (QueueData queueData : route.getQueueDatas()) {
                    for (int queueId = 0; queueId < queueData.getReadQueueNums(); queueId++) {
                        MessageQueue queue = new MessageQueue(topic, queueData.getBrokerName(), queueId);
                        result.add(QueueOffsetVO.builder()
                                .brokerName(queue.getBrokerName())
                                .queueId(queue.getQueueId())
                                .minOffset(adminExt.minOffset(queue))
                                .maxOffset(adminExt.maxOffset(queue))
                                .build());
                    }
                }
                result.sort(Comparator.comparing(QueueOffsetVO::getBrokerName)
                        .thenComparingInt(QueueOffsetVO::getQueueId));
            } catch (Exception e) {
                if (MqResponseCodes.hasResponseCode(e, ResponseCode.TOPIC_NOT_EXIST)) {
                    log.info("getQueueOffsets(topic={}) matched nothing ({}), returning empty list", topic, e.getMessage());
                    return Collections.emptyList();
                }
                log.warn("getQueueOffsets(topic={}) failed: {}", topic, e.getMessage());
                throw new BusinessException(502, "Failed to get queue offsets: " + e.getMessage());
            }
            return result;
        });
    }

    @Override
    public MessageRecordVO pullMessageAtOffset(String instanceId, String topic, String brokerName,
                                                int queueId, long offset) {
        return runtimeAdminClientResolver.executePullConsumer(instanceId, consumer -> {
            try {
                MessageQueue queue = new MessageQueue(topic, brokerName, queueId);
                PullResult pullResult = consumer.pull(queue, "*", offset, 1);
                if (pullResult == null || pullResult.getPullStatus() != PullStatus.FOUND
                        || pullResult.getMsgFoundList() == null || pullResult.getMsgFoundList().isEmpty()) {
                    return null;
                }
                return toRecordVO(pullResult.getMsgFoundList().get(0), brokerName);
            } catch (Exception e) {
                if (isRetryTopicReadBlocked(topic, e)) {
                    log.warn("pullMessageAtOffset(topic={}) skipped: reading a %RETRY% topic requires a "
                            + "group-matched pull consumer; returning empty. cause={}", topic, e.getMessage());
                    return null;
                }
                log.warn("pullMessageAtOffset(topic={}, broker={}, queue={}, offset={}) failed: {}",
                        topic, brokerName, queueId, offset, e.getMessage());
                throw new BusinessException(502, "Failed to pull message at offset: " + e.getMessage());
            }
        });
    }

    /**
     * Scan a topic within a time range on the pooled long-lived pull consumer, mirroring the
     * approach used by the RocketMQ dashboard for time-range topic queries.
     */
    private List<MessageRecordVO> queryByTopic(String instanceId, String topic, String tag,
                                                long begin, long end, int limit) {
        int resultLimit = Math.min(limit, TOPIC_QUERY_HARD_CAP);
        return runtimeAdminClientResolver.executePullConsumer(instanceId, consumer -> {
            PriorityQueue<MessageRecordVO> newestMessages = new PriorityQueue<>(TOPIC_QUERY_ORDER);
            try {
                Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(topic);
                if (queues == null || queues.isEmpty()) {
                    return Collections.emptyList();
                }
                for (MessageQueue queue : queues) {
                    TopicQueueScanPlan scanPlan = buildTopicQueueScanPlan(consumer, queue, begin, end);
                    if (scanPlan.isEmpty()) {
                        continue;
                    }
                    if (scanPlan.truncated()) {
                        log.info("Truncate topic query for {} queue {} to offsets [{}..{}) within the guarded tail budget",
                                topic, queue, scanPlan.startOffset(), scanPlan.endOffsetExclusive());
                    }
                    int consecutiveIllegalOffsets = 0;
                    int pullAttempts = 0;
                    for (long offset = scanPlan.startOffset(); offset < scanPlan.endOffsetExclusive(); ) {
                        if (++pullAttempts > MAX_PULL_ATTEMPTS_PER_QUEUE) {
                            log.warn("Stop topic query for {} because queue {} exhausted the guarded pull budget at offset {}",
                                    topic, queue, offset);
                            break;
                        }
                        PullResult pullResult = consumer.pull(queue, "*", offset, TOPIC_PULL_BATCH_SIZE);
                        if (pullResult == null) {
                            log.warn("Stop topic query for {} because queue {} returned no pull result", topic, queue);
                            break;
                        }
                        long nextOffset = pullResult.getNextBeginOffset();
                        if (nextOffset <= offset) {
                            log.warn("Stop topic query for {} because queue {} did not advance offset {}", topic, queue, offset);
                            break;
                        }
                        offset = Math.min(nextOffset, scanPlan.endOffsetExclusive());
                        if (pullResult.getPullStatus() == PullStatus.OFFSET_ILLEGAL) {
                            // The broker returned a corrected offset in nextBeginOffset because
                            // the requested offset is no longer valid (expired, compacted, or
                            // before the queue's minimum offset). Retry from the corrected
                            // position instead of abandoning the queue -- otherwise messages
                            // that still exist after the corrected offset are silently dropped.
                            if (++consecutiveIllegalOffsets > MAX_CONSECUTIVE_OFFSET_ILLEGAL) {
                                log.warn("Stop topic query for {} because queue {} returned OFFSET_ILLEGAL "
                                        + "{} times consecutively, giving up at offset {}", topic, queue,
                                        consecutiveIllegalOffsets, offset);
                                break;
                            }
                            log.debug("Offset was illegal for queue {} in topic {}, retrying from {}",
                                    queue, topic, offset);
                            continue;
                        }
                        if (pullResult.getPullStatus() != PullStatus.FOUND
                                || pullResult.getMsgFoundList() == null) {
                            break;
                        }
                        consecutiveIllegalOffsets = 0;
                        for (MessageExt messageExt : pullResult.getMsgFoundList()) {
                            if (messageExt.getStoreTimestamp() < begin
                                    || messageExt.getStoreTimestamp() > end) {
                                continue;
                            }
                            if (!matchesTag(messageExt, tag)) {
                                continue;
                            }
                            addTopicQueryCandidate(newestMessages, toRecordVO(messageExt, queue.getBrokerName()), resultLimit);
                        }
                    }
                }
            } catch (Exception e) {
                if (isRetryTopicReadBlocked(topic, e)) {
                    // Reading a %RETRY%<group> topic through the shared pooled pull consumer is
                    // rejected by broker ACL (the pull consumer group must equal the retry topic's
                    // group). We deliberately do NOT spin up a group-matched pull consumer: it would
                    // register as a member of that real group and take part in its push-consumer
                    // rebalance, stalling queues. Degrade to an empty result instead of failing.
                    log.warn("queryByTopic(topic={}) skipped: reading a %RETRY% topic requires a "
                            + "group-matched pull consumer; returning empty. cause={}", topic, e.getMessage());
                    return Collections.emptyList();
                }
                if (MqResponseCodes.hasResponseCode(e, ResponseCode.TOPIC_NOT_EXIST, ResponseCode.NO_MESSAGE)) {
                    log.info("queryByTopic(topic={}) matched nothing ({}), returning empty list", topic, e.getMessage());
                    return Collections.emptyList();
                }
                log.warn("queryByTopic(topic={}) failed: {}", topic, e.getMessage());
                throw new BusinessException(502, "Failed to query messages by topic: " + e.getMessage());
            }
            return newestMessages.stream()
                    .sorted(TOPIC_QUERY_ORDER.reversed())
                    .toList();
        });
    }

    /**
     * True only when {@code topic} is a {@code %RETRY%<group>} system topic and {@code e} carries one
     * of the broker signals that the shared pooled pull consumer cannot read it: the ACL rejection
     * {@code retry topic does not match consumer group} (CODE:16, because the pull consumer group must
     * equal the retry topic's embedded group) or a missing route/queue. Reading such a topic would
     * require a group-matched pull consumer, which we intentionally avoid (it would join that real
     * group's rebalance); callers degrade to an empty result instead. Normal topics and unrelated
     * errors return false so genuine failures still surface.
     */
    private static boolean isRetryTopicReadBlocked(String topic, Throwable e) {
        if (topic == null || !topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
            return false;
        }
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("retry topic does not match consumer group")
                        || lower.contains("can not find message queue")
                        || lower.contains("no topic route info")) {
                    return true;
                }
            }
            if (cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private TopicQueueScanPlan buildTopicQueueScanPlan(DefaultMQPullConsumer consumer, MessageQueue queue,
                                                       long begin, long end) throws Exception {
        long minOffset = consumer.minOffset(queue);
        long maxOffsetExclusive = consumer.maxOffset(queue);
        if (maxOffsetExclusive <= minOffset) {
            return TopicQueueScanPlan.empty();
        }
        long windowStartOffset = clampOffset(consumer.searchOffset(queue, begin), minOffset, maxOffsetExclusive);
        long windowEndOffsetExclusive = clampOffset(consumer.searchOffset(queue, inclusiveUpperBound(end)),
                windowStartOffset, maxOffsetExclusive);
        if (windowEndOffsetExclusive <= windowStartOffset) {
            return TopicQueueScanPlan.empty();
        }
        long budgetedStartOffset = Math.max(windowStartOffset,
                windowEndOffsetExclusive - MAX_TOPIC_SCAN_MESSAGES_PER_QUEUE);
        return new TopicQueueScanPlan(budgetedStartOffset, windowEndOffsetExclusive,
                budgetedStartOffset > windowStartOffset);
    }

    private long clampOffset(long offset, long minOffset, long maxOffsetExclusive) {
        return Math.max(minOffset, Math.min(offset, maxOffsetExclusive));
    }

    private long inclusiveUpperBound(long timestamp) {
        return timestamp == Long.MAX_VALUE ? Long.MAX_VALUE : timestamp + 1;
    }

    private void addTopicQueryCandidate(PriorityQueue<MessageRecordVO> newestMessages,
                                        MessageRecordVO candidate, int resultLimit) {
        if (resultLimit <= 0) {
            return;
        }
        if (newestMessages.size() < resultLimit) {
            newestMessages.offer(candidate);
            return;
        }
        MessageRecordVO oldestKept = newestMessages.peek();
        if (oldestKept != null && TOPIC_QUERY_ORDER.compare(candidate, oldestKept) > 0) {
            newestMessages.poll();
            newestMessages.offer(candidate);
        }
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic) {
        return getMessageTrace(instanceId, msgId, topic, null);
    }

    @Override
    public DirectConsumeMessageResultVO consumeMessageDirectly(DirectConsumeMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Direct consume request is required");
        }
        var instance = ownershipGuard.requireInstance(request.getInstanceId());
        String topic = ResourceOwnershipGuard.requireText(request.getTopic(), "topicName");
        String group = ResourceOwnershipGuard.requireText(request.getConsumerGroup(), "groupName");
        String client = ResourceOwnershipGuard.requireText(request.getClientId(), "clientId");
        String messageId = ResourceOwnershipGuard.requireText(request.getMsgId(), "msgId");
        Resource topicResource = ownershipGuard.topicResource(topic);
        Resource groupResource = new Resource(Kind.GROUP, group);
        ownershipGuard.check(instance, topicResource, true);
        ownershipGuard.check(instance, groupResource, true);
        return ownershipGuard.withOwned(instance, List.of(topicResource, groupResource), () ->
                runtimeAdminClientResolver.execute(instance.getName(), admin -> {
                    var topicOwner = ownershipGuard.check(instance, topicResource, true);
                    var groupOwner = ownershipGuard.check(instance, groupResource, true);
                    if (!topicOwner.clusterId().equals(groupOwner.clusterId())) {
                        throw new BusinessException(409, "Group and topic are not in the same target cluster");
                    }
                    var target = ApacheWriteTargetResolver.resolve(admin, instance, topicOwner.clusterId());
                    ApacheWriteTargetResolver.requireTopicRoute(admin, target, topic);
                    // offsetId can bypass topic routing and connect directly to a broker; verify the address first, then the message's actual ownership.
                    requireDirectMessageTarget(target, messageId, false);
                    MessageExt message = ((DefaultMQAdminExt) admin).viewMessage(topic, messageId);
                    if (message == null || !topic.equals(message.getTopic())) {
                        throw new BusinessException(409, "Message's actual topic does not match the authorized resource");
                    }
                    requireDirectMessageTarget(target, message.getMsgId(), true);
                    if (!(message.getStoreHost() instanceof java.net.InetSocketAddress host)
                            || host.getAddress() == null
                            || !target.masters().contains(host.getAddress().getHostAddress() + ":" + host.getPort())) {
                        throw new BusinessException(409, "Message's store broker is not in the target master set");
                    }
                    var result = ((DefaultMQAdminExt) admin).consumeMessageDirectly(group, client,
                            topic, message.getMsgId());
                    return DirectConsumeMessageResultVO.builder()
                            .consumeResult(result.getConsumeResult() == null ? "UNKNOWN" : result.getConsumeResult().name())
                            .remark(result.getRemark()).spentTimeMillis(result.getSpentTimeMills())
                            .order(result.isOrder()).autoCommit(result.isAutoCommit()).build();
                }));
    }

    private void requireDirectMessageTarget(ApacheWriteTargetResolver.Target target, String messageId,
                                            boolean offsetRequired) {
        MessageId decoded;
        try {
            decoded = MessageDecoder.decodeMessageId(messageId);
        } catch (Exception invalid) {
            if (!offsetRequired) {
                return;
            }
            throw new BusinessException(409, "Unable to determine the physical location of the message");
        }
        String address = BrokerTopologyGuards.decodedBrokerAddr(decoded);
        if (address == null || !target.masters().contains(address)) {
            throw new BusinessException(409, "Message ID does not belong to a target cluster master");
        }
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic, String traceTopic) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> getMessageTrace(instanceId, (DefaultMQAdminExt) adminExt, msgId, topic, traceTopic));
    }

    @Override
    public TraceRecordVO getMessageTraceByKey(String instanceId, String key, String topic, String traceTopic) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> getMessageTraceByKey(instanceId, (DefaultMQAdminExt) adminExt, key, topic, traceTopic));
    }

    private TraceRecordVO getMessageTrace(String instanceId, DefaultMQAdminExt adminExt, String msgId, String topic,
                                          String traceTopic) {

        long now = System.currentTimeMillis();
        long begin;
        long end;
        long messageStoreTimestamp = resolveMessageStoreTimestamp(adminExt, msgId, topic);
        if (messageStoreTimestamp > 0) {
            // Derive the trace query window from the message's own store timestamp
            // instead of a hardcoded 1-hour lookback. This ensures traces for messages
            // older than 1 hour are still found as long as the trace data is retained
            // on the broker (default fileReservedTime = 72 hours).
            long traceBuffer = 5 * 60_000L;
            begin = messageStoreTimestamp - traceBuffer;
            end = Math.max(messageStoreTimestamp + ONE_DAY_MILLIS, now + 60_000L);
        } else {
            // Fallback: use the existing 1-hour window if the message can't be located
            log.warn("Could not resolve store timestamp for msgId={}, falling back to 1h trace window", msgId);
            begin = now - ONE_HOUR_MILLIS;
            end = now + 60_000L;
        }

        List<TraceNodeVO> nodes = new ArrayList<>();
        List<ConsumerStatusVO> consumerStatus = new ArrayList<>();

        try {
            QueryResult traceResult =
                    adminExt.queryMessage(effectiveTraceTopic(traceTopic), msgId, TRACE_QUERY_MAX, begin, end);
            if (traceResult != null && traceResult.getMessageList() != null) {
                for (MessageExt traceMessage : traceResult.getMessageList()) {
                    parseTraceBody(traceMessage.getBody(), msgId, null, nodes, consumerStatus, true);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (MqResponseCodes.hasResponseCode(e, ResponseCode.TOPIC_NOT_EXIST, ResponseCode.NO_MESSAGE)) {
                // The cluster has no trace topic route (trace dispatch disabled) or the
                // message simply has no trace records: the RPC succeeded but there is no
                // business data, so return an empty trace instead of surfacing an error
                // (exception-grading convention).
                log.info("No trace data available for msgId={} ({}), returning empty trace", msgId, e.getMessage());
                return emptyTrace();
            }
            log.warn("Trace query for msgId={} failed: {}", msgId, e.getMessage());
            throw new BusinessException(502, "Failed to query message trace: " + e.getMessage());
        }

        return TraceRecordVO.builder()
                .nodes(nodes)
                .consumerStatus(consumerStatus)
                .build();
    }

    /**
     * Trace lookup by business key. RocketMQ appends every trace context for one source topic
     * into the same trace message and indexes each business key on that message, so the query
     * result can contain contexts for other keys. Contexts are kept only when their keys column
     * contains {@code key} as a whole token. The original message topic is not required to query
     * the global trace topic but is kept in the signature for API symmetry and logged for
     * diagnostics.
     */
    private TraceRecordVO getMessageTraceByKey(String instanceId, DefaultMQAdminExt adminExt, String key,
                                               String topic, String traceTopic) {
        log.debug("Trace by key: key={}, originalTopic={}, traceTopic={}", key, topic,
                effectiveTraceTopic(traceTopic));
        long now = System.currentTimeMillis();
        // No message id to derive a precise window from; scan the last 24h of trace data.
        long begin = now - ONE_DAY_MILLIS;
        long end = now + 60_000L;

        List<TraceNodeVO> nodes = new ArrayList<>();
        List<ConsumerStatusVO> consumerStatus = new ArrayList<>();

        try {
            QueryResult traceResult =
                    adminExt.queryMessage(effectiveTraceTopic(traceTopic), key, TRACE_QUERY_MAX, begin, end);
            if (traceResult != null && traceResult.getMessageList() != null) {
                for (MessageExt traceMessage : traceResult.getMessageList()) {
                    parseTraceBody(traceMessage.getBody(), null, key, nodes, consumerStatus, false);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (MqResponseCodes.hasResponseCode(e, ResponseCode.TOPIC_NOT_EXIST, ResponseCode.NO_MESSAGE)) {
                log.info("No trace data available for key={} ({}), returning empty trace", key, e.getMessage());
                return emptyTrace();
            }
            log.warn("Trace query by key={} failed: {}", key, e.getMessage());
            throw new BusinessException(502, "Failed to query message trace by key: " + e.getMessage());
        }

        return TraceRecordVO.builder()
                .nodes(nodes)
                .consumerStatus(consumerStatus)
                .build();
    }

    private String effectiveTraceTopic(String traceTopic) {
        return StringUtils.hasText(traceTopic) ? traceTopic.trim() : TRACE_TOPIC;
    }

    /**
     * Attempts to resolve the store timestamp of the original message so the trace
     * query window can be derived from the message's own timeline rather than the
     * current time. Returns 0 if the message cannot be located.
     */
    private long resolveMessageStoreTimestamp(DefaultMQAdminExt adminExt, String msgId, String topic) {
        if (StringUtils.hasText(topic)) {
            try {
                MessageExt messageExt = null;
                if (BrokerTopologyGuards.isWithinKnownBrokerTopology(adminExt, msgId)) {
                    messageExt = adminExt.viewMessage(topic, msgId);
                }
                if (messageExt != null) {
                    return messageExt.getStoreTimestamp();
                }
            } catch (Exception e) {
                log.debug("Could not view message {} in topic {} for trace timestamp: {}",
                        msgId, topic, e.getMessage());
            }
        }
        try {
            MessageExt messageExt = viewMessageByOffsetId(adminExt, topic, msgId);
            if (messageExt != null) {
                return messageExt.getStoreTimestamp();
            }
        } catch (Exception e) {
            log.debug("Could not view message {} for trace timestamp: {}", msgId, e.getMessage());
        }
        return 0L;
    }

    /**
     * Parse a trace message body. Trace contexts are separated by STX ({@code \u0002}) and the
     * fields in each context are separated by SOH ({@code \u0001}); the first field is the trace
     * type. When {@code filterByMsgId} is true only contexts whose message id matches
     * {@code targetMsgId} are kept. When {@code targetKey} is non-null, only contexts whose keys
     * column contains that key as a whole token are kept. Message-id lookups pass a null key and
     * are not filtered by key.
     */
    private void parseTraceBody(byte[] body, String targetMsgId, String targetKey, List<TraceNodeVO> nodes,
                                List<ConsumerStatusVO> consumerStatus, boolean filterByMsgId) {
        if (body == null || body.length == 0) {
            return;
        }
        String data = new String(body, StandardCharsets.UTF_8);
        for (String context : data.split(String.valueOf(TraceConstants.FIELD_SPLITOR))) {
            if (!StringUtils.hasText(context)) {
                continue;
            }
            String[] fields = context.split(String.valueOf(TraceConstants.CONTENT_SPLITOR), -1);
            if (fields.length == 0) {
                continue;
            }
            String traceType = fields[0].trim();
            // The message ID column differs by trace type: Pub/EndTransaction place msgId at
            // index 5, while SubAfter places it at index 2 in RocketMQ 5.5.0.
            int msgIdIndex = "SubAfter".equals(traceType) ? 2 : 5;
            if (filterByMsgId && !targetMsgId.equals(field(fields, msgIdIndex))) {
                continue;
            }
            if (targetKey != null && !traceKeysContain(traceType, fields, targetKey)) {
                continue;
            }
            try {
                switch (traceType) {
                    case "Pub":
                        nodes.add(buildProduceNode(fields));
                        break;
                    case "SubAfter":
                        nodes.add(buildConsumeNode(fields));
                        consumerStatus.add(buildConsumerStatus(fields));
                        break;
                    case "EndTransaction":
                        nodes.add(buildTransactionNode(fields));
                        break;
                    case "Recall":
                        nodes.add(buildRecallNode(fields));
                        break;
                    default:
                        // SubBefore and unknown types are not surfaced as timeline nodes.
                        break;
                }
            } catch (Exception e) {
                log.debug("Skipping unparseable trace context: {}", e.getMessage());
            }
        }
    }

    /**
     * Keys column of a RocketMQ 5.5.0 trace context. Recall has no keys column, so a key lookup
     * cannot attribute it and the context is omitted. Pub, EndTransaction, and SubBefore store
     * keys at index 7; SubAfter stores them at index 5.
     */
    private static int traceKeysIndex(String traceType) {
        return switch (traceType) {
            case "Pub", "EndTransaction", "SubBefore" -> 7;
            case "SubAfter" -> 5;
            default -> -1;
        };
    }

    /**
     * Same token split {@code TraceDataEncoder} uses when it indexes a trace message:
     * {@code keys.split(MessageConst.KEY_SEPARATOR)} (a single space). {@code order-A} matches
     * {@code extra order-A} and does not match {@code order-A-suffix}.
     */
    private static boolean traceKeysContain(String traceType, String[] fields, String queryKey) {
        if (!StringUtils.hasText(queryKey)) {
            return false;
        }
        int keysIndex = traceKeysIndex(traceType);
        if (keysIndex < 0) {
            return false;
        }
        String keys = field(fields, keysIndex);
        if (!StringUtils.hasText(keys)) {
            return false;
        }
        for (String token : keys.split(MessageConst.KEY_SEPARATOR)) {
            if (queryKey.equals(token)) {
                return true;
            }
        }
        return false;
    }

    // Pub layout (RocketMQ 5.5.0 TraceDataEncoder):
    //             type, time, region, group, topic, msgId,
    //             tags, keys, storeHost, bodyLength, costTime, msgType, offsetMsgId, isSuccess
    private TraceNodeVO buildProduceNode(String[] f) {
        return TraceNodeVO.builder()
                .title("produce")
                .timestamp(parseLong(field(f, 1)))
                .status(parseBoolean(field(f, 13)) ? "finish" : "failed")
                .costTime(parseLong(field(f, 10)))
                .description("producer=" + field(f, 3) + ", storeHost=" + field(f, 8))
                .build();
    }

    // SubAfter layout (RocketMQ 5.5.0 TraceDataEncoder):
    //                type, requestId, msgId, costTime,
    //                isSuccess, keys, contextCode, timeStamp, groupName. The trailing
    //                timeStamp/groupName columns may be absent when the trace has no region info,
    //                so lookups tolerate short lines.
    private TraceNodeVO buildConsumeNode(String[] f) {
        return TraceNodeVO.builder()
                .title("consume")
                .timestamp(parseLong(field(f, 7)))
                .status(parseBoolean(field(f, 4)) ? "finish" : "failed")
                .costTime(parseLong(field(f, 3)))
                .description("group=" + field(f, 8) + ", contextCode=" + field(f, 6))
                .build();
    }

    private ConsumerStatusVO buildConsumerStatus(String[] f) {
        return ConsumerStatusVO.builder()
                .group(field(f, 8))
                .deliveryStatus(parseBoolean(field(f, 4)) ? DeliveryStatus.success : DeliveryStatus.failed)
                .consumeTime(parseLong(field(f, 7)))
                .retryCount(0)
                .build();
    }

    // EndTransaction layout (RocketMQ 5.5.0 TraceDataEncoder):
    //                     type, time, region, group, topic, msgId, tags,
    //                     keys, storeHost, msgType, transactionId, txState, fromTransactionCheck
    private TraceNodeVO buildTransactionNode(String[] f) {
        return TraceNodeVO.builder()
                .title("endTransaction")
                .timestamp(parseLong(field(f, 1)))
                .status("finish")
                .costTime(0L)
                .description("group=" + field(f, 3) + ", transactionState=" + field(f, 11))
                .build();
    }

    // Recall layout (RocketMQ 5.5.0 TraceDataEncoder):
    //               type, time, region, group, topic, msgId, isSuccess
    private TraceNodeVO buildRecallNode(String[] f) {
        return TraceNodeVO.builder()
                .title("recall")
                .timestamp(parseLong(field(f, 1)))
                .status(parseBoolean(field(f, 6)) ? "finish" : "failed")
                .costTime(0L)
                .description("group=" + field(f, 3) + ", topic=" + field(f, 4))
                .build();
    }

    MessageRecordVO toRecordVO(MessageExt messageExt) {
        return toRecordVO(messageExt, null);
    }

    MessageRecordVO toRecordVO(MessageExt messageExt, String brokerName) {
        byte[] body = messageExt.getBody();
        DisplayBody displayBody = displayBody(body);
        Map<String, String> properties = messageExt.getProperties();
        Map<String, String> displayProperties = MessagePropertyDisplay.limitProperties(properties);
        return MessageRecordVO.builder()
                .msgId(messageExt.getMsgId())
                .topic(messageExt.getTopic())
                .tag(messageExt.getTags())
                .key(messageExt.getKeys())
                .brokerName(brokerName)
                .queueId(messageExt.getQueueId())
                .queueOffset(messageExt.getQueueOffset())
                .body(displayBody.value())
                .bodyEncoding(displayBody.encoding())
                .bodyTruncated(displayBody.truncated())
                .storeTime(messageExt.getStoreTimestamp())
                .bornHost(String.valueOf(messageExt.getBornHost()))
                .storeHost(String.valueOf(messageExt.getStoreHost()))
                .reconsumeTimes(messageExt.getReconsumeTimes())
                .properties(displayProperties)
                .propertiesTruncated(properties != null && (displayProperties.size() < properties.size()
                        || MessagePropertyDisplay.hasOversizedProperty(properties)))
                .size(messageExt.getStoreSize())
                .build();
    }

    private DisplayBody displayBody(byte[] body) {
        if (body == null) {
            return new DisplayBody(null, null, false);
        }
        int textLength = Math.min(body.length, MAX_BODY_DISPLAY_BYTES);
        if (textLength < body.length && isUtf8ContinuationByte(body[textLength])) {
            while (textLength > 0 && isUtf8ContinuationByte(body[textLength])) {
                textLength--;
            }
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body, 0, textLength))
                    .toString();
            return new DisplayBody(value, "UTF-8", body.length > textLength);
        } catch (CharacterCodingException ignored) {
            int binaryLength = Math.min(body.length, MAX_BINARY_BODY_DISPLAY_BYTES);
            return new DisplayBody(Base64.getEncoder().encodeToString(
                    java.util.Arrays.copyOf(body, binaryLength)), "BASE64", body.length > binaryLength);
        }
    }

    private boolean isUtf8ContinuationByte(byte value) {
        return (value & 0xC0) == 0x80;
    }

    private record OffsetMessageLookup(MessageExt message, Throwable failure) {
        private static OffsetMessageLookup empty() {
            return new OffsetMessageLookup(null, null);
        }
    }

    private record DisplayBody(String value, String encoding, boolean truncated) {
    }

    private boolean matchesTag(MessageExt messageExt, String tag) {
        if (!StringUtils.hasText(tag) || "*".equals(tag)) {
            return true;
        }
        return tag.equals(messageExt.getTags());
    }

    private static TraceRecordVO emptyTrace() {
        return TraceRecordVO.builder()
                .nodes(Collections.emptyList())
                .consumerStatus(Collections.emptyList())
                .build();
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index] : "";
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private record TopicQueueScanPlan(long startOffset, long endOffsetExclusive, boolean truncated) {
        private static TopicQueueScanPlan empty() {
            return new TopicQueueScanPlan(0L, 0L, false);
        }

        private boolean isEmpty() {
            return endOffsetExclusive <= startOffset;
        }
    }
}
