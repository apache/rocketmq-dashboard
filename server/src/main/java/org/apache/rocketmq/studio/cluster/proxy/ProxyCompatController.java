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

import org.apache.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proxy")
@RequiredArgsConstructor
public class ProxyCompatController {

    private final ProxyAddressService proxyAddressService;

    @GetMapping("/homePage.query")
    public Result<ProxyHomeVO> homePage(@RequestParam String instanceId) {
        return Result.ok(proxyAddressService.getHomePage(instanceId));
    }

    /**
     * Kept for legacy Dashboard clients. Proxy endpoint changes belong to managed-instance
     * configuration; this page must not mutate a process-local address list.
     */
    @PostMapping("/addProxyAddr.do")
    public Result<Void> addProxyAddr() {
        throw new UnsupportedOperationException("Update the managed instance endpoint instead");
    }

    @PostMapping("/removeProxyAddr.do")
    public Result<Void> removeProxyAddr() {
        throw new UnsupportedOperationException("Update the managed instance endpoint instead");
    }

}
