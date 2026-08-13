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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

    private static final long MAX_TOPIC_QUERY_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final MessageProvider messageProvider;
    private final InstanceProviderRegistry providerRegistry;
    private final QueryHistoryService queryHistoryService;

    public List<MessageRecordVO> queryMessages(
            String instanceId, String topic, String msgId, String tag, String key, Long startTime, Long endTime) {
        validateTopicQueryWindow(topic, msgId, key, startTime, endTime);
        log.info("Querying messages: topic={}, msgId={}, tag={}, key={}", topic, msgId, tag, key);
        List<MessageRecordVO> result = providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.queryMessages(instanceId, topic, msgId, tag, key, startTime, endTime))
                .orElseGet(() -> messageProvider.queryMessages(instanceId, topic, msgId, tag, key, startTime, endTime));
        recordMessageQuery(instanceId, topic, msgId, tag, key, startTime, endTime, result.size());
        return result;
    }

    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic) {
        if (!StringUtils.hasText(msgId)) {
            throw new BusinessException(400, "msgId is required");
        }
        log.info("Getting message trace: msgId={}, topic={}", msgId, topic);
        TraceRecordVO result = providerRegistry.byInstanceId(instanceId)
                .map(provider -> provider.getMessageTrace(instanceId, msgId, topic))
                .orElseGet(() -> messageProvider.getMessageTrace(instanceId, msgId, topic));
        recordTraceQuery(instanceId, msgId, topic, result);
        return result;
    }

    private void recordMessageQuery(String instanceId, String topic, String msgId, String tag,
                                    String key, Long startTime, Long endTime, int resultCount) {
        String queryType = StringUtils.hasText(msgId) ? "MSG_ID" : StringUtils.hasText(key) ? "KEY" : "TOPIC";
        try {
            queryHistoryService.recordMessageQuery(instanceId, queryType, topic, msgId, tag, key,
                    startTime, endTime, resultCount);
        } catch (RuntimeException failure) {
            log.warn("Failed to record message query history: {}", failure.getMessage());
        }
    }

    private void recordTraceQuery(String instanceId, String msgId, String topic, TraceRecordVO result) {
        int nodeCount = result == null || result.getNodes() == null ? 0 : result.getNodes().size();
        int consumerCount = result == null || result.getConsumerStatus() == null ? 0
                : result.getConsumerStatus().size();
        try {
            queryHistoryService.recordTraceQuery(instanceId, msgId, topic, nodeCount, consumerCount);
        } catch (RuntimeException failure) {
            log.warn("Failed to record trace query history: {}", failure.getMessage());
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
