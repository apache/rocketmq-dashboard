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

import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigPreviewVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clusters")
@RequiredArgsConstructor
public class ClusterController {

    private final ClusterService clusterService;
    private final ClusterConnectionService clusterConnectionService;

    @GetMapping
    public Result<List<ClusterVO>> listClusters(@RequestParam(required = false) String instanceId) {
        return Result.ok(clusterService.listClusters(instanceId));
    }

    @GetMapping("/registry")
    public Result<List<ClusterVO>> listRegistryClusters() {
        return Result.ok(clusterService.listRegistryClusters());
    }

    @PostMapping("/test-connection")
    public Result<ClusterProbeResult> testConnection(@Valid @RequestBody TestConnectionDTO command) {
        return Result.ok(clusterConnectionService.testConnection(command));
    }

    @GetMapping("/{id}")
    public Result<ClusterVO> getCluster(@PathVariable String id,
                                        @RequestParam(required = false) String instanceId) {
        return Result.ok(clusterService.getCluster(id, instanceId));
    }

    @PostMapping("/config/update")
    public Result<ClusterConfigUpdateResultVO> updateClusterConfig(@Valid @RequestBody(required = false) UpdateConfigDTO command) {
        requireUpdateConfigCommand(command);
        return Result.ok(clusterService.updateClusterConfig(command));
    }

    @PostMapping("/config/preview")
    public Result<ClusterConfigPreviewVO> previewClusterConfig(@Valid @RequestBody(required = false) UpdateConfigDTO command) {
        requireUpdateConfigCommand(command);
        return Result.ok(clusterService.previewClusterConfig(command));
    }

    @PostMapping("/{clusterId}/brokers/{name}/restart")
    public Result<Map<String, Object>> restartBroker(@PathVariable String clusterId,
                                                     @PathVariable String name) {
        boolean success = clusterService.restartBroker(clusterId, name);
        if (!success) {
            throw new BusinessException(500, "Failed to restart broker: " + name);
        }
        return Result.ok(Map.of(
                "message", "Broker restart initiated for " + name
        ));
    }

    private void requireUpdateConfigCommand(UpdateConfigDTO command) {
        if (command == null) {
            throw new BusinessException(400, "Cluster config update request is required");
        }
    }
}
