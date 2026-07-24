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

package com.rocketmq.studio.cluster.proxy;

import com.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ProxyAddressService {

    private final Set<String> proxyAddrs = new LinkedHashSet<>(List.of("127.0.0.1:8081"));
    private String currentProxyAddr = "127.0.0.1:8081";

    public synchronized ProxyHomeVO getHomePage() {
        return ProxyHomeVO.builder()
                .proxyAddrList(new ArrayList<>(proxyAddrs))
                .currentProxyAddr(currentProxyAddr)
                .build();
    }

    public synchronized void addProxyAddr(String newProxyAddr) {
        String normalized = normalizeProxyAddr(newProxyAddr);
        proxyAddrs.add(normalized);
        if (currentProxyAddr == null || currentProxyAddr.isBlank()) {
            currentProxyAddr = normalized;
        }
        log.info("Added Proxy address {}", normalized);
    }

    private String normalizeProxyAddr(String proxyAddr) {
        if (proxyAddr == null || proxyAddr.trim().isEmpty()) {
            throw new BusinessException(400, "newProxyAddr is required");
        }
        return proxyAddr.trim();
    }
}
