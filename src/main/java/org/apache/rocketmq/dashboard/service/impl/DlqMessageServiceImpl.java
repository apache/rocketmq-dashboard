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

package org.apache.rocketmq.dashboard.service.impl;

import com.google.common.base.Throwables;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.DlqMessageRequest;
import org.apache.rocketmq.dashboard.model.DlqMessageResendResult;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessagePageTask;
import org.apache.rocketmq.dashboard.model.MessageQueryByPage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.QueueOffsetInfo;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.DlqMessageService;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.CMResult;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

@Service
@Slf4j
public class DlqMessageServiceImpl implements DlqMessageService {

    @Resource
    private MQAdminExt mqAdminExt;

    @Resource
    private MessageService messageService;

    @Resource
    private AutoCloseConsumerWrapper autoCloseConsumerWrapper;

    @Autowired
    private RMQConfigure configure;

    @Override
    public final MessagePage queryDlqMessageByPage(final MessageQuery query) {
        List<MessageView> messageViews = new ArrayList<>();
        PageRequest page = PageRequest.of(query.getPageNum(),
                query.getPageSize());
        String topic = query.getTopic();

        // For DLQ topics, use DLQ-specific querying logic
        if (topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)) {
            try {
                mqAdminExt.examineTopicRouteInfo(topic);
            } catch (MQClientException e) {
                // If the %DLQ%Group does not exist, return empty result
                if (e.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
                    return new MessagePage(new PageImpl<>(messageViews, page, 0),
                            query.getTaskId());
                } else {
                    log.error("Error examining DLQ topic route info "
                            + "for topic: {}", topic, e);
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                log.error("Exception examining DLQ topic route info "
                        + "for topic: {}", topic, e);
                Throwables.throwIfUnchecked(e);
                throw new RuntimeException(e);
            }

            // Use DLQ-specific querying instead of regular message service
            return queryDlqMessageByPageInternal(query, page);
        } else {
            // For non-DLQ topics, fall back to regular message service
            return messageService.queryMessageByPage(query);
        }
    }

    /**
     * DLQ-specific message querying logic that properly handles DLQ topics.
     *
     * @param query the message query parameters
     * @param page the page request
     * @return message page with DLQ messages
     */
    private MessagePage queryDlqMessageByPageInternal(final MessageQuery query,
            final PageRequest page) {
        MessageQueryByPage queryByPage = new MessageQueryByPage(
                query.getPageNum(),
                query.getPageSize(),
                query.getTopic(),
                query.getBegin(),
                query.getEnd());

        // Create a unique task ID for this query
        String taskId = MessageClientIDSetter.createUniqID();

        try {
            MessagePageTask task = this.queryFirstDlqMessagePage(queryByPage);
            return new MessagePage(task.getPage(), taskId);
        } catch (Exception e) {
            log.error("Failed to query DLQ messages for topic: {}",
                    query.getTopic(), e);
            // Return empty result on error
            return new MessagePage(new PageImpl<>(new ArrayList<>(), page, 0),
                    taskId);
        }
    }

