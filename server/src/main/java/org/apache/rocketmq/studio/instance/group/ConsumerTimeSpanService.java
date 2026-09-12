/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.remoting.protocol.body.QueueTimeSpan;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static org.apache.rocketmq.studio.instance.group.ConsumerTimeSpanSnapshot.CursorState;

@Service
@RequiredArgsConstructor
public class ConsumerTimeSpanService {
    private final RuntimeAdminClientResolver resolver;

    public ConsumerTimeSpanSnapshot inspect(String instanceId, String topic, String group) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(group)) {
            throw new BusinessException(400, "Topic and consumer group are required");
        }
        return resolver.execute(instanceId, admin -> {
            try {
                var stats = admin.examineTopicStats(topic);
                var offsets = admin.examineConsumeStats(group, topic);
                var spans = admin.queryConsumeTimeSpan(topic, group);
                if (stats == null || stats.getOffsetTable() == null
                        || offsets == null || offsets.getOffsetTable() == null || spans == null) {
                    throw new BusinessException(502, "Broker did not return time span and offset metadata");
                }
                Map<MessageQueue, QueueTimeSpan> byQueue = new HashMap<>();
                for (QueueTimeSpan span : spans) {
                    if (span == null || span.getMessageQueue() == null
                            || !stats.getOffsetTable().containsKey(span.getMessageQueue())
                            || byQueue.putIfAbsent(span.getMessageQueue(), span) != null) {
                        throw new BusinessException(502, "Queue topology changed or duplicate time span was returned");
                    }
                }
                var rows = new ArrayList<ConsumerTimeSpanSnapshot.Queue>();
                for (var entry : stats.getOffsetTable().entrySet()) {
                    MessageQueue queue = entry.getKey();
                    TopicOffset range = entry.getValue();
                    if (!topic.equals(queue.getTopic())
                            || !StringUtils.hasText(queue.getBrokerName()) || queue.getQueueId() < 0
                            || range.getMinOffset() < 0
                            || range.getMaxOffset() < range.getMinOffset()) {
                        throw new BusinessException(502, "Broker returned invalid queue metadata");
                    }
                    rows.add(row(queue, range, offsets.getOffsetTable().get(queue), byQueue.get(queue)));
                }
                rows.sort(Comparator.comparing(ConsumerTimeSpanSnapshot.Queue::brokerName)
                        .thenComparingInt(ConsumerTimeSpanSnapshot.Queue::queueId));
                return new ConsumerTimeSpanSnapshot(topic, group, Instant.now(), rows);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Consumer time span inspection was interrupted");
            }
        });
    }

    private ConsumerTimeSpanSnapshot.Queue row(MessageQueue queue, TopicOffset range,
            OffsetWrapper offset, QueueTimeSpan span) {
        Long consumer = offset == null || offset.getConsumerOffset() < 0 ? null : offset.getConsumerOffset();
        String earliest = span == null ? null : timestamp(span.getMinTimeStamp());
        String latest = span == null ? null : timestamp(span.getMaxTimeStamp());
        String reference = span == null ? null : timestamp(span.getConsumeTimeStamp());
        CursorState state;
        if (consumer == null) {
            state = CursorState.OFFSET_UNAVAILABLE;
        } else if (consumer == 0) {
            // Broker uses the earliest retained timestamp when no positive offset exists.
            state = CursorState.EARLIEST_MESSAGE_FALLBACK;
        } else if (consumer - 1 < range.getMinOffset() || consumer > range.getMaxOffset()) {
            state = CursorState.OUTSIDE_RETAINED_RANGE;
        } else if (reference == null) {
            state = CursorState.TIMESTAMP_UNAVAILABLE;
        } else {
            state = CursorState.RECORDED_OFFSET_REFERENCE;
        }
        return new ConsumerTimeSpanSnapshot.Queue(queue.getBrokerName(), queue.getQueueId(),
                Long.toString(range.getMinOffset()), Long.toString(range.getMaxOffset()),
                consumer == null ? null : consumer.toString(), earliest, latest, reference, state, span != null);
    }

    private String timestamp(long value) {
        return value > 0 ? Long.toString(value) : null;
    }
}
