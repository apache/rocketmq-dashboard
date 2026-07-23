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
package com.rocketmq.studio.cluster.capability;

import com.rocketmq.studio.cluster.broker.ClusterRepository;
import com.rocketmq.studio.cluster.broker.ClusterVO;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO.CapabilityStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import com.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

@Service
public class ClusterCapabilityService {
    private static final String V4_UNSUPPORTED_REASON =
            "Capability is not available on the v4 direct path";

    private final ClusterRepository clusterRepository;

    public ClusterCapabilityService(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }

    public ClusterCapabilityVO getCapabilities(String clusterId) {
        ClusterVO cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId));
        return new ClusterCapabilityVO(clusterId, cluster.getType(), configuredDefaults(cluster.getType()));
    }

    private Map<ClusterCapability, CapabilityStatus> configuredDefaults(ClusterType clusterType) {
        EnumMap<ClusterCapability, CapabilityStatus> defaults =
                new EnumMap<>(ClusterCapability.class);
        if (clusterType == ClusterType.V4_DIRECT) {
            defaults.put(ClusterCapability.GRPC_ADMIN, CapabilityStatus.unsupported(V4_UNSUPPORTED_REASON));
            defaults.put(ClusterCapability.LITE_TOPIC, CapabilityStatus.unsupported(V4_UNSUPPORTED_REASON));
            defaults.put(ClusterCapability.POP_CONSUME, CapabilityStatus.unsupported(V4_UNSUPPORTED_REASON));
            defaults.put(ClusterCapability.BATCH_CONSUME, CapabilityStatus.unsupported(V4_UNSUPPORTED_REASON));
        }
        return defaults;
    }
}
