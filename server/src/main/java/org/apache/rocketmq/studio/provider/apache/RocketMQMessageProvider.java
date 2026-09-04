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
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.trace.TraceConstants;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageResultVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.QueueOffsetVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.apache.rocketmq.tools.admin.api.TrackType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final int TRACE_QUERY_MAX = 64;
    private static final int DEFAULT_TOPIC_LIMIT = 200;
    private static final int TOPIC_QUERY_HARD_CAP = 2000;
    private static final int TOPIC_PULL_BATCH_SIZE = 32;
    private static final int MAX_BODY_DISPLAY_BYTES = 64 * 1024;
    private static final int MAX_BINARY_BODY_DISPLAY_BYTES = 48 * 1024;
    private static final int MAX_PROPERTIES = 64;
    private static final int MAX_PROPERTY_VALUE_CHARS = 1024;
    private static final long VIEW_MESSAGE_TIMEOUT_MILLIS = 3000L;
    private static final long ONE_HOUR_MILLIS = 3600_000L;
    private static final long ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS;
    private static final long MAX_TOPIC_QUERY_WINDOW_MILLIS = 7 * ONE_DAY_MILLIS;
    private static final int MAX_PULLS_PER_QUEUE = 32;
    private static final int MAX_CONSECUTIVE_OFFSET_ILLEGAL = 3;
    private static final int MAX_TOPIC_SCAN_MESSAGES_PER_QUEUE = MAX_PULLS_PER_QUEUE * TOPIC_PULL_BATCH_SIZE;
    private static final int MAX_PULL_ATTEMPTS_PER_QUEUE = MAX_PULLS_PER_QUEUE + MAX_CONSECUTIVE_OFFSET_ILLEGAL;
    private static final Comparator<MessageRecordVO> TOPIC_QUERY_ORDER = Comparator
            .comparingLong(MessageRecordVO::getStoreTime)
            .thenComparing(MessageRecordVO::getMsgId, Comparator.nullsFirst(String::compareTo));

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId, String tag, String key,
                                               Long startTime, Long endTime) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> queryMessages(instanceId, (DefaultMQAdminExt) adminExt, topic, msgId, tag, key,
                        startTime, endTime));
    }

    private List<MessageRecordVO> queryMessages(String instanceId, DefaultMQAdminExt adminExt,
                                                 String topic, String msgId, String tag, String key,
                                                 Long startTime, Long endTime) {

        if (StringUtils.hasText(msgId)) {
            return queryByMsgId(adminExt, topic, msgId);
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
            return queryByTopic(instanceId, topic, tag, begin, end, DEFAULT_TOPIC_LIMIT);
        }

        log.warn("queryMessages requires at least one of msgId/topic, returning empty list");
        return Collections.emptyList();
    }

    private List<MessageRecordVO> queryByMsgId(DefaultMQAdminExt adminExt, String topic, String msgId) {
        MessageExt messageExt = null;
        if (StringUtils.hasText(topic)) {
            try {
                if (isWithinKnownBrokerTopology(adminExt, msgId)) {
                    messageExt = adminExt.viewMessage(topic, msgId);
                }
            } catch (Exception e) {
                log.warn("viewMessage(topic={}, msgId={}) failed: {}", topic, msgId, e.getMessage());
            }
        }
        if (messageExt == null) {
            messageExt = viewMessageByOffsetId(adminExt, topic, msgId);
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
        try {
            MessageId messageId = MessageDecoder.decodeMessageId(msgId);
            String brokerAddr = validatedBrokerAddr(adminExt, msgId, messageId);
            if (!StringUtils.hasText(brokerAddr)) {
                return null;
            }
            return adminExt.getDefaultMQAdminExtImpl()
                    .getMqClientInstance()
                    .getMQClientAPIImpl()
                    .viewMessage(brokerAddr, topic, messageId.getOffset(), VIEW_MESSAGE_TIMEOUT_MILLIS);
        } catch (Exception e) {
            log.warn("viewMessage by decoded offset id failed for msgId={}: {}", msgId, e.getMessage());
            return null;
        }
    }

    private String validatedBrokerAddr(DefaultMQAdminExt adminExt, String msgId, MessageId messageId) throws Exception {
        String brokerAddr = decodedBrokerAddr(messageId);
        if (!StringUtils.hasText(brokerAddr)) {
            return null;
        }
        if (knownBrokerEndpoints(adminExt).contains(brokerAddr)) {
            return brokerAddr;
        }
        log.warn("Rejecting decoded broker address {} for msgId={} because it is not a known broker endpoint"
                + " for the selected instance", brokerAddr, msgId);
        return null;
    }

    /**
     * Offset-style message ids embed a broker address that {@code MQAdminImpl#viewMessage} connects
     * to directly, so ids whose embedded address is outside the selected instance topology must be
     * rejected before that call. Ids that do not decode as offset ids take MQAdminImpl's unique-key
     * lookup, which resolves brokers from the topic route and needs no guard. When the topology
     * itself cannot be verified, reject too — mirroring the fallback path's behavior — instead of
     * handing an unverified address to remoting.
     */
    private boolean isWithinKnownBrokerTopology(DefaultMQAdminExt adminExt, String msgId) {
        MessageId messageId;
        try {
            messageId = MessageDecoder.decodeMessageId(msgId);
        } catch (Exception e) {
            return true;
        }
        try {
            return validatedBrokerAddr(adminExt, msgId, messageId) != null;
        } catch (Exception e) {
            log.warn("Could not verify broker topology for msgId={}: {}", msgId, e.getMessage());
            return false;
        }
    }

    private String decodedBrokerAddr(MessageId messageId) {
        SocketAddress address = messageId.getAddress();
        if (!(address instanceof InetSocketAddress)) {
            return null;
        }
        InetSocketAddress inet = (InetSocketAddress) address;
        if (inet.getAddress() == null) {
            return null;
        }
        return inet.getAddress().getHostAddress() + ":" + inet.getPort();
    }

    private Set<String> knownBrokerEndpoints(DefaultMQAdminExt adminExt) throws Exception {
        ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null
                || clusterInfo.getBrokerAddrTable().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> endpoints = new HashSet<>();
        for (BrokerData brokerData : clusterInfo.getBrokerAddrTable().values()) {
            if (brokerData == null || brokerData.getBrokerAddrs() == null || brokerData.getBrokerAddrs().isEmpty()) {
                continue;
            }
            for (String brokerAddr : brokerData.getBrokerAddrs().values()) {
                if (StringUtils.hasText(brokerAddr)) {
                    endpoints.add(brokerAddr.trim());
                }
            }
        }
        return endpoints;
    }

    private List<MessageRecordVO> queryByKey(DefaultMQAdminExt adminExt, String topic, String key,
                                             String tag, long begin, long end) {
        try {
            QueryResult queryResult = adminExt.queryMessage(topic, key, KEY_QUERY_MAX, begin, end);
            if (queryResult == null || queryResult.getMessageList() == null) {
                return Collections.emptyList();
            }
            List<MessageRecordVO> result = new ArrayList<>();
            for (MessageExt messageExt : queryResult.getMessageList()) {
                if (matchesTag(messageExt, tag)) {
                    result.add(toRecordVO(messageExt));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("queryMessage(topic={}, key={}) failed: {}", topic, key, e.getMessage());
            throw new BusinessException(502, "Failed to query messages by key: " + e.getMessage());
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
                    for (int queueId = 0; queueId < queueData.getWriteQueueNums(); queueId++) {
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
                log.warn("queryByTopic(topic={}) failed: {}", topic, e.getMessage());
                throw new BusinessException(502, "Failed to query messages by topic: " + e.getMessage());
            }
            return newestMessages.stream()
                    .sorted(TOPIC_QUERY_ORDER.reversed())
                    .toList();
        });
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
        return runtimeAdminClientResolver.execute(request.getInstanceId(), admin -> {
            org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult result =
                    ((DefaultMQAdminExt) admin).consumeMessageDirectly(request.getConsumerGroup(), request.getClientId(),
                            request.getTopic(), request.getMsgId());
            return DirectConsumeMessageResultVO.builder()
                    .consumeResult(result.getConsumeResult() == null ? "UNKNOWN" : result.getConsumeResult().name())
                    .remark(result.getRemark()).spentTimeMillis(result.getSpentTimeMills())
                    .order(result.isOrder()).autoCommit(result.isAutoCommit()).build();
        });
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
                    parseTraceBody(traceMessage.getBody(), msgId, nodes, consumerStatus, true);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            if (isTraceTopicAbsent(e)) {
                // The cluster has no trace topic route (trace dispatch disabled): the RPC
                // succeeded but there is no business data, so return an empty trace instead
                // of surfacing an error (exception-grading convention).
                log.info("Trace topic not available on this cluster (msgId={}), returning empty trace", msgId);
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

    private static boolean isTraceTopicAbsent(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof MQClientException clientException
                    && clientException.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    /**
     * Trace lookup by business key. The key query already scopes the returned trace messages to
     * the requested message, so the body parser does not filter on a message id. The original
     * message topic is not required to query the global trace topic but is kept in the signature
     * for API symmetry and logged for diagnostics.
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
                    parseTraceBody(traceMessage.getBody(), null, nodes, consumerStatus, false);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
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
                if (isWithinKnownBrokerTopology(adminExt, msgId)) {
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
     * {@code targetMsgId} are kept; otherwise every context is parsed (used by key lookups where
     * the query already scoped the trace messages to the requested key).
     */
    private void parseTraceBody(byte[] body, String targetMsgId, List<TraceNodeVO> nodes,
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

    private List<ConsumerStatusVO> fallbackConsumerStatus(DefaultMQAdminExt adminExt, MessageExt message) {
        List<ConsumerStatusVO> result = new ArrayList<>();
        try {
            List<MessageTrack> tracks = adminExt.messageTrackDetail(message);
            if (tracks == null) {
                return result;
            }
            for (MessageTrack track : tracks) {
                result.add(ConsumerStatusVO.builder()
                        .group(track.getConsumerGroup())
                        .deliveryStatus(mapTrackType(track.getTrackType()))
                        .consumeTime(0L)
                        .retryCount(0)
                        .build());
            }
        } catch (Exception e) {
            log.warn("messageTrackDetail fallback failed for msgId={}: {}", message.getMsgId(), e.getMessage());
        }
        return result;
    }

    private DeliveryStatus mapTrackType(TrackType trackType) {
        if (trackType == null) {
            return DeliveryStatus.pending;
        }
        switch (trackType) {
            case CONSUMED:
            case CONSUME_BROADCASTING:
            case CONSUMED_BUT_FILTERED:
                return DeliveryStatus.success;
            case NOT_CONSUME_YET:
            case PULL:
            case NOT_ONLINE:
                return DeliveryStatus.pending;
            default:
                return DeliveryStatus.failed;
        }
    }

    MessageRecordVO toRecordVO(MessageExt messageExt) {
        return toRecordVO(messageExt, null);
    }

    MessageRecordVO toRecordVO(MessageExt messageExt, String brokerName) {
        byte[] body = messageExt.getBody();
        DisplayBody displayBody = displayBody(body);
        Map<String, String> properties = messageExt.getProperties();
        Map<String, String> displayProperties = limitProperties(properties);
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
                .properties(displayProperties)
                .propertiesTruncated(properties != null && (displayProperties.size() < properties.size()
                        || hasOversizedProperty(properties)))
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

    private Map<String, String> limitProperties(Map<String, String> properties) {
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> limited = new LinkedHashMap<>();
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsLast(String::compareTo)))
                .limit(MAX_PROPERTIES)
                .forEach(entry -> limited.put(entry.getKey(), abbreviate(entry.getValue(), MAX_PROPERTY_VALUE_CHARS)));
        return limited;
    }

    private boolean hasOversizedProperty(Map<String, String> properties) {
        return properties != null && properties.values().stream()
                .anyMatch(value -> value != null && value.length() > MAX_PROPERTY_VALUE_CHARS);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
