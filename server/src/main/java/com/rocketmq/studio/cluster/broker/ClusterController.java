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
package com.rocketmq.studio.cluster.broker;

import com.rocketmq.studio.cluster.capability.ClusterCapabilityService;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO;
import com.rocketmq.studio.cluster.config.UpdateConfigDTO;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final ClusterCapabilityService clusterCapabilityService;

    @GetMapping
    public Result<List<ClusterVO>> listClusters() {
        return Result.ok(clusterService.listClusters());
    }

    @GetMapping("/{id}")
    public Result<ClusterVO> getCluster(@PathVariable String id) {
        return Result.ok(clusterService.getCluster(id));
    }

    @GetMapping("/{id}/capabilities")
    public Result<ClusterCapabilityVO> getCapabilities(@PathVariable String id) {
        return Result.ok(clusterCapabilityService.getCapabilities(id));
    }

    @PostMapping("/config/update")
    public Result<ClusterVO> updateClusterConfig(@RequestBody UpdateConfigDTO command) {
        return Result.ok(clusterService.updateClusterConfig(command));
    }

    @PostMapping("/{clusterId}/brokers/{name}/restart")
    public Result<Map<String, Object>> restartBroker(@PathVariable String clusterId,
                                                     @PathVariable String name) {
        boolean success = clusterService.restartBroker(clusterId, name);
        return Result.ok(Map.of(
                "success", success,
                "message", "Broker restart initiated for " + name
        ));
    }
}
