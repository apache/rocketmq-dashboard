/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Expected;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Outcome;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Preview;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Queue;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Receipt;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Request;
import org.apache.rocketmq.studio.instance.group.ConsumerOffsetCopy.Status;

@Service
@RequiredArgsConstructor
public class ConsumerOffsetCopyService {
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public Preview preview(String instanceId, String topic, String sourceGroup, String targetGroup) {
        validate(topic, sourceGroup, targetGroup);
        return resolver.execute(instanceId, admin -> {
            try {
                return inspect(admin, topic, sourceGroup, targetGroup);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Offset copy inspection was interrupted");
            }
        });
    }

    public Receipt apply(Request request) {
        validate(request.topic(), request.sourceGroup(), request.targetGroup());
        if (request.expected() == null || request.expected().isEmpty()) {
            throw new BusinessException(400, "A reviewed queue preview is required");
        }
        return resolver.execute(request.instanceId(), admin -> {
            Preview fresh;
            try {
                fresh = inspect(admin, request.topic(), request.sourceGroup(), request.targetGroup());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Offset copy preflight was interrupted");
            }
            List<Expected> expected = fresh.queues().stream().map(Queue::expected).toList();
            if (!expected.equals(request.expected())) {
                throw new BusinessException(409, "Queue topology or offsets changed; obtain a new preview");
            }
            var outcomes = new ArrayList<Outcome>();
            boolean stopped = false;
            for (Expected queue : expected) {
                if (stopped) {
                    outcomes.add(new Outcome(queue, Status.NOT_ATTEMPTED, null));
                    continue;
                }
                if (queue.sourceOffset().equals(queue.targetOffset())) {
                    outcomes.add(new Outcome(queue, Status.UNCHANGED, queue.targetOffset()));
                    continue;
                }
                String observed = null;
                Status status = Status.UNKNOWN;
                try {
                    admin.updateConsumeOffset(queue.brokerAddr(), request.targetGroup(),
                            new MessageQueue(request.topic(), queue.brokerName(), queue.queueId()),
                            Long.parseLong(queue.sourceOffset()));
                    observed = offset(admin.examineConsumeStats(queue.brokerAddr(), request.targetGroup(),
                            request.topic(), 5000), new MessageQueue(request.topic(),
                            queue.brokerName(), queue.queueId()));
                    if (queue.sourceOffset().equals(observed)) {
                        status = Status.CONFIRMED;
                    }
                } catch (Exception exception) {
                    // A failed RPC may already have changed the broker. Never retry automatically.
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                stopped = status == Status.UNKNOWN;
                outcomes.add(new Outcome(queue, status, observed));
                audit.record("COPY_CONSUMER_OFFSET", "GROUP", request.targetGroup(), request.instanceId(),
                        "topic=" + request.topic() + ", source=" + request.sourceGroup() + ", broker="
                                + queue.brokerName() + ", queue=" + queue.queueId() + ", before="
                                + queue.targetOffset() + ", requested=" + queue.sourceOffset(),
                        status.name(), stopped ? "Read back offsets before any further operation" : null);
            }
            return new Receipt(List.copyOf(outcomes));
        });
    }

    private Preview inspect(MQAdminExt admin, String topic, String source, String target) throws Exception {
        var route = admin.examineTopicRouteInfo(topic);
        var cluster = admin.examineBrokerClusterInfo();
        if (route == null || route.getQueueDatas() == null || route.getQueueDatas().isEmpty()
                || cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Topic route or registered brokers are unavailable");
        }
        var queues = new ArrayList<Queue>();
        var brokers = new HashSet<String>();
        for (var data : route.getQueueDatas()) {
            if (data == null || !StringUtils.hasText(data.getBrokerName()) || data.getReadQueueNums() < 0
                    || !brokers.add(data.getBrokerName())) {
                throw new BusinessException(502, "Invalid or duplicate topic route");
            }
            var broker = cluster.getBrokerAddrTable().get(data.getBrokerName());
            String address = broker == null || broker.getBrokerAddrs() == null
                    ? null : broker.getBrokerAddrs().get(0L);
            if (!StringUtils.hasText(address)) {
                throw new BusinessException(409, "Every topic broker must have a registered master");
            }
            requireOffline(admin, address, source);
            requireOffline(admin, address, target);
            var ranges = admin.examineTopicStats(address, topic);
            var sourceStats = admin.examineConsumeStats(address, source, topic, 5000);
            var targetStats = admin.examineConsumeStats(address, target, topic, 5000);
            if (ranges == null || ranges.getOffsetTable() == null) {
                throw new BusinessException(502, "Queue retention metadata is unavailable");
            }
            for (int id = 0; id < data.getReadQueueNums(); id++) {
                var identity = new MessageQueue(topic, data.getBrokerName(), id);
                var range = ranges.getOffsetTable().get(identity);
                String sourceOffset = offset(sourceStats, identity);
                String targetOffset = offset(targetStats, identity);
                if (range == null || range.getMinOffset() < 0 || range.getMaxOffset() < range.getMinOffset()) {
                    throw new BusinessException(502, "Queue retention range is invalid or missing");
                }
                if (sourceOffset == null || Long.parseLong(sourceOffset) < range.getMinOffset()
                        || Long.parseLong(sourceOffset) > range.getMaxOffset()) {
                    throw new BusinessException(409, "Source offset is missing or outside the retained range");
                }
                queues.add(new Queue(new Expected(data.getBrokerName(), address, id, sourceOffset, targetOffset),
                        Long.toString(range.getMinOffset()), Long.toString(range.getMaxOffset())));
            }
        }
        if (queues.isEmpty()) {
            throw new BusinessException(409, "Topic has no readable queues");
        }
        queues.sort(Comparator.comparing((Queue row) -> row.expected().brokerName())
                .thenComparingInt(row -> row.expected().queueId()));
        return new Preview(topic, source, target, List.copyOf(queues));
    }

    private void requireOffline(MQAdminExt admin, String address, String group) throws Exception {
        if (admin.examineSubscriptionGroupConfig(address, group) == null) {
            throw new BusinessException(409, "Both groups must already exist on every topic master");
        }
        try {
            var connections = admin.examineConsumerConnectionInfo(group, address);
            if (connections == null || connections.getConnectionSet() == null) {
                throw new BusinessException(502, "Consumer connection state is unavailable");
            }
            if (!connections.getConnectionSet().isEmpty()) {
                throw new BusinessException(409, "Stop both consumer groups before copying offsets");
            }
        } catch (MQClientException exception) {
            if (exception.getResponseCode() != ResponseCode.CONSUMER_NOT_ONLINE) {
                throw exception;
            }
        } catch (MQBrokerException exception) {
            if (exception.getResponseCode() != ResponseCode.CONSUMER_NOT_ONLINE) {
                throw exception;
            }
        }
    }

    private String offset(ConsumeStats stats, MessageQueue queue) {
        if (stats == null || stats.getOffsetTable() == null) {
            throw new BusinessException(502, "Consumer offset metadata is unavailable");
        }
        var value = stats.getOffsetTable().get(queue);
        return value == null || value.getConsumerOffset() < 0 ? null : Long.toString(value.getConsumerOffset());
    }

    private void validate(String topic, String source, String target) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(source) || !StringUtils.hasText(target)
                || source.equals(target) || topic.startsWith("%RETRY%") || topic.startsWith("%DLQ%")) {
            throw new BusinessException(400, "A normal topic and two different consumer groups are required");
        }
    }
}
