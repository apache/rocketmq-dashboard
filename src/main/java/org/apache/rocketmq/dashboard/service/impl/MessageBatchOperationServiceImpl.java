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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationRequest;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationResult;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationResult.ItemResult;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.service.MessageBatchOperationService;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class MessageBatchOperationServiceImpl implements MessageBatchOperationService {

    private static final Logger logger = LoggerFactory.getLogger(MessageBatchOperationServiceImpl.class);

    @Autowired
    private MessageService messageService;

    @Override
    public MessageBatchOperationResult batchResendMessages(MessageBatchOperationRequest request) {
        MessageBatchOperationResult result = new MessageBatchOperationResult();
        if (request == null || CollectionUtils.isEmpty(request.getMsgIds())) {
            return result;
        }

        result.setTotalProcessed(request.getMsgIds().size());
        Pattern tagPattern = StringUtils.isNotBlank(request.getTagFilterRegex()) ? Pattern.compile(request.getTagFilterRegex()) : null;

        int concurrency = Math.min(Math.max(1, request.getMaxConcurrency()), 20);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        for (String msgId : request.getMsgIds()) {
            executor.submit(() -> {
                try {
                    MessageView msg = messageService.viewMessage(request.getTopic(), msgId);
                    if (msg == null) {
                        synchronized (result) {
                            result.getItemResults().add(new ItemResult(msgId, false, "Message not found"));
                            result.setFailedCount(result.getFailedCount() + 1);
                        }
                        return;
                    }

                    // Apply Tag Filter
                    if (tagPattern != null && !tagPattern.matcher(msg.getTags() != null ? msg.getTags() : "").find()) {
                        synchronized (result) {
                            result.getItemResults().add(new ItemResult(msgId, false, "Filtered out by tag regex"));
                            result.setFilteredOutCount(result.getFilteredOutCount() + 1);
                        }
                        return;
                    }

                    // Apply Property Regex Filters
                    if (request.getPropertyRegexMap() != null && !request.getPropertyRegexMap().isEmpty()) {
                        Map<String, String> properties = msg.getProperties();
                        boolean matchesAll = true;
                        for (Map.Entry<String, String> entry : request.getPropertyRegexMap().entrySet()) {
                            String val = properties != null ? properties.get(entry.getKey()) : null;
                            if (val == null || !Pattern.compile(entry.getValue()).matcher(val).find()) {
                                matchesAll = false;
                                break;
                            }
                        }
                        if (!matchesAll) {
                            synchronized (result) {
                                result.getItemResults().add(new ItemResult(msgId, false, "Filtered out by property regex"));
                                result.setFilteredOutCount(result.getFilteredOutCount() + 1);
                            }
                            return;
                        }
                    }

                    // Rate limit delay
                    if (request.getRateLimitDelayMs() > 0) {
                        Thread.sleep(request.getRateLimitDelayMs());
                    }

                    String resendTopic = StringUtils.isNotBlank(request.getTargetTopic()) ? request.getTargetTopic() : request.getTopic();
                    boolean success = messageService.resendMessageById(request.getTopic(), msgId, resendTopic);
                    synchronized (result) {
                        if (success) {
                            result.getItemResults().add(new ItemResult(msgId, true, "Successfully resent to " + resendTopic));
                            result.setSuccessCount(result.getSuccessCount() + 1);
                        } else {
                            result.getItemResults().add(new ItemResult(msgId, false, "Resend call returned false"));
                            result.setFailedCount(result.getFailedCount() + 1);
                        }
                    }

                } catch (Exception e) {
                    logger.error("Error processing msgId {} in batch resend", msgId, e);
                    synchronized (result) {
                        result.getItemResults().add(new ItemResult(msgId, false, "Exception: " + e.getMessage()));
                        result.setFailedCount(result.getFailedCount() + 1);
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result;
    }

    @Override
    public String exportMessagesAsCsv(MessageBatchOperationRequest request) {
        StringBuilder csv = new StringBuilder();
        csv.append("MsgId,Topic,Tags,Keys,StoreTimestamp,ReconsumeTimes,BornHost\n");

        if (request != null && CollectionUtils.isNotEmpty(request.getMsgIds())) {
            for (String msgId : request.getMsgIds()) {
                try {
                    MessageView msg = messageService.viewMessage(request.getTopic(), msgId);
                    if (msg != null) {
                        csv.append(String.format("\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,\"%s\"\n",
                                msg.getMsgId(),
                                msg.getTopic(),
                                msg.getTags() != null ? msg.getTags() : "",
                                msg.getKeys() != null ? msg.getKeys() : "",
                                msg.getStoreTimestamp(),
                                msg.getReconsumeTimes(),
                                msg.getBornHostString() != null ? msg.getBornHostString() : ""
                        ));
                    }
                } catch (Exception e) {
                    logger.warn("Could not fetch msgId {} for CSV export", msgId, e);
                }
            }
        }

        return csv.toString();
    }
}
