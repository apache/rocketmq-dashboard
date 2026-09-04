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
import org.apache.rocketmq.common.topic.TopicValidator;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.common.util.SystemTopicFilter;
import org.apache.rocketmq.studio.instance.dlq.DLQExcelExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageExcelRow;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.instance.dlq.DLQProvider;
import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_CONSECUTIVE_OFFSET_ILLEGAL = 3;
    private static final int STATS_THREADS = 8;
    private static final long STATS_TIMEOUT_SECONDS = 5;
    private static final String ORIGIN_MESSAGE_ID_PROPERTY = "studio_dlq_origin_message_id";
    private static final String ORIGIN_TOPIC_PROPERTY = "studio_dlq_origin_topic";

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final AuditService auditService;

    private final ExecutorService statsExecutor = Executors.newFixedThreadPool(STATS_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "dlq-topic-stats");
        thread.setDaemon(true);
        return thread;
    });

    @jakarta.annotation.PreDestroy
    void shutdownStatsExecutor() {
        statsExecutor.shutdownNow();
    }

    @Override
    public List<DLQGroupVO> listDLQGroups(String instanceId) {
        return listDLQGroups(instanceId, null, 1, Integer.MAX_VALUE).getItems();
    }

    @Override
    public PageResult<DLQGroupVO> listDLQGroups(String instanceId, String search, int page, int pageSize) {
        return runtimeAdminClientResolver.execute(instanceId,
                admin -> listDLQGroups(admin, search, page, pageSize));
    }

    private PageResult<DLQGroupVO> listDLQGroups(MQAdminExt adminExt, String search, int page, int pageSize)
            throws Exception {
        TopicList topicList = adminExt.fetchAllTopicList();
        Set<String> topics = topicList == null ? Collections.emptySet() : topicList.getTopicList();
        String normalizedSearch = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;

        List<String> dlqTopics = new ArrayList<>();
        for (String topic : topics) {
            if (topic == null || !topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)) {
                continue;
            }
            String groupName = topic.substring(MixAll.DLQ_GROUP_TOPIC_PREFIX.length());
            if (!StringUtils.hasText(groupName)) {
                continue;
            }
            if (normalizedSearch == null
                    || groupName.toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    || topic.toLowerCase(Locale.ROOT).contains(normalizedSearch)) {
                dlqTopics.add(topic);
            }
        }
        dlqTopics.sort(Comparator.naturalOrder());
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, dlqTopics.size());
        int to = (int) Math.min(offset + pageSize, dlqTopics.size());
        List<String> pageTopics = dlqTopics.subList(from, to);
        List<DLQGroupVO> groups = loadGroupStatsInParallel(adminExt, pageTopics);
        return PageResult.of(groups, dlqTopics.size(), page, pageSize);
    }

    private List<DLQGroupVO> loadGroupStatsInParallel(MQAdminExt adminExt, List<String> pageTopics) {
        List<Future<DLQGroupVO>> futures = new ArrayList<>(pageTopics.size());
        for (String topic : pageTopics) {
            String groupName = topic.substring(MixAll.DLQ_GROUP_TOPIC_PREFIX.length());
            futures.add(statsExecutor.submit(() -> buildDLQGroup(adminExt, groupName, topic)));
        }
        List<DLQGroupVO> groups = new ArrayList<>(futures.size());
        for (Future<DLQGroupVO> future : futures) {
            groups.add(awaitGroupStats(future));
        }
        return groups;
    }

    private DLQGroupVO awaitGroupStats(Future<DLQGroupVO> future) {
        try {
            return future.get(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // buildDLQGroup reports single-topic failures inside the row itself.
        }
        return DLQGroupVO.builder().statsAvailable(false).status("UNAVAILABLE").build();
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
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required for DLQ resend");
        }
        groupName = groupName.trim();
        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        if (begin >= end) {
            throw new BusinessException(400, "DLQ resend start time must be before end time");
        }
        if (StringUtils.hasText(targetTopic)) {
            validateResendTargetTopic(instanceId, targetTopic);
        }

        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;

        DeadLetterScanResult scanResult;
        try {
            scanResult = collectDeadLetters(instanceId, dlqTopic, begin, end, RESEND_HARD_CAP);
        } catch (BusinessException e) {
            String detail = String.format("instanceId=%s, group=%s, dlqTopic=%s, targetTopic=%s, "
                            + "matched=0, resent=0, failed=0, scanIncomplete=true, scanFailedQueues=all",
                    instanceId, groupName, dlqTopic,
                    StringUtils.hasText(targetTopic) ? targetTopic : "<original>");
            recordAudit(groupName, detail, "FAILED");
            throw e;
        }
        List<MessageExt> deadLetters = scanResult.messages();
        int[] counts = {0, 0};
        if (!deadLetters.isEmpty()) {
            try {
                runtimeAdminClientResolver.executeProducer(instanceId, producer -> {
                    for (MessageExt deadLetter : deadLetters) {
                        if (resendOne(producer, deadLetter, targetTopic)) {
                            counts[0]++;
                        } else {
                            counts[1]++;
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("Failed to resend dead letters for group {}: {}", groupName, e.getMessage());
                counts[1] += deadLetters.size() - counts[0];
            }
        }
        int resent = counts[0];
        int failed = counts[1];

        String outcome = classifyOutcome(deadLetters.size(), resent, failed, scanResult.scanIncomplete());
        String detail = String.format("instanceId=%s, group=%s, dlqTopic=%s, targetTopic=%s, matched=%d, resent=%d, "
                        + "failed=%d, scanIncomplete=%s, scanTruncated=%s, scanFailedQueues=%d",
                instanceId, groupName, dlqTopic, StringUtils.hasText(targetTopic) ? targetTopic : "<original>",
                deadLetters.size(), resent, failed, scanResult.scanIncomplete(), scanResult.truncated(),
                scanResult.failedQueueCount());
        recordAudit(groupName, detail, outcome);
        log.info("DLQ resend completed: {}", detail);
        return DLQResendResultVO.builder()
                .matched(deadLetters.size())
                .resent(resent)
                .failed(failed)
                .outcome(outcome)
                .scanIncomplete(scanResult.scanIncomplete())
                .failedQueueCount(scanResult.failedQueueCount())
                .build();
    }

    @Override
    public DLQResendResultVO resendMessages(String instanceId, String groupName, List<String> msgIds,
                                             String targetTopic) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required for DLQ resend");
        }
        if (msgIds == null || msgIds.isEmpty()) {
            throw new BusinessException(400, "At least one msgId is required for selected DLQ resend");
        }
        groupName = groupName.trim();
        Set<String> selected = new LinkedHashSet<>();
        for (String msgId : msgIds) {
            if (StringUtils.hasText(msgId)) {
                selected.add(msgId.trim());
            }
        }
        if (selected.isEmpty()) {
            throw new BusinessException(400, "At least one valid msgId is required for selected DLQ resend");
        }
        if (StringUtils.hasText(targetTopic)) {
            validateResendTargetTopic(instanceId, targetTopic);
        }

        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;

        List<MessageExt> deadLetters = runtimeAdminClientResolver.execute(instanceId, admin -> {
            List<MessageExt> resolved = new ArrayList<>(selected.size());
            for (String msgId : selected) {
                try {
                    if (!BrokerTopologyGuards.isWithinKnownBrokerTopology(admin, msgId)) {
                        continue;
                    }
                    MessageExt deadLetter = admin.viewMessage(dlqTopic, msgId);
                    if (deadLetter != null) {
                        resolved.add(deadLetter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to resolve selected dead letter {} from {}: {}",
                            msgId, dlqTopic, e.getMessage());
                }
            }
            return resolved;
        });
        int[] counts = {0, 0};
        if (!deadLetters.isEmpty()) {
            try {
                runtimeAdminClientResolver.executeProducer(instanceId, producer -> {
                    for (MessageExt deadLetter : deadLetters) {
                        if (resendOne(producer, deadLetter, targetTopic)) {
                            counts[0]++;
                        } else {
                            counts[1]++;
                        }
                    }
                    return null;
                });
            } catch (Exception e) {
                log.warn("Failed to resend selected dead letters for group {}: {}", groupName, e.getMessage());
                counts[1] += deadLetters.size() - counts[0];
            }
        }
        int resent = counts[0];
        int failed = counts[1];
        boolean foundAll = deadLetters.size() == selected.size();

        String outcome = classifyOutcome(deadLetters.size(), resent, failed, !foundAll);
        String detail = String.format("instanceId=%s, group=%s, selected=%d, matched=%d, resent=%d, failed=%d",
                instanceId, groupName, selected.size(), deadLetters.size(), resent, failed);
        recordAudit(groupName, detail, outcome);
        log.info("Selected DLQ resend completed: {}", detail);
        return DLQResendResultVO.builder()
                .matched(deadLetters.size())
                .resent(resent)
                .failed(failed)
                .outcome(outcome)
                .scanIncomplete(!foundAll)
                .build();
    }

    @Override
    public PageResult<DLQMessageVO> listMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                                 int page, int pageSize) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required for DLQ message details");
        }
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "Invalid page or pageSize");
        }
        groupName = groupName.trim();
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;
        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        if (begin >= end) {
            throw new BusinessException(400, "DLQ detail start time must be before end time");
        }
        List<DLQMessageVO> all = collectDeadLetters(instanceId, dlqTopic, begin, end, RESEND_HARD_CAP)
                .messages().stream()
                .map(this::toExportVO)
                .toList();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, all.size());
        int to = (int) Math.min(offset + pageSize, all.size());
        return PageResult.of(all.subList(from, to), all.size(), page, pageSize);
    }

    @Override
    public DLQExcelExportResultVO exportExcel(String instanceId, String groupName, Long startTime, Long endTime,
                                              List<String> msgIds) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "groupName is required for DLQ export");
        }
        groupName = groupName.trim();
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;
        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        DeadLetterScanResult scanResult = collectDeadLetters(instanceId, dlqTopic, begin, end, RESEND_HARD_CAP);
        Set<String> selected = msgIds == null ? Collections.emptySet()
                : new java.util.HashSet<>(msgIds);
        List<DLQMessageVO> messages = scanResult.messages().stream()
                .filter(message -> selected.isEmpty() || selected.contains(message.getMsgId()))
                .map(this::toExportVO)
                .toList();
        byte[] data;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            com.alibaba.excel.EasyExcel.write(output, DLQMessageExcelRow.class)
                    .sheet("DLQ")
                    .doWrite(messages.stream().map(DLQMessageExcelRow::from).toList());
            data = output.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to build Excel export for group {}: {}", groupName, e.getMessage());
            throw new BusinessException(502, "Failed to export DLQ messages as Excel: " + e.getMessage());
        }
        return DLQExcelExportResultVO.builder()
                .data(data)
                .truncated(scanResult.truncated())
                .failedQueueCount(scanResult.failedQueueCount())
                .limit(RESEND_HARD_CAP)
                .build();
    }

    @Override
    public DLQExportResultVO exportMessages(String instanceId, String groupName, Long startTime, Long endTime,
                                            Integer maxCount) {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + groupName;
        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        int cap = maxCount == null || maxCount <= 0 ? RESEND_HARD_CAP : Math.min(maxCount, RESEND_HARD_CAP);
        DeadLetterScanResult scanResult = collectDeadLetters(instanceId, dlqTopic, begin, end, cap);
        return DLQExportResultVO.builder()
                .messages(scanResult.messages().stream().map(this::toExportVO).toList())
                .truncated(scanResult.truncated())
                .failedQueueCount(scanResult.failedQueueCount())
                .limit(cap)
                .build();
    }

    private DLQMessageVO toExportVO(MessageExt message) {
        return DLQMessageVO.builder()
                .msgId(message.getMsgId())
                .topic(message.getTopic())
                .queueId(message.getQueueId())
                .offset(message.getQueueOffset())
                .storeTime(message.getStoreTimestamp())
                .keys(message.getKeys())
                .body(toUtf8Text(message.getBody()))
                .bodyBase64(message.getBody() == null ? null
                        : Base64.getEncoder().encodeToString(message.getBody()))
                .build();
    }

    private String toUtf8Text(byte[] body) {
        if (body == null) {
            return null;
        }
        try {
            return new String(body, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DeadLetterScanResult collectDeadLetters(String instanceId, String dlqTopic,
                                                     long begin, long end, int cap) {
        return runtimeAdminClientResolver.executePullConsumer(instanceId,
                consumer -> scanDeadLetters(consumer, dlqTopic, begin, end, cap));
    }

    private DeadLetterScanResult scanDeadLetters(DefaultMQPullConsumer consumer, String dlqTopic,
                                                  long begin, long end, int cap) {
        List<MessageExt> result = new ArrayList<>();
        int failedQueueCount = 0;
        boolean truncated = false;
        try {
            Set<MessageQueue> queues = consumer.fetchSubscribeMessageQueues(dlqTopic);
            if (queues == null || queues.isEmpty()) {
                return new DeadLetterScanResult(result, 0, false);
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
                        if (result.size() >= cap) {
                            truncated = true;
                            break outer;
                        }
                        PullResult pullResult = consumer.pull(queue, "*", offset, 32);
                        if (pullResult == null) {
                            log.warn("Stop DLQ scan for {} because queue {} returned no pull result", dlqTopic, queue);
                            failedQueueCount++;
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
                                if (result.size() >= cap) {
                                    truncated = true;
                                    break outer;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    failedQueueCount++;
                    log.warn("Failed to scan DLQ queue {} in {}: {}", queue, dlqTopic, e.getMessage());
                }
            }
            if (failedQueueCount == queues.size()) {
                throw new BusinessException(502, "Failed to scan DLQ topic " + dlqTopic);
            }
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            log.warn("Failed to collect dead letters from {}: {}", dlqTopic, e.getMessage());
            throw new BusinessException(502, "Failed to scan DLQ topic " + dlqTopic + ": " + e.getMessage());
        }
        return new DeadLetterScanResult(result, failedQueueCount, truncated);
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
                    if (!MessageConst.STRING_HASH_SET.contains(key)
                            && !MessageConst.PROPERTY_TAGS.equals(key)
                            && !MessageConst.PROPERTY_KEYS.equals(key)
                            && !MessageConst.PROPERTY_WAIT_STORE_MSG_OK.equals(key)) {
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

    /**
     * An explicit resend target is a powerful override: without validation it can feed dead
     * letters back into their own DLQ or retry topic, poison broker system topics, or silently
     * create new topics on clusters with autoCreateTopicEnable. Restrict it to valid, existing,
     * non-system topics on the selected instance.
     */
    private void validateResendTargetTopic(String instanceId, String targetTopic) {
        TopicValidator.ValidateResult validity = TopicValidator.validateTopic(targetTopic);
        if (!validity.isValid()) {
            throw new BusinessException(400, "targetTopic is not a valid RocketMQ topic name: "
                    + targetTopic);
        }
        if (SystemTopicFilter.isSystem(targetTopic)) {
            throw new BusinessException(400,
                    "targetTopic must not be a RocketMQ system, retry or DLQ topic: " + targetTopic);
        }
        boolean exists;
        try {
            exists = Boolean.TRUE.equals(runtimeAdminClientResolver.execute(instanceId, admin -> {
                TopicList topics = admin.fetchAllTopicList();
                return topics != null && topics.getTopicList() != null
                        && topics.getTopicList().contains(targetTopic);
            }));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(502, "Failed to verify targetTopic on the selected instance: "
                    + e.getMessage());
        }
        if (!exists) {
            throw new BusinessException(400,
                    "targetTopic does not exist on the selected instance; create the topic before resending: "
                            + targetTopic);
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

    private String classifyOutcome(int matched, int resent, int failed, boolean scanIncomplete) {
        if (scanIncomplete) {
            return "PARTIAL";
        }
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
            auditService.record("RESEND_DLQ", "DLQ", groupName, null, detail, result);
        } catch (Exception e) {
            log.warn("Failed to record DLQ resend audit: {}", e.getMessage());
        }
    }

    private record DeadLetterScanResult(List<MessageExt> messages, int failedQueueCount, boolean truncated) {
        boolean scanIncomplete() {
            return failedQueueCount > 0 || truncated;
        }
    }
}