    /**
     * Query the first page of DLQ messages using pull consumer.
     *
     * @param query the query parameters for DLQ messages
     * @return message page task containing DLQ messages
     */
    private MessagePageTask queryFirstDlqMessagePage(final MessageQueryByPage query) {
        long beginTime = query.getBegin();
        long endTime = query.getEnd();
        boolean useTimestampFilter = true;

        log.info("Querying DLQ messages: topic={}, beginTime={} ({}), endTime={} ({}), pageSize={}",
                query.getTopic(), beginTime, new java.util.Date(beginTime),
                endTime, new java.util.Date(endTime), query.getPageSize());

        // Validate time range - if invalid, return empty result
        if (beginTime > endTime) {
            log.warn("Invalid time range detected (begin={}, end={}), "
                    + "begin is after end. Returning empty result.",
                    beginTime, endTime);
            Page<MessageView> emptyPage = new PageImpl<>(new ArrayList<>(),
                    PageRequest.of(0, query.getPageSize()), 0);
            return new MessagePageTask(emptyPage, new ArrayList<>());
        }

        // If time range is too small (< 1 second), still apply filter
        final long oneSecondInMillis = 1000L;
        if (beginTime == endTime || (endTime - beginTime) < oneSecondInMillis) {
            log.warn("Very small or zero time range detected (begin={}, end={}), "
                    + "range={}ms. Filtering will be very restrictive.",
                    beginTime, endTime, endTime - beginTime);
        }

        boolean isEnableAcl = !StringUtils.isEmpty(configure.getAccessKey())
                && !StringUtils.isEmpty(configure.getSecretKey());
        RPCHook rpcHook = null;
        if (isEnableAcl) {
            rpcHook = new AclClientRPCHook(
                    new SessionCredentials(configure.getAccessKey(),
                            configure.getSecretKey()));
        }

        DefaultMQPullConsumer consumer = autoCloseConsumerWrapper.getConsumer(
                rpcHook, configure.isUseTLS());

        List<QueueOffsetInfo> queueOffsetInfos = new ArrayList<>();
        List<MessageView> messageViews = new ArrayList<>();

        try {
            Collection<MessageQueue> messageQueues =
                    consumer.fetchSubscribeMessageQueues(query.getTopic());

            int idx = 0;
            for (MessageQueue messageQueue : messageQueues) {
                // Get actual min/max offsets for the queue (not timestamp-based)
                Long minOffset = consumer.minOffset(messageQueue);
                Long maxOffset = consumer.maxOffset(messageQueue);
                // QueueOffsetInfo(idx, start, end, startOffset, endOffset, messageQueue)
                queueOffsetInfos.add(new QueueOffsetInfo(idx++, minOffset, maxOffset,
                        minOffset, maxOffset, messageQueue));
            }

            // Collect messages from all queues, filtering by timestamp
            for (QueueOffsetInfo queueOffset : queueOffsetInfos) {
                Long start = queueOffset.getStartOffset();
                Long maxQueueOffset = queueOffset.getEndOffset();
                MessageQueue mq = queueOffset.getMessageQueues();

                if (start < maxQueueOffset) {
                    try {
                        // Pull messages in batches, filtering by timestamp
                        // Collect ALL matching messages first, then paginate
                        long currentOffset = start;
                        int batchSize = 32; // Pull 32 messages at a time

                        while (currentOffset < maxQueueOffset) {
                            int pullSize = (int) Math.min(batchSize, maxQueueOffset - currentOffset);

                            PullResult pullResult = consumer.pull(mq, "*", currentOffset, pullSize);

                            if (pullResult.getPullStatus() == PullStatus.FOUND) {
                                List<MessageExt> messages = pullResult.getMsgFoundList();
                                log.info("Pulled {} messages from queue {}, offset {}", 
                                        messages.size(), mq, currentOffset);
                                for (MessageExt message : messages) {
                                    boolean matches = true;
                                    if (useTimestampFilter) {
                                        long storeTimestamp = message.getStoreTimestamp();
                                        matches = storeTimestamp >= beginTime && storeTimestamp <= endTime;
                                        if (!matches) {
                                            log.debug("Message filtered out: msgId={}, storeTimestamp={} ({}), "
                                                    + "beginTime={} ({}), endTime={} ({})",
                                                    message.getMsgId(), storeTimestamp, 
                                                    new java.util.Date(storeTimestamp),
                                                    beginTime, new java.util.Date(beginTime),
                                                    endTime, new java.util.Date(endTime));
                                        } else {
                                            log.debug("Message matches filter: msgId={}, storeTimestamp={} ({})",
                                                    message.getMsgId(), storeTimestamp,
                                                    new java.util.Date(storeTimestamp));
                                        }
                                    }

                                    if (matches) {
                                        MessageView messageView = MessageView.fromMessageExt(message);
                                        String originalMsgId = message.getMsgId();
                                        
                                        // Try to get full message details, but don't fail if it doesn't work
                                        try {
                                            Pair<MessageView, List<MessageTrack>> pair =
                                                    messageService.viewMessage(query.getTopic(),
                                                            originalMsgId);
                                            if (pair != null && pair.getObject1() != null) {
                                                MessageView detailedView = pair.getObject1();
                                                // Ensure msgId is still set even if viewMessage didn't preserve it
                                                if (StringUtils.isBlank(detailedView.getMsgId())) {
                                                    detailedView.setMsgId(originalMsgId);
                                                }
                                                messageView = detailedView;
                                            }
                                        } catch (Exception e) {
                                            log.warn("Failed to get detailed message view for msgId: {}, using basic view", originalMsgId);
                                            // Ensure msgId is set even if viewMessage failed
                                            if (StringUtils.isBlank(messageView.getMsgId())) {
                                                messageView.setMsgId(originalMsgId);
                                            }
                                        }
                                        
                                        // Double-check msgId is set
                                        if (StringUtils.isBlank(messageView.getMsgId())) {
                                            messageView.setMsgId(originalMsgId);
                                        }
                                        
                                        messageViews.add(messageView);
                                    }
                                }
                                currentOffset = pullResult.getNextBeginOffset();
                            } else {
                                // No more messages
                                break;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to pull messages from queue: {}", mq, e);
                    }
                }
            }

            // Sort messages by store timestamp (newest first)
            messageViews.sort((a, b) -> Long.compare(b.getStoreTimestamp(),
                    a.getStoreTimestamp()));

            // Paginate from the sorted list
            int totalCount = messageViews.size();
            int pageNum = query.getPageNum();
            int pageSize = query.getPageSize();
            int startIndex = pageNum * pageSize;
            int endIndex = Math.min(startIndex + pageSize, totalCount);
            
            List<MessageView> pageContent;
            if (startIndex >= totalCount) {
                // Requested page is beyond available data
                pageContent = new ArrayList<>();
            } else {
                pageContent = new ArrayList<>(messageViews.subList(startIndex, endIndex));
            }

            // Create page with paginated content and total count
            Page<MessageView> page = new PageImpl<>(pageContent,
                    PageRequest.of(pageNum, pageSize), totalCount);

            return new MessagePageTask(page, queueOffsetInfos);

        } catch (Exception e) {
            log.error("Failed to query DLQ messages", e);
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public final List<DlqMessageResendResult> batchResendDlqMessage(final List<DlqMessageRequest> dlqMessages) {
        List<DlqMessageResendResult> batchResendResults = new LinkedList<>();
        
        // Follow TopicServiceImpl pattern for producer setup
        RPCHook rpcHook = null;
        if (configure.isACLEnabled()) {
            rpcHook = new AclClientRPCHook(
                    new SessionCredentials(configure.getAccessKey(), configure.getSecretKey()));
        }
        
        DefaultMQProducer producer = new DefaultMQProducer("DLQ_RESEND_PRODUCER_GROUP_" + System.currentTimeMillis(), 
                rpcHook, false, null);
        producer.setNamesrvAddr(configure.getNamesrvAddr());
        producer.setUseTLS(configure.isUseTLS());
        
        try {
            producer.start();
        } catch (Exception e) {
            log.error("Failed to start DLQ resend producer", e);
            // Return error results for all messages
            for (DlqMessageRequest dlqMessage : dlqMessages) {
                ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                failedResult.setRemark("Failed to start producer: " + e.getMessage());
                batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
            }
            return batchResendResults;
        }
        
        try {
            for (DlqMessageRequest dlqMessage : dlqMessages) {
                try {
                    log.info("Resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(), dlqMessage.getConsumerGroup());
                    
                    // Get original message from DLQ
                    String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + dlqMessage.getConsumerGroup();
                    MessageExt originalMessage = null;
                    try {
                        originalMessage = mqAdminExt.viewMessage(dlqTopic, dlqMessage.getMsgId());
                    } catch (Exception e) {
                        log.error("Failed to retrieve message from DLQ: msgId={}, topic={}, error={}", 
                                dlqMessage.getMsgId(), dlqTopic, e.getMessage());
                        // Try using UNIQ_KEY if available
                        if (e.getMessage() != null && e.getMessage().contains("no message")) {
                            throw new RuntimeException("Message not found in DLQ topic. The message may have been deleted or the message ID is incorrect.", e);
                        }
                        throw e;
                    }
                    
                    if (originalMessage == null) {
                        log.error("Original message not found in DLQ: msgId={}", dlqMessage.getMsgId());
                        ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                        failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                        failedResult.setRemark("Original message not found in DLQ");
                        batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                        continue;
                    }
                    
                    // Create new message to send (following TopicServiceImpl pattern)
                    org.apache.rocketmq.common.message.Message messageToResend = 
                        new org.apache.rocketmq.common.message.Message(
                            dlqMessage.getTopicName(), // Original topic (RETRY_TOPIC)
                            originalMessage.getBody()
                        );
                    
                    // Preserve original properties (skip system-reserved properties)
                    if (originalMessage.getProperties() != null) {
                        for (String key : originalMessage.getProperties().keySet()) {
                            // Skip system-reserved properties (check against MessageConst.STRING_HASH_SET)
                            if (!MessageConst.STRING_HASH_SET.contains(key)) {
                                try {
                                    messageToResend.putUserProperty(key, originalMessage.getProperty(key));
                                } catch (Exception e) {
                                    // Skip properties that cannot be set
                                    log.warn("Skipping property {} as it cannot be set: {}", key, e.getMessage());
                                }
                            } else {
                                log.debug("Skipping system-reserved property: {}", key);
                            }
                        }
                    }
                    
                    // Send using producer (standard pattern from TopicServiceImpl)
                    SendResult sendResult = producer.send(messageToResend);
                    
                    // Create success result
                    ConsumeMessageDirectlyResult result = new ConsumeMessageDirectlyResult();
                    result.setConsumeResult(CMResult.CR_SUCCESS);
                    result.setRemark("Message resent successfully");
                    batchResendResults.add(new DlqMessageResendResult(result, dlqMessage.getMsgId()));
                            
                } catch (MQClientException e) {
                    log.error("MQClientException while resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(),
                            dlqMessage.getConsumerGroup(), e);
                    ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                    failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                    failedResult.setRemark("Failed to resend: " + e.getMessage());
                    batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                } catch (org.apache.rocketmq.remoting.exception.RemotingException e) {
                    log.error("RemotingException while resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(),
                            dlqMessage.getConsumerGroup(), e);
                    ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                    failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                    failedResult.setRemark("Network error while resending: " + e.getMessage());
                    batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                } catch (org.apache.rocketmq.client.exception.MQBrokerException e) {
                    log.error("MQBrokerException while resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(),
                            dlqMessage.getConsumerGroup(), e);
                    ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                    failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                    failedResult.setRemark("Broker error while resending: " + e.getMessage());
                    batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                } catch (InterruptedException e) {
                    log.error("InterruptedException while resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(),
                            dlqMessage.getConsumerGroup(), e);
                    Thread.currentThread().interrupt();
                    ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                    failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                    failedResult.setRemark("Operation interrupted: " + e.getMessage());
                    batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                } catch (Exception e) {
                    log.error("Unexpected exception while resending DLQ message: msgId={}, topic={}, consumerGroup={}",
                            dlqMessage.getMsgId(), dlqMessage.getTopicName(),
                            dlqMessage.getConsumerGroup(), e);
                    ConsumeMessageDirectlyResult failedResult = new ConsumeMessageDirectlyResult();
                    failedResult.setConsumeResult(CMResult.CR_THROW_EXCEPTION);
                    failedResult.setRemark("Failed to resend: " + e.getMessage());
                    batchResendResults.add(new DlqMessageResendResult(failedResult, dlqMessage.getMsgId()));
                }
            }
        } finally {
            producer.shutdown();
        }
        
        return batchResendResults;
    }
}
