/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BrokerReadAheadService {
    private final RuntimeAdminClientResolver resolver;
    private final OperationAuditService audit;

    public record Snapshot(String brokerName, String address, boolean enabled, Instant sampledAt) { }
    public record Request(String instanceId, String brokerName, String address, Boolean expectedEnabled,
            Boolean enabled) { }
    public record Receipt(String status, boolean acknowledged, Snapshot before, Snapshot observed) { }

    public Snapshot inspect(String instanceId, String brokerName, String address) {
        validate(brokerName, address);
        return resolver.execute(instanceId, admin -> {
            try {
                requireRegistered(admin, brokerName, address);
                return read(admin, brokerName, address);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Read-ahead inspection was interrupted");
            }
        });
    }

    public Receipt apply(Request request) {
        validate(request.brokerName(), request.address());
        if (request.enabled() == null || request.expectedEnabled() == null) {
            throw new BusinessException(400, "Requested and reviewed read-ahead values are required");
        }
        return resolver.execute(request.instanceId(), admin -> {
            Snapshot before;
            try {
                requireRegistered(admin, request.brokerName(), request.address());
                before = read(admin, request.brokerName(), request.address());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Read-ahead preflight was interrupted");
            }
            if (before.enabled() != request.expectedEnabled()) {
                throw new BusinessException(409, "Read-ahead configuration changed; inspect again");
            }
            if (before.enabled() == request.enabled()) {
                return new Receipt("UNCHANGED", false, before, before);
            }
            boolean acknowledged = false;
            Snapshot observed = null;
            try {
                // Native command applies MADV_NORMAL (0) or MADV_RANDOM (1) to existing mapped files.
                admin.setCommitLogReadAheadMode(request.address(), request.enabled() ? "0" : "1");
                acknowledged = true;
                observed = read(admin, request.brokerName(), request.address());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            String status = acknowledged && observed != null && observed.enabled() == request.enabled()
                    ? "CONFIG_CONFIRMED" : "UNKNOWN";
            audit.record("SET_COMMITLOG_READ_AHEAD", "BROKER", request.brokerName(), request.instanceId(),
                    "address=" + request.address() + ", before=" + before.enabled() + ", requested=" + request.enabled(),
                    status, "UNKNOWN".equals(status) ? "Read configuration and broker logs before recovery" : null);
            return new Receipt(status, acknowledged, before, observed);
        });
    }

    private Snapshot read(MQAdminExt admin, String brokerName, String address) throws Exception {
        var properties = admin.getBrokerConfig(address);
        String value = properties == null ? null : properties.getProperty("dataReadAheadEnable");
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new BusinessException(422, "Broker does not expose a supported dataReadAheadEnable value");
        }
        return new Snapshot(brokerName, address, Boolean.parseBoolean(value), Instant.now());
    }

    private void requireRegistered(MQAdminExt admin, String brokerName, String address) throws Exception {
        var cluster = admin.examineBrokerClusterInfo();
        if (cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Broker registry is unavailable");
        }
        var broker = cluster.getBrokerAddrTable().get(brokerName);
        if (broker == null || broker.getBrokerAddrs() == null || !broker.getBrokerAddrs().containsValue(address)) {
            throw new BusinessException(404, "Broker address is not registered in the selected instance");
        }
    }

    private void validate(String brokerName, String address) {
        if (!StringUtils.hasText(brokerName) || !StringUtils.hasText(address)) {
            throw new BusinessException(400, "Broker name and registered address are required");
        }
    }
}
