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
package com.rocketmq.studio.ops.ai.tool;

import com.rocketmq.studio.cluster.broker.ClusterService;
import com.rocketmq.studio.cluster.broker.ClusterVO;
import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CapabilityResolver {

    private final ClusterService clusterService;

    public List<String> resolve(String clusterId) {
        return resolve(clusterService.getCluster(clusterId));
    }

    List<String> resolve(ClusterVO cluster) {
        if (cluster.getType() == null) {
            throw new BusinessException(
                    400, "Cluster type is unavailable: " + cluster.getId());
        }
        return switch (cluster.getType()) {
            case V4_DIRECT -> List.of(
                    "REMOTING",
                    "ROCKETMQ_4");
            case V5_PROXY_LOCAL -> List.of(
                    "ACL_V2",
                    "GRPC",
                    "LITE_TOPIC",
                    "LOCAL_PROXY",
                    "POP",
                    "REMOTING",
                    "ROCKETMQ_5");
            case V5_PROXY_CLUSTER -> List.of(
                    "ACL_V2",
                    "CLUSTER_PROXY",
                    "GRPC",
                    "LITE_TOPIC",
                    "POP",
                    "REMOTING",
                    "ROCKETMQ_5");
        };
    }
}
