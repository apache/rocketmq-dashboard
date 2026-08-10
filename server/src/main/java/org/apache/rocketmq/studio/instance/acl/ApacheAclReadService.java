/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.acl;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApacheAclReadService {
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    public RemoteAclReadResult listRules(String instanceId, String subject, String resource) {
        return runtimeAdminClientResolver.execute(instanceId, admin -> {
            Map<String, List<AclInfo>> policies = new LinkedHashMap<>();
            Map<String, String> failures = new LinkedHashMap<>();
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
                return result(policies, failures);
            }
            for (BrokerData broker : clusterInfo.getBrokerAddrTable().values()) {
                String address = masterAddress(broker);
                if (address == null || policies.containsKey(address) || failures.containsKey(address)) {
                    continue;
                }
                try {
                    policies.put(address, List.copyOf(admin.listAcl(address, subject, resource)));
                } catch (Exception ex) {
                    failures.put(address, rootMessage(ex));
                }
            }
            return result(policies, failures);
        });
    }

    private RemoteAclReadResult result(Map<String, List<AclInfo>> policies,
                                       Map<String, String> failures) {
        return RemoteAclReadResult.builder().source("APACHE_ACL2")
                .policiesByBroker(Map.copyOf(policies)).failuresByBroker(Map.copyOf(failures)).build();
    }

    private String masterAddress(BrokerData broker) {
        if (broker == null || broker.getBrokerAddrs() == null) {
            return null;
        }
        return broker.getBrokerAddrs().get(MixAll.MASTER_ID);
    }

    private String rootMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
