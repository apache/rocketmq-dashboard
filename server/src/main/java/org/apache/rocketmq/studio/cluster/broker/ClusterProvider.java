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
package org.apache.rocketmq.studio.cluster.broker;


import java.util.List;

public interface ClusterProvider {

    List<ClusterVO> discoverClusters();

    default List<ClusterVO> discoverClusters(String instanceId) {
        return instanceId == null || instanceId.isBlank() ? discoverClusters() : List.of();
    }

    default List<ClusterVO> discoverClustersAt(String namesrvAddr) {
        return List.of();
    }

    default List<org.apache.rocketmq.studio.cluster.proxy.ProxyVO> discoverProxies(String instanceId) {
        throw new UnsupportedOperationException("Instance Proxy discovery is not supported");
    }

    default List<BrokerVO> discoverBrokers(String instanceId, String brokerName) {
        throw new UnsupportedOperationException("Instance Broker discovery is not supported");
    }

    ClusterVO refreshClusterDetail(String clusterId);

    default ClusterVO refreshClusterDetail(String clusterId, String instanceId) {
        return instanceId == null || instanceId.isBlank() ? refreshClusterDetail(clusterId) : null;
    }
}
