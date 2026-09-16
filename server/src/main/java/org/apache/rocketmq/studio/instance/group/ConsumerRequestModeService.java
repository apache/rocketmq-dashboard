/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.group;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageRequestMode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Broker;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Outcome;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Preview;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Receipt;
import org.apache.rocketmq.studio.instance.group.ConsumerRequestMode.Request;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ConsumerRequestModeService {
    private final RuntimeAdminClientResolver resolver;
    private final MessageRequestModeReader reader;
    private final OperationAuditService audit;

    public Preview preview(String instanceId, String topic, String group) {
        validate(topic, group);
        return resolver.execute(instanceId, admin -> {
            try {
                return inspect(admin, topic, group);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Request mode inspection was interrupted");
            }
        });
    }

    public Receipt apply(Request request) {
        validate(request.topic(), request.group());
        requireMode(request.mode(), 400);
        if (request.popShareQueueNum() < -1 || "PULL".equals(request.mode()) && request.popShareQueueNum() != 0
                || request.expected() == null || request.expected().isEmpty()) {
            throw new BusinessException(400, "A reviewed preview and valid POP sharing value are required");
        }
        return resolver.execute(request.instanceId(), admin -> {
            Preview fresh;
            try {
                fresh = inspect(admin, request.topic(), request.group());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Request mode preflight was interrupted");
            }
            if (!fresh.brokers().equals(request.expected())) {
                throw new BusinessException(409, "Broker configuration or topology changed; preview again");
            }
            var outcomes = new ArrayList<Outcome>();
            boolean stopped = false;
            for (Broker before : fresh.brokers()) {
                if (stopped) {
                    outcomes.add(new Outcome(before, "NOT_ATTEMPTED", null));
                    continue;
                }
                if (matches(before, request)) {
                    outcomes.add(new Outcome(before, "UNCHANGED", before));
                    continue;
                }
                Broker observed = null;
                String status = "UNKNOWN";
                try {
                    admin.setMessageRequestMode(before.address(), request.topic(), request.group(),
                            MessageRequestMode.valueOf(request.mode()), request.popShareQueueNum(), 5000);
                    observed = read(admin, before.brokerName(), before.address(), request.topic(), request.group());
                    if (matches(observed, request)) {
                        status = "CONFIRMED";
                    }
                } catch (Exception exception) {
                    // A failed response does not prove that the broker rejected the update.
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                stopped = "UNKNOWN".equals(status);
                outcomes.add(new Outcome(before, status, observed));
                audit.record("SET_MESSAGE_REQUEST_MODE", "GROUP", request.group(), request.instanceId(),
                        "topic=" + request.topic() + ", broker=" + before.brokerName() + ", before="
                                + before.mode() + "/" + before.popShareQueueNum() + ", explicit=" + before.explicit()
                                + ", requested=" + request.mode() + "/" + request.popShareQueueNum(),
                        status, stopped ? "Read current request modes before recovery" : null);
            }
            return new Receipt(List.copyOf(outcomes));
        });
    }

    private boolean matches(Broker broker, Request request) {
        return broker.explicit() && broker.mode().equals(request.mode())
                && broker.popShareQueueNum() == request.popShareQueueNum();
    }

    private Preview inspect(MQAdminExt admin, String topic, String group) throws Exception {
        var route = admin.examineTopicRouteInfo(topic);
        var cluster = admin.examineBrokerClusterInfo();
        if (route == null || route.getQueueDatas() == null || cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Topic route or broker registry is unavailable");
        }
        var names = new TreeSet<String>();
        for (var queue : route.getQueueDatas()) {
            if (queue == null || !StringUtils.hasText(queue.getBrokerName())) {
                throw new BusinessException(502, "Topic route contains an invalid broker");
            }
            names.add(queue.getBrokerName());
        }
        if (names.isEmpty()) {
            throw new BusinessException(404, "Topic has no registered brokers");
        }
        var result = new ArrayList<Broker>();
        for (String name : names) {
            var broker = cluster.getBrokerAddrTable().get(name);
            String address = broker == null || broker.getBrokerAddrs() == null ? null : broker.getBrokerAddrs().get(0L);
            if (!StringUtils.hasText(address)) {
                throw new BusinessException(409, "Every topic broker must have a registered master");
            }
            requireOffline(admin, address, group);
            result.add(read(admin, name, address, topic, group));
        }
        result.sort(Comparator.comparing(Broker::brokerName));
        return new Preview(topic, group, List.copyOf(result));
    }

    private Broker read(MQAdminExt admin, String name, String address, String topic, String group) throws Exception {
        var config = admin.getBrokerConfig(address);
        if (config == null) {
            throw new BusinessException(502, "Broker defaults are unavailable");
        }
        JsonNode override = reader.read((DefaultMQAdminExt) admin, address, topic, group);
        String mode;
        int sharing;
        if (override == null) {
            mode = config.getProperty("defaultMessageRequestMode");
            requireMode(mode, 502);
            sharing = "POP".equals(mode) ? Integer.parseInt(config.getProperty("defaultPopShareQueueNum")) : 0;
        } else {
            if (!override.isObject() || !topic.equals(override.path("topic").asText())
                    || !group.equals(override.path("consumerGroup").asText())
                    || !override.path("popShareQueueNum").isIntegralNumber()
                    || !override.path("popShareQueueNum").canConvertToInt()) {
                throw new BusinessException(502, "Stored request mode is invalid");
            }
            mode = override.path("mode").asText();
            requireMode(mode, 502);
            sharing = override.get("popShareQueueNum").intValue();
        }
        return new Broker(name, address, mode, sharing, override != null,
                config.getProperty("serverLoadBalancerEnable", "UNKNOWN"));
    }

    private void requireOffline(MQAdminExt admin, String address, String group) throws Exception {
        if (admin.examineSubscriptionGroupConfig(address, group) == null) {
            throw new BusinessException(409, "Consumer group must exist on every topic master");
        }
        try {
            var connection = admin.examineConsumerConnectionInfo(group, address);
            if (connection == null || connection.getConnectionSet() == null || !connection.getConnectionSet().isEmpty()) {
                throw new BusinessException(409, "Stop the consumer group and verify its connection state");
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

    private void validate(String topic, String group) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(group)
                || topic.startsWith("%RETRY%") || topic.startsWith("%DLQ%")) {
            throw new BusinessException(400, "A normal topic and consumer group are required");
        }
    }

    private void requireMode(String mode, int code) {
        if (!"POP".equals(mode) && !"PULL".equals(mode)) {
            throw new BusinessException(code, "Message request mode must be POP or PULL");
        }
    }
}
