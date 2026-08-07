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
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.message.QueryHistoryService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final char FIELD_SEPARATOR = '\u0001';
    private static final int KEY_QUERY_MAX = 64;
    private static final int TRACE_QUERY_MAX = 64;
    private static final int DEFAULT_TOPIC_LIMIT = 200;
    private static final int TOPIC_QUERY_HARD_CAP = 2000;
    private static final int MAX_BODY_DISPLAY_BYTES = 64 * 1024;
    private static final int MAX_BINARY_BODY_DISPLAY_BYTES = 48 * 1024;
    private static final int MAX_PROPERTIES = 64;
    private static final int MAX_PROPERTY_VALUE_CHARS = 1024;
    private static final long ONE_HOUR_MILLIS = 3600_000L;
    private static final long ONE_DAY_MILLIS = 24 * ONE_HOUR_MILLIS;

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final QueryHistoryService queryHistoryService;

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId, String tag, String key,
                                               Long startTime, Long endTime) {
        String endpoint = runtimeAdminClientResolver.resolveEndpoint(instanceId);
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> queryMessages(instanceId, (DefaultMQAdminExt) adminExt, endpoint,
                        topic, msgId, tag, key, startTime, endTime));
    }

    private List<MessageRecordVO> queryMessages(String instanceId, DefaultMQAdminExt adminExt, String endpoint,
                                                 String topic, String msgId, String tag, String key,
                                                 Long startTime, Long endTime) {

        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;

        List<MessageRecordVO> result;
        String queryType;
        if (StringUtils.hasText(msgId)) {
            queryType = "MSG_ID";
            result = queryByMsgId(adminExt, topic, msgId);
        } else if (StringUtils.hasText(topic) && StringUtils.hasText(key)) {
            queryType = "KEY";
            result = queryByKey(adminExt, topic, key, tag, begin, end);
        } else if (StringUtils.hasText(topic)) {
            queryType = "TOPIC";
            result = queryByTopic(endpoint, topic, tag, begin, end, DEFAULT_TOPIC_LIMIT);
        } else {
            log.warn("queryMessages requires at least one of msgId/topic, returning empty list");
            return Collections.emptyList();
        }

        recordMessageQuery(instanceId, queryType, topic, msgId, tag, key, startTime, endTime, result.size());
        return result;
    }

    private List<MessageRecordVO> queryByMsgId(DefaultMQAdminExt adminExt, String topic, String msgId) {
        MessageExt messageExt = null;
        if (StringUtils.hasText(topic)) {
            try {
                messageExt = adminExt.viewMessage(topic, msgId);
            } catch (Exception e) {
                log.warn("viewMessage(topic={}, msgId={}) failed: {}", topic, msgId, e.getMessage());
            }
        }
        if (messageExt == null) {
            messageExt = viewMessageByOffsetId(adminExt, msgId);
        }
        if (messageExt == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(toRecordVO(messageExt));
    }

    /**
     * Locate a message purely by its offset msgId by decoding the broker address embedded in the
     * id and querying that broker directly. Used when no topic hint is available.
     */
    private MessageExt viewMessageByOffsetId(DefaultMQAdminExt adminExt, String msgId) {
        try {
            MessageId messageId = MessageDecoder.decodeMessageId(msgId);
            SocketAddress address = messageId.getAddress();
            if (!(address instanceof InetSocketAddress)) {
                return null;
            }
            InetSocketAddress inet = (InetSocketAddress) address;
            String brokerAddr = inet.getAddress().getHostAddress() + ":" + inet.getPort();
            return adminExt.getDefaultMQAdminExtImpl()
                    .getMqClientInstance()
                    .getMQClientAPIImpl()
                    .viewMessage(brokerAddr, msgId, 3000L, 3000L);
        } catch (Exception e) {
            log.warn("viewMessage by decoded offset id failed for msgId={}: {}", msgId, e.getMessage());
            return null;
        }
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

    /**
     * Scan a topic within a time range using a short-lived pull consumer, mirroring the approach
     * used by the RocketMQ dashboard for time-range topic queries.
     */
    private List<MessageRecordVO> queryByTopic(String endpoint, String topic, String tag, long begin, long end, int limit) {
        DefaultMQPullConsumer consumer = newPullConsumer("studio-msg-query", endpoint);
        List<MessageRecordVO> result = new ArrayList<>();
        try {
            consumer.start();
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(topic);
            if (queues == null || queues.isEmpty()) {
                return result;
            }
            outer:
            for (MessageQueue queue : queues) {
                long minOffset = consumer.searchOffset(queue, begin);
                long maxOffset = consumer.searchOffset(queue, end);
                for (long offset = minOffset; offset <= maxOffset; ) {
                    if (result.size() >= Math.min(limit, TOPIC_QUERY_HARD_CAP)) {
                        break outer;
                    }
                    PullResult pullResult = consumer.pull(queue, "*", offset, 32);
                    if (pullResult == null) {
                        log.warn("Stop topic query for {} because queue {} returned no pull result", topic, queue);
                        break;
                    }
                    long nextOffset = pullResult.getNextBeginOffset();
                    if (nextOffset <= offset) {
                        log.warn("Stop topic query for {} because queue {} did not advance offset {}", topic, queue, offset);
                        break;
                    }
                    offset = nextOffset;
                    if (pullResult.getPullStatus() != PullStatus.FOUND
                            || pullResult.getMsgFoundList() == null) {
                        break;
                    }
                    for (MessageExt messageExt : pullResult.getMsgFoundList()) {
                        if (messageExt.getStoreTimestamp() < begin
                                || messageExt.getStoreTimestamp() > end) {
                            continue;
                        }
                        if (!matchesTag(messageExt, tag)) {
                            continue;
                        }
                        result.add(toRecordVO(messageExt));
                        if (result.size() >= Math.min(limit, TOPIC_QUERY_HARD_CAP)) {
                            break outer;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("queryByTopic(topic={}) failed: {}", topic, e.getMessage());
            throw new BusinessException(502, "Failed to query messages by topic: " + e.getMessage());
        } finally {
            consumer.shutdown();
        }
        return result;
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId) {
        return runtimeAdminClientResolver.execute(instanceId,
                adminExt -> getMessageTrace(instanceId, (DefaultMQAdminExt) adminExt, msgId));
    }

    private TraceRecordVO getMessageTrace(String instanceId, DefaultMQAdminExt adminExt, String msgId) {

        long now = System.currentTimeMillis();
        long begin = now - ONE_HOUR_MILLIS;
        long end = now + 60_000L;

        List<TraceNodeVO> nodes = new ArrayList<>();
        List<ConsumerStatusVO> consumerStatus = new ArrayList<>();

        try {
            QueryResult traceResult = adminExt.queryMessage(TRACE_TOPIC, msgId, TRACE_QUERY_MAX, begin, end);
            if (traceResult != null && traceResult.getMessageList() != null) {
                for (MessageExt traceMessage : traceResult.getMessageList()) {
                    parseTraceBody(traceMessage.getBody(), msgId, nodes, consumerStatus);
                }
            }
        } catch (Exception e) {
            log.warn("Trace query for msgId={} failed: {}", msgId, e.getMessage());
        }

        recordTraceQuery(instanceId, msgId, null, nodes.size(), consumerStatus.size());
        return TraceRecordVO.builder()
                .nodes(nodes)
                .consumerStatus(consumerStatus)
                .build();
    }

    /**
     * Parse a trace message body. Each line is one trace context whose fields are separated by
     * the SOH character ({@code \u0001}); the first field is the trace type.
     */
    private void parseTraceBody(byte[] body, String targetMsgId, List<TraceNodeVO> nodes,
                                List<ConsumerStatusVO> consumerStatus) {
        if (body == null || body.length == 0) {
            return;
        }
        String data = new String(body, StandardCharsets.UTF_8);
        for (String line : data.split("\n")) {
            if (!StringUtils.hasText(line)) {
                continue;
            }
            String[] fields = line.split(String.valueOf(FIELD_SEPARATOR), -1);
            if (fields.length == 0) {
                continue;
            }
            String traceType = fields[0].trim();
            // The message id column differs by trace type: Pub/EndTransaction encode the trace bean
            // (msgId at index 5), while SubAfter in RocketMQ 5.3.3 places msgId at index 2.
            int msgIdIndex = "SubAfter".equals(traceType) ? 2 : 5;
            if (!targetMsgId.equals(field(fields, msgIdIndex))) {
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
                    default:
                        // SubBefore and unknown types are not surfaced as timeline nodes.
                        break;
                }
            } catch (Exception e) {
                log.debug("Skipping unparseable trace line: {}", e.getMessage());
            }
        }
    }

    // Pub layout (RocketMQ 5.3.3 TraceDataEncoder): type, time, region, group, topic, msgId,
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

    // SubAfter layout (RocketMQ 5.3.3 TraceDataEncoder): type, requestId, msgId, costTime,
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

    // EndTransaction layout (RocketMQ 5.3.3): type, time, region, group, topic, msgId, tags,
    //                     keys, storeHost, bodyLength, costTime, msgType, transactionId, txState
    private TraceNodeVO buildTransactionNode(String[] f) {
        return TraceNodeVO.builder()
                .title("endTransaction")
                .timestamp(parseLong(field(f, 1)))
                .status("finish")
                .costTime(0L)
                .description("group=" + field(f, 3) + ", transactionState=" + field(f, 13))
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
        byte[] body = messageExt.getBody();
        DisplayBody displayBody = displayBody(body);
        Map<String, String> properties = messageExt.getProperties();
        Map<String, String> displayProperties = limitProperties(properties);
        return MessageRecordVO.builder()
                .msgId(messageExt.getMsgId())
                .topic(messageExt.getTopic())
                .tag(messageExt.getTags())
                .key(messageExt.getKeys())
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

    private DefaultMQPullConsumer newPullConsumer(String groupPrefix, String endpoint) {
        DefaultMQPullConsumer consumer = new DefaultMQPullConsumer(groupPrefix + "-group");
        consumer.setInstanceName(ShortLivedClientName.next(groupPrefix));
        consumer.setNamesrvAddr(endpoint);
        return consumer;
    }

    private void recordMessageQuery(String instanceId, String queryType, String topic, String msgId, String tag, String key,
                                    Long startTime, Long endTime, int resultCount) {
        try {
            queryHistoryService.recordMessageQuery(instanceId, queryType, topic, msgId, tag, key,
                    startTime, endTime, resultCount);
        } catch (Exception e) {
            log.warn("Failed to record message query history: {}", e.getMessage());
        }
    }

    private void recordTraceQuery(String instanceId, String msgId, String topic, int nodeCount, int consumerCount) {
        try {
            queryHistoryService.recordTraceQuery(instanceId, msgId, topic, nodeCount, consumerCount);
        } catch (Exception e) {
            log.warn("Failed to record trace query history: {}", e.getMessage());
        }
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
}
