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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

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

    private static final String RELOAD_PATH = "/admin/reloadConfig";

    private final Set<String> proxyAddrs = new LinkedHashSet<>(List.of("127.0.0.1:8081"));
    private String currentProxyAddr = "127.0.0.1:8081";
    private final RestTemplate restTemplate;

    public ProxyAddressService() {
        NoRedirectClientHttpRequestFactory factory = new NoRedirectClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restTemplate = new RestTemplate(factory);
    }

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

    /**
     * Trigger a configuration hot-reload for the proxy at the given address.
     * POSTs to {@code http://<addr>/admin/reloadConfig}. Throws {@link BusinessException}
     * on transport or protocol failure so the caller receives a structured error response.
     */
    public void reloadConfig(String addr) {
        String normalized = normalizeProxyAddr(addr, "addr");
        synchronized (this) {
            if (!proxyAddrs.contains(normalized)) {
                throw new BusinessException(400, "addr is not a registered proxy address");
            }
        }
        String url = "http://" + normalized + RELOAD_PATH;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new BusinessException(502, "Proxy returned " + status);
            }
            log.info("Proxy {} accepted config reload", normalized);
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(502, "Proxy returned " + ex.getStatusCode());
        } catch (ResourceAccessException ex) {
            log.warn("Unable to reach proxy {} for config reload: {}", normalized, ex.getMessage());
            throw new BusinessException(502, "Unable to reach proxy: " + ex.getMessage());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Proxy config reload via {} failed: {}", url, ex.getMessage());
            throw new BusinessException(500, "Config reload failed: " + ex.getMessage());
        }
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
