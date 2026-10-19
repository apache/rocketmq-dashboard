/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.instance.acl;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class ApacheAclReadService {
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    public RemoteAclReadResult listRules(String instanceId, String subject, String resource) {
        String configuredCluster = StringUtils.hasText(instanceId)
                ? runtimeAdminClientResolver.configuredClusterName(instanceId) : null;
        return runtimeAdminClientResolver.execute(instanceId, admin -> {
            Map<String, List<RemoteAclPolicyVO>> policies = new LinkedHashMap<>();
            Map<String, String> failures = new LinkedHashMap<>();
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
                return result(policies, failures);
            }
            for (String brokerName : scopedBrokerNames(clusterInfo, configuredCluster)) {
                String address = masterAddress(clusterInfo.getBrokerAddrTable().get(brokerName));
                if (address == null || policies.containsKey(address) || failures.containsKey(address)) {
                    continue;
                }
                try {
                    policies.put(address, mapPolicies(admin.listAcl(address, subject, resource)));
                } catch (Exception ex) {
                    failures.put(address, rootMessage(ex));
                }
            }
            return result(policies, failures);
        });
    }

    /**
     * The broker set this read is allowed to touch. An instance bound to one cluster on a NameServer
     * shared by several must not surface another cluster's ACL, so the topology is narrowed the same
     * way {@code RocketMQClusterProvider.discoverBrokers} narrows it; an instance that names no
     * cluster keeps every broker of the endpoint. A configured cluster whose membership is missing
     * from the topology cannot be resolved, so the read fails closed instead of reading the whole
     * cluster table (mirrors the 503 that broker discovery raises for the same condition).
     */
    private Set<String> scopedBrokerNames(ClusterInfo clusterInfo, String configuredCluster) {
        Set<String> names = new TreeSet<>(clusterInfo.getBrokerAddrTable().keySet());
        if (configuredCluster == null) {
            return names;
        }
        Map<String, Set<String>> clusters = clusterInfo.getClusterAddrTable();
        if (clusters == null || clusters.containsKey(configuredCluster)
                && clusters.get(configuredCluster) == null) {
            throw new BusinessException(503, "Broker cluster membership is unavailable");
        }
        names.retainAll(clusters.getOrDefault(configuredCluster, Set.of()));
        return names;
    }

    private List<RemoteAclPolicyVO> mapPolicies(List<AclInfo> brokerPolicies) {
        if (brokerPolicies == null) {
            return List.of();
        }
        return brokerPolicies.stream()
                .filter(Objects::nonNull)
                .map(RemoteAclPolicyVO::from)
                .toList();
    }

    private RemoteAclReadResult result(Map<String, List<RemoteAclPolicyVO>> policies,
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
