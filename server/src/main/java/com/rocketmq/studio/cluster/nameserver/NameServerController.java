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
package com.rocketmq.studio.cluster.nameserver;

import com.rocketmq.studio.cluster.broker.ClusterService;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/nameservers")
@RequiredArgsConstructor
public class NameServerController {

    private final ClusterService clusterService;

    @PostMapping("/create")
    public Result<NameServerVO> createNameServer(@RequestBody CreateNameServerDTO command) {
        return Result.ok(clusterService.createNameServer(command));
    }

    @PostMapping("/update")
    public Result<Void> updateNameServer(@RequestBody UpdateNameServerDTO command) {
        clusterService.updateNameServer(command);
        return Result.ok();
    }

    @PostMapping("/restart")
    public Result<Map<String, Boolean>> restartNameServer(@RequestBody RestartNameServerDTO command) {
        boolean success = clusterService.restartNameServer(command);
        return Result.ok(Map.of("success", success));
    }

    @PostMapping("/upgrade")
    public Result<Map<String, Boolean>> upgradeNameServer(@RequestBody UpgradeNameServerDTO command) {
        boolean success = clusterService.upgradeNameServer(command);
        return Result.ok(Map.of("success", success));
    }

    @PostMapping("/delete")
    public Result<Map<String, Boolean>> deleteNameServer(@RequestBody DeleteNameServerDTO command) {
        boolean success = clusterService.deleteNameServer(command);
        return Result.ok(Map.of("success", success));
    }
}
