/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BrokerQueueCleanupService {
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public enum Operation { UNUSED_TOPIC_QUEUES, EXPIRED_CONSUME_QUEUES }
    public record Preview(String brokerName, String address, Instant sampledAt, List<String> configuredTopics) { }
    public record Request(String instanceId, String brokerName, String address, Operation operation,
            List<String> expectedConfiguredTopics, String confirmation) { }
    public record Receipt(Operation operation, String brokerName, String address, String status, Instant finishedAt) { }

    public Preview preview(String instanceId, String brokerName, String address) {
        validate(brokerName, address);
        return resolver.execute(instanceId, admin -> {
            try {
                return inspect(admin, brokerName, address);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Queue cleanup preview was interrupted");
            }
        });
    }

    public Receipt apply(Request request) {
        validate(request.brokerName(), request.address());
        if (request.operation() == null || request.expectedConfiguredTopics() == null
                || !request.address().equals(request.confirmation())) {
            throw new BusinessException(400, "Review the node, select an operation and confirm its address");
        }
        return resolver.execute(request.instanceId(), admin -> {
            Preview current;
            try {
                current = inspect(admin, request.brokerName(), request.address());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Queue cleanup preflight was interrupted");
            }
            if (!current.configuredTopics().equals(request.expectedConfiguredTopics())) {
                throw new BusinessException(409, "Configured topics changed; review cleanup scope again");
            }
            boolean acknowledged = false;
            try {
                acknowledged = switch (request.operation()) {
                    case UNUSED_TOPIC_QUEUES -> admin.cleanUnusedTopicByAddr(request.address());
                    case EXPIRED_CONSUME_QUEUES -> admin.cleanExpiredConsumerQueueByAddr(request.address());
                };
            } catch (Exception exception) {
                // Cleanup may have removed some files before a response is lost. Never retry here.
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            String result = acknowledged ? "BROKER_REPORTED_COMPLETION" : "UNKNOWN";
            audit.record("CLEAN_BROKER_QUEUES", "BROKER", request.brokerName(), request.instanceId(),
                    "address=" + request.address() + ", operation=" + request.operation()
                            + ", configuredTopicCount=" + current.configuredTopics().size(),
                    result, acknowledged ? null : "Check broker logs and storage before another cleanup");
            return new Receipt(request.operation(), request.brokerName(), request.address(), result, Instant.now());
        });
    }

    private Preview inspect(MQAdminExt admin, String brokerName, String address) throws Exception {
        var cluster = admin.examineBrokerClusterInfo();
        if (cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Broker registry is unavailable");
        }
        var broker = cluster.getBrokerAddrTable().get(brokerName);
        if (broker == null || broker.getBrokerAddrs() == null || !broker.getBrokerAddrs().containsValue(address)) {
            throw new BusinessException(404, "Broker address is not registered in the selected instance");
        }
        var topics = admin.getAllTopicConfig(address, 5000);
        if (topics == null || topics.getTopicConfigTable() == null) {
            throw new BusinessException(502, "Configured topics are unavailable; cleanup cannot be reviewed");
        }
        List<String> names = topics.getTopicConfigTable().keySet().stream().sorted().toList();
        return new Preview(brokerName, address, Instant.now(), names);
    }

    private void validate(String brokerName, String address) {
        if (!StringUtils.hasText(brokerName) || !StringUtils.hasText(address)) {
            throw new BusinessException(400, "Broker name and registered address are required");
        }
    }
}
