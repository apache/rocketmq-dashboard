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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProxyAddressService {

    private static final Pattern PROXY_ADDR_PATTERN =
            Pattern.compile("^(\\[[0-9a-fA-F:.]+]|[A-Za-z0-9._-]+):(\\d{1,5})$");
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final Set<String> proxyAddrs = new LinkedHashSet<>(List.of("127.0.0.1:8081"));
    private String currentProxyAddr = "127.0.0.1:8081";

    public synchronized ProxyHomeVO getHomePage() {
        return ProxyHomeVO.builder()
                .proxyAddrList(new ArrayList<>(proxyAddrs))
                .currentProxyAddr(currentProxyAddr)
                .build();
    }

    public synchronized void addProxyAddr(String newProxyAddr) {
        String normalized = normalizeProxyAddr(newProxyAddr, "newProxyAddr");
        proxyAddrs.add(normalized);
        if (currentProxyAddr == null || currentProxyAddr.isBlank()) {
            currentProxyAddr = normalized;
        }
        log.info("Added Proxy address {}", normalized);
    }

    public synchronized void removeProxyAddr(String proxyAddr) {
        String normalized = normalizeProxyAddr(proxyAddr, "proxyAddr");
        if (!proxyAddrs.remove(normalized)) {
            throw new BusinessException(404, "Proxy address not found: " + normalized);
        }
        if (normalized.equals(currentProxyAddr)) {
            currentProxyAddr = proxyAddrs.stream().findFirst().orElse("");
        }
        log.info("Removed Proxy address {}", normalized);
    }

    private String normalizeProxyAddr(String proxyAddr, String fieldName) {
        if (proxyAddr == null || proxyAddr.trim().isEmpty()) {
            throw new BusinessException(400, fieldName + " is required");
        }
        String normalized = proxyAddr.trim();
        Matcher matcher = PROXY_ADDR_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new BusinessException(400, fieldName + " must be in host:port or [ipv6]:port format");
        }
        int port = Integer.parseInt(matcher.group(2));
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new BusinessException(400, fieldName + " port must be between 1 and 65535");
        }
        return normalized;
    }
}
