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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Verifies live connectivity to a RocketMQ NameServer and summarises the topology it reports.
 *
 * <p>Powers the "test connection" action in the cluster UI: it opens a real admin connection
 * through {@link RealClusterProvider}, so a failure surfaces as a {@code BusinessException} with the
 * underlying cause instead of a fabricated success.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterConnectionService {

    private final RealClusterProvider clusterProvider;

    /**
     * Probes the NameServer described by the command.
     *
     * @param command the connection request holding the NameServer address
     * @return a populated {@link ClusterProbeResult} on success
     */
    public ClusterProbeResult testConnection(TestConnectionDTO command) {
        if (command == null || !StringUtils.hasText(command.getNamesrvAddr())) {
            throw new BusinessException(400, "namesrvAddr is required");
        }
        String namesrvAddr = command.getNamesrvAddr().trim();
        log.info("Testing connection to NameServer {}", namesrvAddr);
        long start = System.currentTimeMillis();
        ClusterVO cluster = clusterProvider.describeCluster(namesrvAddr);
        long elapsed = System.currentTimeMillis() - start;

        List<String> brokerNames = cluster.getBrokers() == null ? List.of()
                : cluster.getBrokers().stream().map(BrokerVO::getName).toList();

        return ClusterProbeResult.builder()
                .connected(true)
                .namesrvAddr(namesrvAddr)
                .clusterName(cluster.getName())
                .brokerCount(brokerNames.size())
                .brokerNames(brokerNames)
                .elapsedMillis(elapsed)
                .message("Connected to " + brokerNames.size() + " broker(s) in " + elapsed + "ms")
                .build();
    }
}
