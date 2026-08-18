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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/nameservers")
@RequiredArgsConstructor
public class NameServerController {

    private final ClusterService clusterService;
    private final NameServerConfigDiffService configDiffService;
    private final NameserverRegistryService registryService;

    @GetMapping
    public Result<List<NameserverRegistryVO>> listRegistry() {
        return Result.ok(registryService.list());
    }

    @PostMapping("/registry/create")
    public Result<NameserverRegistryVO> createRegistryEntry(
            @Valid @RequestBody(required = false) CreateNameserverRegistryDTO command) {
        requireCommand(command);
        return Result.ok(registryService.create(command));
    }

    @PostMapping("/registry/update")
    public Result<NameserverRegistryVO> updateRegistryEntry(
            @Valid @RequestBody(required = false) UpdateNameserverRegistryDTO command) {
        requireCommand(command);
        return Result.ok(registryService.update(command));
    }

    @PostMapping("/registry/delete")
    public Result<Void> deleteRegistryEntry(
            @Valid @RequestBody(required = false) DeleteNameserverRegistryDTO command) {
        requireCommand(command);
        registryService.delete(command.getId());
        return Result.ok();
    }

    @GetMapping("/config-diff")
    public Result<NameServerConfigDiffVO> compareConfiguration(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(configDiffService.compare(clusterId, instanceId));
    }

    @PostMapping("/create")
    public Result<NameServerVO> createNameServer(@Valid @RequestBody(required = false) CreateNameServerDTO command) {
        requireCommand(command);
        return Result.ok(clusterService.createNameServer(command));
    }

    @PostMapping("/update")
    public Result<Void> updateNameServer(@Valid @RequestBody(required = false) UpdateNameServerDTO command) {
        requireCommand(command);
        clusterService.updateNameServer(command);
        return Result.ok();
    }

    @PostMapping("/restart")
    public Result<Void> restartNameServer(
            @Valid @RequestBody(required = false) RestartNameServerDTO command) {
        requireCommand(command);
        boolean success = clusterService.restartNameServer(command);
        requireOperationSuccess(success, "restart");
        return Result.ok();
    }

    @PostMapping("/upgrade")
    public Result<Void> upgradeNameServer(
            @Valid @RequestBody(required = false) UpgradeNameServerDTO command) {
        requireCommand(command);
        boolean success = clusterService.upgradeNameServer(command);
        requireOperationSuccess(success, "upgrade");
        return Result.ok();
    }

    @PostMapping("/delete")
    public Result<Void> deleteNameServer(
            @Valid @RequestBody(required = false) DeleteNameServerDTO command) {
        requireCommand(command);
        boolean success = clusterService.deleteNameServer(command);
        requireOperationSuccess(success, "delete");
        return Result.ok();
    }

    private void requireOperationSuccess(boolean success, String operation) {
        if (!success) {
            throw new BusinessException(500, "Failed to " + operation + " NameServer");
        }
    }

    private void requireCommand(Object command) {
        if (command == null) {
            throw new BusinessException(400, "NameServer request is required");
        }
    }
}
