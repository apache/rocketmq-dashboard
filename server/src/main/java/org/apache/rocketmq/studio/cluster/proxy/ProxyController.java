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
package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proxies")
@RequiredArgsConstructor
public class ProxyController {

    private final ClusterService clusterService;
    private final ProxyAddressService proxyAddressService;

    @GetMapping
    public Result<List<ProxyVO>> listProxies() {
        List<ProxyVO> proxies = proxyAddressService.getHomePage().getProxyAddrList().stream()
                .map(addr -> ProxyVO.builder().addr(addr).build())
                .toList();
        return Result.ok(proxies);
    }

    @PostMapping("/config/reload")
    public Result<Map<String, Boolean>> reloadProxyConfig(@Valid @RequestBody ProxyConfigReloadDTO command) {
        proxyAddressService.reloadConfig(command.getAddr());
        return Result.ok(Map.of("success", true));
    }

    @PostMapping("/restart")
    public Result<Void> restartProxy(@Valid @RequestBody RestartProxyDTO command) {
        boolean success = clusterService.restartProxy(command);
        if (!success) {
            throw new BusinessException(500, "Failed to restart proxy");
        }
        return Result.ok();
    }
}
