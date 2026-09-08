/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import org.apache.commons.codec.DecoderException;
import org.apache.rocketmq.common.producer.RecallMessageHandle;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DelayMessageRecallService {
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService auditService;

    public RecallMessagePreview preview(RecallMessageDTO request) {
        resolver.resolveEndpoint(request.instanceId());
        return decode(request);
    }

    public RecallMessageReceipt recall(RecallMessageDTO request) {
        RecallMessagePreview target = decode(request);
        return resolver.executeProducer(request.instanceId(), producer -> {
            String messageId;
            try {
                messageId = producer.recallMessage(target.topic(), request.recallHandle().trim());
                if (messageId == null || messageId.isBlank()) {
                    throw new BusinessException(502, "Broker returned no recall receipt");
                }
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                record(request.instanceId(), target, "FAILED", failure.getMessage());
                throw failure;
            }
            // Keep the observational audit outside the broker operation's failure boundary.
            record(request.instanceId(), target, "SUCCESS", null);
            return new RecallMessageReceipt(messageId, System.currentTimeMillis());
        });
    }

    private RecallMessagePreview decode(RecallMessageDTO request) {
        try {
            var handle = (RecallMessageHandle.HandleV1) RecallMessageHandle.decodeHandle(request.recallHandle().trim());
            if (!request.topic().trim().equals(handle.getTopic())) {
                throw new BusinessException(400, "Topic does not match the recall handle");
            }
            long timestamp = Long.parseLong(handle.getTimestampStr());
            if (timestamp <= 0 || handle.getBrokerName().isBlank() || handle.getMessageId().isBlank()) {
                throw new BusinessException(400, "Recall handle has invalid target fields");
            }
            return new RecallMessagePreview(handle.getTopic(), handle.getBrokerName(), handle.getMessageId(), timestamp);
        } catch (DecoderException | NumberFormatException | IndexOutOfBoundsException failure) {
            throw new BusinessException(400, "Recall handle is invalid");
        }
    }

    private void record(String instanceId, RecallMessagePreview target, String result, String error) {
        auditService.record("RECALL_DELAY_MESSAGE", "MESSAGE", target.messageId(), instanceId,
                "topic=" + target.topic() + ", broker=" + target.brokerName()
                        + ", deliveryTimestamp=" + target.deliveryTimestamp(),
                result, error);
    }
}
