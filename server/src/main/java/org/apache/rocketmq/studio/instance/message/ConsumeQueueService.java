/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.ConsumeQueueData;
import org.apache.rocketmq.remoting.protocol.body.QueryConsumeQueueResponseBody;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.admin.TopicOffset;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConsumeQueueService {
    private final RuntimeAdminClientResolver resolver;

    public ConsumeQueueSnapshot inspect(String instanceId, String topic, String brokerName,
            int queueId, String index, int count, String consumerGroup) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(brokerName) || queueId < 0
                || count < 1 || count > 32) {
            throw new BusinessException(400, "Topic, broker, nonnegative queue ID and count 1..32 are required");
        }
        long start = parseIndex(index);
        String group = StringUtils.hasText(consumerGroup) ? consumerGroup.trim() : null;
        return resolver.execute(instanceId, admin -> {
            try {
                var cluster = admin.examineBrokerClusterInfo();
                BrokerData broker = cluster == null || cluster.getBrokerAddrTable() == null
                        ? null : cluster.getBrokerAddrTable().get(brokerName);
                String address = broker == null || broker.getBrokerAddrs() == null
                        ? null : broker.getBrokerAddrs().get(0L);
                if (!StringUtils.hasText(address)) {
                    throw new BusinessException(404, "Registered master not found for broker: " + brokerName);
                }
                var stats = admin.examineTopicStats(topic);
                TopicOffset range = stats == null || stats.getOffsetTable() == null ? null
                        : stats.getOffsetTable().get(new MessageQueue(topic, brokerName, queueId));
                if (range == null) {
                    throw new BusinessException(404, "Queue not found in the selected topic");
                }
                long min = range.getMinOffset();
                long max = range.getMaxOffset();
                if (min < 0 || max < min) {
                    throw new BusinessException(502, "Broker returned an invalid queue range");
                }
                if (start < min || start > max) {
                    throw new BusinessException(416, "Index is outside [" + min + ", " + max + "]");
                }
                if (start == max) {
                    return new ConsumeQueueSnapshot(address, Long.toString(min), Long.toString(max),
                            Long.toString(start), count, true, null, null, null, List.of());
                }
                var body = admin.queryConsumeQueue(address, topic, queueId, start, count, group);
                return snapshot(address, start, count, group, body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "ConsumeQueue inspection was interrupted");
            }
        });
    }

    private long parseIndex(String index) {
        if (index == null || !index.matches("[0-9]{1,19}")) {
            throw new BusinessException(400, "Index must be a nonnegative 64-bit decimal string");
        }
        try {
            return Long.parseLong(index);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "Index exceeds the signed 64-bit range");
        }
    }

    private ConsumeQueueSnapshot snapshot(String address, long start, int count, String group,
            QueryConsumeQueueResponseBody body) {
        if (body == null || body.getQueueData() == null || body.getMinQueueIndex() < 0
                || body.getMaxQueueIndex() < body.getMinQueueIndex()) {
            throw new BusinessException(502, "Broker did not return a valid ConsumeQueue snapshot");
        }
        var subscription = body.getSubscriptionData();
        List<ConsumeQueueSnapshot.Entry> entries = new ArrayList<>();
        for (ConsumeQueueData data : body.getQueueData()) {
            if (data == null || entries.size() >= count) {
                throw new BusinessException(502, "Broker returned invalid or excessive index entries");
            }
            // RocketMQ leaves eval=false when it skips extension-based filtering.
            Boolean match = group != null && subscription != null && data.getExtendDataJson() != null
                    ? data.isEval() : null;
            entries.add(new ConsumeQueueSnapshot.Entry(entries.size() + 1,
                    Long.toString(data.getPhysicOffset()), data.getPhysicSize(),
                    Long.toString(data.getTagsCode()), data.getExtendDataJson(), data.getBitMap(),
                    match, data.getMsg()));
        }
        return new ConsumeQueueSnapshot(address, Long.toString(body.getMinQueueIndex()),
                Long.toString(body.getMaxQueueIndex()), Long.toString(start), count, false,
                subscription == null ? null : subscription.getExpressionType(),
                subscription == null ? null : subscription.getSubString(), body.getFilterData(),
                List.copyOf(entries));
    }
}
