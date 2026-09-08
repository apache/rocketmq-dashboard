/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.protocol.body.BrokerReplicasInfo;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ControllerReplicaService {
    private final RuntimeAdminClientResolver resolver;

    public ControllerReplicaSnapshot inspect(String instanceId, String brokerName) {
        if (!StringUtils.hasText(brokerName)) {
            throw new BusinessException(400, "Broker name is required");
        }
        return resolver.execute(instanceId, admin -> {
            try {
                return inspect(admin, brokerName);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new BusinessException(502, "Controller inspection was interrupted");
            }
        });
    }

    private ControllerReplicaSnapshot inspect(MQAdminExt admin, String brokerName) throws Exception {
        var cluster = admin.examineBrokerClusterInfo();
        if (cluster == null || cluster.getBrokerAddrTable() == null) {
            throw new BusinessException(502, "Registered broker metadata is unavailable");
        }
        var broker = cluster.getBrokerAddrTable().get(brokerName);
        if (broker == null || broker.getBrokerAddrs() == null) {
            throw new BusinessException(404, "Broker is not registered in the selected instance");
        }
        String source = broker.getBrokerAddrs().get(0L);
        if (!StringUtils.hasText(source)) {
            source = broker.getBrokerAddrs().values().stream().filter(StringUtils::hasText).sorted()
                    .findFirst().orElseThrow(() -> new BusinessException(404, "Broker has no registered address"));
        }
        var config = admin.getBrokerConfig(source);
        if (config == null) {
            throw new BusinessException(502, "Broker configuration is unavailable");
        }
        String enabled = config.getProperty("enableControllerMode");
        String mode = "true".equalsIgnoreCase(enabled) ? "ENABLED"
                : "false".equalsIgnoreCase(enabled) ? "DISABLED" : "UNKNOWN";
        var addresses = Arrays.stream(config.getProperty("controllerAddr", "").split(";"))
                .map(String::trim).filter(StringUtils::hasText).distinct().sorted().toList();
        if (!"ENABLED".equals(mode) || addresses.isEmpty()) {
            return new ControllerReplicaSnapshot(brokerName, source, mode, Instant.now(), List.of(),
                    "UNAVAILABLE", null, "Controller mode is disabled, unknown, or has no configured addresses");
        }
        var nodes = new ArrayList<ControllerReplicaSnapshot.Node>();
        for (String address : addresses) {
            try {
                var header = admin.getControllerMetaData(address);
                if (header == null) {
                    throw new BusinessException(502, "Controller returned no metadata");
                }
                nodes.add(new ControllerReplicaSnapshot.Node(address, header.getGroup(), header.getControllerLeaderId(),
                        header.getControllerLeaderAddress(), header.isLeader(), header.getPeers(), null));
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                nodes.add(new ControllerReplicaSnapshot.Node(address, null, null, null, null, null, error(exception)));
            }
        }
        ControllerReplicaSnapshot.Membership membership = null;
        String membershipError = "No controller reported a leader address";
        var discovery = nodes.stream().filter(node -> node.error() == null
                && StringUtils.hasText(node.leaderAddress())).findFirst();
        if (discovery.isPresent()) {
            String address = discovery.get().address();
            try {
                // The SDK discovers the leader again; this is not the preceding metadata snapshot.
                var response = admin.getInSyncStateData(address, List.of(brokerName));
                var info = response == null || response.getReplicasInfoTable() == null
                        ? null : response.getReplicasInfoTable().get(brokerName);
                if (info == null || info.getInSyncReplicas() == null || info.getNotInSyncReplicas() == null) {
                    throw new BusinessException(502, "Controller has no complete replica record for this broker");
                }
                var replicas = new ArrayList<ControllerReplicaSnapshot.Replica>();
                var ids = new HashSet<Long>();
                append(replicas, ids, info.getInSyncReplicas(), brokerName, true);
                append(replicas, ids, info.getNotInSyncReplicas(), brokerName, false);
                replicas.sort(Comparator.comparing(row -> Long.parseLong(row.brokerId())));
                membership = new ControllerReplicaSnapshot.Membership(address,
                        info.getMasterBrokerId() == null ? null : info.getMasterBrokerId().toString(),
                        info.getMasterAddress(), info.getMasterEpoch(), info.getSyncStateSetEpoch(), List.copyOf(replicas));
                membershipError = null;
            } catch (InterruptedException exception) {
                throw exception;
            } catch (Exception exception) {
                membershipError = error(exception);
            }
        }
        var successful = nodes.stream().filter(node -> node.error() == null).toList();
        String agreement = "UNAVAILABLE";
        if (!successful.isEmpty()) {
            var first = successful.getFirst();
            boolean same = successful.stream().allMatch(node -> Objects.equals(first.group(), node.group())
                    && Objects.equals(first.leaderId(), node.leaderId())
                    && Objects.equals(first.leaderAddress(), node.leaderAddress()));
            agreement = !same ? "DIVERGENT" : successful.size() < nodes.size() ? "PARTIAL" : "MATCHING";
        }
        return new ControllerReplicaSnapshot(brokerName, source, mode, Instant.now(), List.copyOf(nodes),
                agreement, membership, membershipError);
    }

    private void append(List<ControllerReplicaSnapshot.Replica> rows, HashSet<Long> ids,
            List<BrokerReplicasInfo.ReplicaIdentity> replicas, String brokerName, boolean inSync) {
        for (var replica : replicas) {
            if (replica == null || !brokerName.equals(replica.getBrokerName()) || replica.getBrokerId() == null
                    || replica.getBrokerId() < 0 || !ids.add(replica.getBrokerId())) {
                throw new BusinessException(502, "Replica identity is invalid or appears in both sets");
            }
            rows.add(new ControllerReplicaSnapshot.Replica(replica.getBrokerId().toString(),
                    replica.getBrokerAddress(), inSync, replica.getAlive()));
        }
    }

    private String error(Exception exception) {
        if (exception instanceof BusinessException) {
            return exception.getMessage();
        }
        if (exception instanceof MQBrokerException brokerException) {
            return "Broker response code: " + brokerException.getResponseCode();
        }
        return "Controller request failed: " + exception.getClass().getSimpleName();
    }
}
