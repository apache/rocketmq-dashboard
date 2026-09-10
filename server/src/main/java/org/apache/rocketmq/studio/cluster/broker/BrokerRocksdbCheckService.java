/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BrokerRocksdbCheckService {
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public record Settings(boolean doubleWriteEnabled, List<String> loadingStores) { }
    public record Preview(String brokerName, String address, Instant sampledAt, Settings settings,
            boolean eligible, List<String> topics) { }
    public record Request(String instanceId, String brokerName, String address, String topic,
            String checkFromMillis, Settings expectedSettings, boolean confirmed) { }
    public record Receipt(String brokerName, String address, String topic, String checkFromMillis,
            String status, Integer brokerStatus, String brokerRemark, Instant submittedAt, Instant receivedAt) { }

    public Preview preview(String instanceId, String brokerName, String address) {
        validate(brokerName, address);
        return resolver.execute(instanceId, admin -> {
            try {
                return inspect(admin, brokerName, address);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "RocksDB check preview was interrupted");
            }
        });
    }

    public Receipt submit(Request request) {
        validate(request.brokerName(), request.address());
        if (!StringUtils.hasText(request.topic()) || !request.confirmed() || request.expectedSettings() == null) {
            throw new BusinessException(400, "Select one topic, review storage settings and confirm the scan");
        }
        long checkpoint;
        try {
            if (request.checkFromMillis() == null || !request.checkFromMillis().matches("[0-9]+")) {
                throw new NumberFormatException();
            }
            checkpoint = Long.parseLong(request.checkFromMillis());
            if (checkpoint <= 0 || checkpoint > System.currentTimeMillis()) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "Check start must be positive epoch milliseconds in the past");
        }
        return resolver.execute(request.instanceId(), admin -> {
            Preview current;
            try {
                current = inspect(admin, request.brokerName(), request.address());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "RocksDB check preflight was interrupted");
            }
            if (!current.eligible() || !current.settings().equals(request.expectedSettings())) {
                throw new BusinessException(409, "Double-write storage settings are unsupported or changed; review again");
            }
            if (!current.topics().contains(request.topic())) {
                throw new BusinessException(409, "Selected topic is no longer configured on this broker");
            }
            Instant submittedAt = Instant.now();
            Integer brokerStatus = null;
            String remark = null;
            try {
                var response = admin.checkRocksdbCqWriteProgress(request.address(), request.topic(), checkpoint);
                if (response != null) {
                    brokerStatus = response.getCheckStatus();
                    remark = response.getCheckResult();
                }
            } catch (Exception exception) {
                // A lost response does not prove the broker's asynchronous worker was not started.
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            String status = brokerStatus == null ? "UNKNOWN" : brokerStatus == 2 ? "ACCEPTED" : "BROKER_RESPONSE";
            audit.record("CHECK_ROCKSDB_QUEUE_PROGRESS", "BROKER", request.brokerName(), request.instanceId(),
                    "address=" + request.address() + ", topic=" + request.topic() + ", checkFromMillis=" + checkpoint,
                    status, brokerStatus == null ? "Check broker logs before submitting another scan" : null);
            return new Receipt(request.brokerName(), request.address(), request.topic(), Long.toString(checkpoint),
                    status, brokerStatus, remark, submittedAt, Instant.now());
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
        var config = admin.getBrokerConfig(address);
        String enabled = config == null ? null : config.getProperty("rocksdbCQDoubleWriteEnable");
        String stores = config == null ? null : config.getProperty("combineCQLoadingCQTypes");
        if (!("true".equalsIgnoreCase(enabled) || "false".equalsIgnoreCase(enabled)) || stores == null) {
            throw new BusinessException(502, "RocksDB double-write configuration is unavailable");
        }
        var settings = new Settings(Boolean.parseBoolean(enabled), Arrays.stream(stores.split(";"))
                .map(String::trim).filter(StringUtils::hasText).distinct().sorted().toList());
        var topics = admin.getAllTopicConfig(address, 5000);
        if (topics == null || topics.getTopicConfigTable() == null) {
            throw new BusinessException(502, "Configured topics are unavailable");
        }
        boolean eligible = settings.doubleWriteEnabled()
                && settings.loadingStores().containsAll(List.of("default", "defaultRocksDB"));
        return new Preview(brokerName, address, Instant.now(), settings, eligible,
                topics.getTopicConfigTable().keySet().stream().sorted().toList());
    }

    private void validate(String brokerName, String address) {
        if (!StringUtils.hasText(brokerName) || !StringUtils.hasText(address)) {
            throw new BusinessException(400, "Broker name and registered address are required");
        }
    }
}
