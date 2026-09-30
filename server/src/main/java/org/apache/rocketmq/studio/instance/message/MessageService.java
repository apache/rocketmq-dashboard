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
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Resource;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Kind;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private static final long MAX_TOPIC_QUERY_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int TOPIC_QUERY_RESULT_LIMIT = 200;

    private final MessageProvider messageProvider;
    private final InstanceProviderRegistry providerRegistry;
    private final QueryHistoryService queryHistoryService;
    private final OperationAuditService operationAuditService;
    private final ResourceOwnershipGuard ownershipGuard;

    public List<MessageRecordVO> queryMessages(
            String instanceId, String topic, String msgId, String tag, String key, Long startTime, Long endTime) {
        return queryMessagesDetailed(instanceId, topic, msgId, tag, key, startTime, endTime, true)
                .messages();
    }

    private MessageQueryResult queryMessagesDetailed(
            String instanceId, String topic, String msgId, String tag, String key,
            Long startTime, Long endTime, boolean recordHistory) {
        validateTopicQueryWindow(topic, msgId, key, startTime, endTime);
        log.info("Querying messages: topic={}, msgId={}, tag={}, key={}", topic, msgId, tag, key);
        MessageQueryResult result = providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.queryMessagesDetailed(instanceId, topic, msgId, tag,
                        key, startTime, endTime))
                .orElseGet(() -> messageProvider.queryMessagesDetailed(instanceId, topic, msgId,
                        tag, key, startTime, endTime));
        if (recordHistory) {
            recordMessageQuery(instanceId, topic, msgId, tag, key, startTime, endTime, result.messages());
        }
        return result;
    }

    public List<MessageRecordVO> queryMessageByUniqueKey(
            String instanceId, String topic, String uniqueKey, Long startTime, Long endTime) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "topic is required");
        }
        if (!StringUtils.hasText(uniqueKey)) {
            throw new BusinessException(400, "uniqueKey is required");
        }
        validateProvidedTimeWindow(startTime, endTime);
        log.info("Querying message by unique key: topic={}, uniqueKey={}", topic, uniqueKey);
        return providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.queryMessageByUniqueKey(instanceId, topic, uniqueKey, startTime, endTime))
                .orElseGet(() -> messageProvider.queryMessageByUniqueKey(
                        instanceId, topic, uniqueKey, startTime, endTime));
    }

    public MessageQueryPageVO queryMessagesPage(String instanceId, String topic, String msgId, String tag,
                                                 String key, Long startTime, Long endTime, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "page must be positive and pageSize must be between 1 and 200");
        }
        // The Apache / Aliyun / Tencent providers all cap topic-keyed scans at a fixed
        // broker-side limit (see RocketMQMessageProvider#DEFAULT_TOPIC_LIMIT). RocketMQ has
        // no server-side "skip the first N messages" API, so the in-memory subList below is
        // the page slice within that cap; the total / resultMayBeTruncated fields let the UI
        // tell the user when deeper pages may be empty.
        MessageQueryResult queryResult = queryMessagesDetailed(
                instanceId, topic, msgId, tag, key, startTime, endTime, page == 1);
        List<MessageRecordVO> result = queryResult.messages();
        long offset = (long) (page - 1) * pageSize;
        int from = (int) Math.min(offset, result.size());
        int to = Math.min(from + pageSize, result.size());
        boolean topicQuery = StringUtils.hasText(topic) && !StringUtils.hasText(msgId)
                && !StringUtils.hasText(key);
        return MessageQueryPageVO.builder().items(result.subList(from, to)).total(result.size()).page(page)
                .size(pageSize).resultMayBeTruncated(queryResult.mayBeTruncated()
                        || topicQuery && result.size() >= TOPIC_QUERY_RESULT_LIMIT).build();
    }

    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic) {
        if (!StringUtils.hasText(msgId)) {
            throw new BusinessException(400, "msgId is required");
        }
        log.info("Getting message trace: msgId={}, topic={}", msgId, topic);
        TraceRecordVO result = providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.getMessageTrace(instanceId, msgId, topic))
                .orElseGet(() -> messageProvider.getMessageTrace(instanceId, msgId, topic));
        recordTraceQuery(instanceId, msgId, topic, null, result);
        return result;
    }

    public List<QueueOffsetVO> getQueueOffsets(String instanceId, String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "topic is required");
        }
        return providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.getQueueOffsets(instanceId, topic))
                .orElseGet(() -> messageProvider.getQueueOffsets(instanceId, topic));
    }

    public MessageRecordVO pullMessageAtOffset(String instanceId, String topic, String brokerName,
                                                int queueId, long offset) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "topic is required");
        }
        if (!StringUtils.hasText(brokerName)) {
            throw new BusinessException(400, "brokerName is required");
        }
        if (queueId < 0) {
            throw new BusinessException(400, "queueId must not be negative");
        }
        if (offset < 0) {
            throw new BusinessException(400, "offset must not be negative");
        }
        return providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.pullMessageAtOffset(instanceId, topic, brokerName, queueId, offset))
                .orElseGet(() -> messageProvider.pullMessageAtOffset(
                        instanceId, topic, brokerName, queueId, offset));
    }

    public DirectConsumeMessageResultVO consumeMessageDirectly(DirectConsumeMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Direct consume request must not be null");
        }
        var instance = ownershipGuard.requireInstance(request.getInstanceId());
        request.setInstanceId(instance.getName());
        request.setTopic(ResourceOwnershipGuard.requireText(request.getTopic(), "topicName"));
        request.setConsumerGroup(ResourceOwnershipGuard.requireText(request.getConsumerGroup(), "groupName"));
        request.setClientId(ResourceOwnershipGuard.requireText(request.getClientId(), "clientId"));
        request.setMsgId(ResourceOwnershipGuard.requireText(request.getMsgId(), "msgId"));
        // Ownership guard applies to open-source Apache instances only; cloud calls are isolated by their own instance id.
        boolean apache = instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE;
        List<Resource> resources = List.of(new Resource(Kind.GROUP, request.getConsumerGroup()),
                ownershipGuard.topicResource(request.getTopic()));
        if (apache) {
            resources.forEach(resource -> ownershipGuard.check(instance, resource, true));
            ownershipGuard.requireSupportedProvider(instance);
        }
        String detail = "topic=" + request.getTopic() + ", consumerGroup=" + request.getConsumerGroup()
                + ", clientId=" + request.getClientId();
        try {
            DirectConsumeMessageResultVO result = apache
                    ? ownershipGuard.withOwned(instance, resources, () ->
                            providerRegistry.byInstanceId(request.getInstanceId())
                                    .map(provider -> consumeDirectlyThrough(provider, request))
                                    .orElseGet(() -> messageProvider.consumeMessageDirectly(request)))
                    : providerRegistry.byInstanceId(request.getInstanceId())
                            .map(provider -> consumeDirectlyThrough(provider, request))
                            .orElseGet(() -> messageProvider.consumeMessageDirectly(request));
            recordDirectConsumeAudit(request, detail + ", result=" + result.getConsumeResult(),
                    auditResult(result.getConsumeResult()), null);
            return result;
        } catch (RuntimeException e) {
            recordDirectConsumeAudit(request, detail, "FAILED", e.getMessage());
            throw e;
        }
    }

    /**
     * Gate the registered-provider path on the advertised capability so a vendor without a
     * direct-consume API answers 501 (see {@code GlobalExceptionHandler}) instead of letting the
     * call fall through to an implementation that cannot honour it.
     */
    private static DirectConsumeMessageResultVO consumeDirectlyThrough(InstanceProvider provider,
                                                                       DirectConsumeMessageDTO request) {
        if (!provider.capabilities().contains(InstanceCapability.DIRECT_MESSAGE_CONSUME)) {
            throw new UnsupportedOperationException(
                    "Direct message consumption is not supported by this instance");
        }
        return provider.consumeMessageDirectly(request);
    }

    /** consumeResult mirrors the broker-side CMResult enum, where only CR_SUCCESS means consumed. */
    private static String auditResult(String consumeResult) {
        return "CR_SUCCESS".equals(consumeResult) ? "SUCCESS" : "FAILED";
    }

    private void recordDirectConsumeAudit(DirectConsumeMessageDTO request, String detail,
                                          String result, String errorInfo) {
        try {
            operationAuditService.record("DIRECT_CONSUME_MESSAGE", "MESSAGE", request.getMsgId(),
                    request.getInstanceId(), detail, result, errorInfo);
        } catch (RuntimeException auditFailure) {
            log.warn("Failed to record direct-consume audit: {}", auditFailure.getMessage());
        }
    }

    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic, String traceTopic) {
        if (!StringUtils.hasText(msgId)) {
            throw new BusinessException(400, "msgId is required");
        }
        String normalizedTraceTopic = normalizeOptional(traceTopic);
        if (normalizedTraceTopic == null) {
            // No custom trace topic: fall back to the legacy 3-arg path so providers that
            // only implement message-id tracing (Aliyun/Tencent) keep working unchanged.
            return getMessageTrace(instanceId, msgId, topic);
        }
        log.info("Getting message trace: msgId={}, topic={}, traceTopic={}", msgId, topic,
                normalizedTraceTopic);
        TraceRecordVO result = providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.getMessageTrace(instanceId, msgId, topic, normalizedTraceTopic))
                .orElseGet(() -> messageProvider.getMessageTrace(instanceId, msgId, topic, normalizedTraceTopic));
        recordTraceQuery(instanceId, msgId, topic, normalizedTraceTopic, result);
        return result;
    }

    public TraceRecordVO getMessageTraceByKey(String instanceId, String key, String topic, String traceTopic) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(400, "key is required");
        }
        String normalizedTraceTopic = normalizeOptional(traceTopic);
        log.info("Getting message trace by key: key={}, topic={}, traceTopic={}", key, topic,
                normalizedTraceTopic);
        // Trace query history is keyed by message id; key-based lookups are intentionally
        // not recorded so the key is not misreported as a message id.
        return providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.getMessageTraceByKey(instanceId, key, topic, normalizedTraceTopic))
                .orElseGet(() -> messageProvider.getMessageTraceByKey(instanceId, key, topic, normalizedTraceTopic));
    }
    private void recordMessageQuery(String instanceId, String topic, String msgId, String tag,
                                    String key, Long startTime, Long endTime, List<MessageRecordVO> result) {
        String queryType = StringUtils.hasText(msgId) ? "MSG_ID" : StringUtils.hasText(key) ? "KEY" : "TOPIC";
        try {
            String snapshot = queryHistoryService.buildResultSnapshot(result);
            queryHistoryService.recordMessageQuery(instanceId, queryType, topic, msgId, tag, key,
                    startTime, endTime, result.size(), snapshot);
        } catch (RuntimeException failure) {
            log.warn("Failed to record message query history: {}", failure.getMessage());
        }
    }

    private void recordTraceQuery(String instanceId, String msgId, String topic, String traceTopic,
                                  TraceRecordVO result) {
        int nodeCount = result == null || result.getNodes() == null ? 0 : result.getNodes().size();
        int consumerCount = result == null || result.getConsumerStatus() == null ? 0
                : result.getConsumerStatus().size();
        try {
            queryHistoryService.recordTraceQuery(instanceId, msgId, topic, traceTopic, nodeCount, consumerCount);
        } catch (RuntimeException failure) {
            log.warn("Failed to record trace query history: {}", failure.getMessage());
        }
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Rejects a supplied window that cannot describe a real lookup, for the two lookups whose
     * window is optional and therefore not covered by {@link #validateTopicQueryWindow}: the key
     * branch of {@code queryMessages} and {@code queryMessageByUniqueKey}. Both used to forward a
     * negated or inverted window to the provider, which either rejected it with a message of its
     * own or passed it on to the broker / cloud API, so a malformed request had no single
     * documented answer. No length bound is applied: the documented seven-day maximum belongs to
     * topic scans only.
     */
    private static void validateProvidedTimeWindow(Long startTime, Long endTime) {
        if (startTime != null && startTime < 0) {
            throw new BusinessException(400, "message query timestamps must not be negative");
        }
        if (endTime != null && endTime < 0) {
            throw new BusinessException(400, "message query timestamps must not be negative");
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new BusinessException(400, "startTime must be before endTime");
        }
    }

    private void validateTopicQueryWindow(String topic, String msgId, String key, Long startTime, Long endTime) {
        boolean hasTopic = StringUtils.hasText(topic);
        boolean hasMessageId = StringUtils.hasText(msgId);
        boolean hasKey = StringUtils.hasText(key);
        if (hasKey && !hasTopic) {
            throw new BusinessException(400, "topic is required when key is specified");
        }
        if (hasMessageId && !hasTopic) {
            throw new BusinessException(400, "topic is required when msgId is specified");
        }
        if (!hasTopic && !hasMessageId) {
            throw new BusinessException(400, "topic or msgId is required");
        }
        if (hasMessageId || hasKey) {
            // A message id is a point lookup and never needs a window. A key query may carry an
            // explicit one, so it is validated here without the topic-scan length bound.
            if (hasKey) {
                validateProvidedTimeWindow(startTime, endTime);
            }
            return;
        }
        long end = endTime == null ? System.currentTimeMillis() : endTime;
        long start = startTime == null ? end - 60 * 60 * 1000L : startTime;
        if (start < 0 || end < 0) {
            throw new BusinessException(400, "message query timestamps must not be negative");
        }
        if (start >= end) {
            throw new BusinessException(400, "startTime must be before endTime");
        }
        // Compare without subtracting untrusted endpoints; end - start can overflow long.
        if (start < end - MAX_TOPIC_QUERY_WINDOW_MILLIS) {
            throw new BusinessException(400, "topic query time range must not exceed 7 days");
        }
    }
}
