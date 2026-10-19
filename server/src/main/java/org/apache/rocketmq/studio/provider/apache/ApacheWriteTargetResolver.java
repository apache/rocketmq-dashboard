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
package org.apache.rocketmq.studio.provider.apache;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

/** A write target must come from trusted ownership or a unique topology; never guess the physical cluster from the instance name. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApacheWriteTargetResolver {
    public record Target(String cluster, Set<String> masters, Set<String> brokers) { }

    public static Target resolve(MQAdminExt admin, InstanceVO instance, String ownedCluster) throws Exception {
        ClusterInfo info = admin.examineBrokerClusterInfo();
        if (info == null || info.getClusterAddrTable() == null || info.getBrokerAddrTable() == null
                || info.getClusterAddrTable().isEmpty()) {
            throw new BusinessException(409, "Incomplete cluster topology; write rejected");
        }
        String cluster = ownedCluster;
        if (instance.getId() == null) {
            if (StringUtils.hasText(cluster) && !instance.getName().equals(cluster)) {
                throw new BusinessException(409, "Virtual instance does not match the physical cluster of the resource");
            }
            cluster = instance.getName();
        } else if (!StringUtils.hasText(cluster)) {
            if (info.getClusterAddrTable().size() != 1) {
                throw new BusinessException(409, "Registered instance is not bound to an explicit physical cluster; writes are rejected on multi-cluster endpoints");
            }
            cluster = info.getClusterAddrTable().keySet().iterator().next();
            for (BrokerData broker : info.getBrokerAddrTable().values()) {
                if (!StringUtils.hasText(cluster) || broker == null || !cluster.equals(broker.getCluster())) {
                    throw new BusinessException(409, "Cluster table and broker table are inconsistent; topology is not uniquely determined");
                }
            }
        }
        return target(info, cluster);
    }

    public static Target target(ClusterInfo info, String cluster) {
        if (!StringUtils.hasText(cluster) || info == null || info.getClusterAddrTable() == null
                || info.getBrokerAddrTable() == null) {
            throw new BusinessException(409, "Cannot determine the target cluster; write rejected");
        }
        Set<String> names = info.getClusterAddrTable().get(cluster);
        if (names == null || names.isEmpty()) {
            throw new BusinessException(409, "Target cluster does not exist or has no broker: " + cluster);
        }
        for (var entry : info.getBrokerAddrTable().entrySet()) {
            BrokerData broker = entry.getValue();
            if (broker != null && cluster.equals(broker.getCluster()) && !names.contains(entry.getKey())) {
                throw new BusinessException(409, "Target cluster is missing a broker; incomplete topology rejected: " + entry.getKey());
            }
        }
        Set<String> masters = new HashSet<>();
        for (String name : names) {
            BrokerData broker = info.getBrokerAddrTable().get(name);
            if (!StringUtils.hasText(name) || broker == null || !cluster.equals(broker.getCluster())
                    || !name.equals(broker.getBrokerName())
                    || broker.getBrokerAddrs() == null || !StringUtils.hasText(broker.getBrokerAddrs().get(0L))) {
                throw new BusinessException(409, "Incomplete broker/master topology in the target cluster: " + name);
            }
            masters.add(broker.getBrokerAddrs().get(0L));
        }
        return new Target(cluster, Set.copyOf(masters), Set.copyOf(names));
    }

    /** Name-addressed send/reset RPCs must reject cross-cluster aggregated routes so the SDK cannot fan out on its own. */
    public static void requireTopicRoute(MQAdminExt admin, Target target, String topic) throws Exception {
        var route = admin.examineTopicRouteInfo(topic);
        if (route == null || route.getBrokerDatas() == null || route.getBrokerDatas().isEmpty()) {
            throw new BusinessException(409, "Incomplete topic route; resource write rejected: " + topic);
        }
        for (BrokerData broker : route.getBrokerDatas()) {
            if (broker == null || !target.cluster().equals(broker.getCluster())
                    || !target.brokers().contains(broker.getBrokerName()) || broker.getBrokerAddrs() == null
                    || !target.masters().contains(broker.getBrokerAddrs().get(0L))) {
                throw new BusinessException(409, "Topic route contains a broker outside the target cluster: " + topic);
            }
        }
        if (route.getQueueDatas() == null || route.getQueueDatas().isEmpty()
                || route.getQueueDatas().stream().anyMatch(queue -> queue == null
                || !target.brokers().contains(queue.getBrokerName()))) {
            throw new BusinessException(409, "Incomplete topic queue route: " + topic);
        }
    }
}
