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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The brokers of an instance's endpoint that one ACL operation is allowed to touch.
 *
 * <p>An instance bound to a single cluster on a NameServer shared by several must neither surface nor
 * mutate another cluster's brokers, so the topology is narrowed the same way broker discovery narrows
 * it ({@code RocketMQClusterProvider.discoverBrokers}). An instance that names no cluster keeps every
 * broker of the endpoint, and a configured cluster whose membership cannot be resolved fails closed
 * instead of falling back to the whole cluster table.
 *
 * <p>The ACL read and the ACL push both resolve their broker set through this method: if they
 * disagreed, the page would show one cluster's rules while a push wrote the dashboard's accounts and
 * policies into another cluster's brokers.
 */
final class AclBrokerScope {

    private AclBrokerScope() {
    }

    static Set<String> scopedBrokerNames(ClusterInfo clusterInfo, String configuredCluster) {
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
}
