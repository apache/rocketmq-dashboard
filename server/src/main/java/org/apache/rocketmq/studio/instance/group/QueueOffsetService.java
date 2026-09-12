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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueOffsetService {
    private static final long READ_TIMEOUT_MS = 5000;
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public QueueOffsetPreview preview(QueueOffsetCommand request) {
        long offset = parseOffset(request.offset());
        return resolver.execute(request.instanceId(), admin -> inspect(admin, request, offset));
    }

    public QueueOffsetPreview apply(QueueOffsetCommand request) {
        long offset = parseOffset(request.offset());
        long expected = parseOffset(request.expectedCurrentOffset());
        return resolver.execute(request.instanceId(), admin -> {
            var preview = inspect(admin, request, offset);
            if (Long.parseLong(preview.currentOffset()) != expected) {
                throw new BusinessException(409, "Consumer offset changed; inspect the queue again");
            }
            var queue = queue(request);
            try {
                // Do not use resetOffsetByQueueId: its second RPC can reset every topic queue
                // when the broker uses the legacy client-side timestamp reset protocol.
                admin.updateConsumeOffset(preview.brokerAddress(), request.group().trim(), queue, offset);
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                record(request, preview, "FAILED", failure.getMessage());
                throw failure;
            }
            record(request, preview, "SUCCESS", null);
            return preview;
        });
    }

    private QueueOffsetPreview inspect(MQAdminExt admin, QueueOffsetCommand request, long target) throws Exception {
        var queue = queue(request);
        var route = admin.examineTopicRouteInfo(queue.getTopic());
        String address = route.getBrokerDatas().stream()
                .filter(broker -> queue.getBrokerName().equals(broker.getBrokerName()))
                .map(broker -> broker.getBrokerAddrs().get(0L)).filter(value -> value != null && !value.isBlank())
                .findFirst().orElseThrow(() -> new BusinessException(409, "Queue has no registered master"));
        requireOffline(admin, request.group().trim(), address);
        var stats = admin.examineTopicStats(queue.getTopic());
        var range = stats.getOffsetTable().get(queue);
        if (range == null) {
            throw new BusinessException(404, "Queue is not present in the topic");
        }
        long min = range.getMinOffset();
        long max = range.getMaxOffset();
        if (min < 0 || max < min || target < min || target > max) {
            throw new BusinessException(409, "Target offset is outside the readable queue range");
        }
        var consumption = admin.examineConsumeStats(address, request.group().trim(), queue.getTopic(), READ_TIMEOUT_MS);
        var current = consumption.getOffsetTable().get(queue);
        if (current == null || current.getConsumerOffset() < 0) {
            throw new BusinessException(409, "Queue has no committed consumer offset");
        }
        long currentOffset = current.getConsumerOffset();
        return new QueueOffsetPreview(address, Long.toString(currentOffset), Long.toString(target),
                Long.toString(min), Long.toString(max), Long.toString(target - currentOffset), Long.toString(max - target));
    }

    private void requireOffline(MQAdminExt admin, String group, String address) throws Exception {
        try {
            var connections = admin.examineConsumerConnectionInfo(group, address);
            if (connections == null || !connections.getConnectionSet().isEmpty()) {
                throw new BusinessException(409, "Stop all consumers before changing a queue offset");
            }
        } catch (MQClientException failure) {
            if (failure.getResponseCode() != ResponseCode.CONSUMER_NOT_ONLINE) {
                throw failure;
            }
        } catch (MQBrokerException failure) {
            if (failure.getResponseCode() != ResponseCode.CONSUMER_NOT_ONLINE) {
                throw failure;
            }
        }
    }

    private long parseOffset(String value) {
        try {
            long offset = Long.parseLong(value);
            if (offset < 0) {
                throw new NumberFormatException();
            }
            return offset;
        } catch (NumberFormatException failure) {
            throw new BusinessException(400, "A non-negative 64-bit offset is required");
        }
    }

    private MessageQueue queue(QueueOffsetCommand request) {
        return new MessageQueue(request.topic().trim(), request.brokerName().trim(), request.queueId());
    }

    private void record(QueueOffsetCommand request, QueueOffsetPreview preview, String result, String error) {
        audit.record("SET_QUEUE_CONSUMER_OFFSET", "CONSUMER_GROUP", request.group().trim(), request.instanceId(),
                "topic=" + request.topic().trim() + ", broker=" + request.brokerName().trim()
                        + ", queueId=" + request.queueId() + ", previous=" + preview.currentOffset()
                        + ", target=" + preview.targetOffset(), result, error);
    }
}
