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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

import java.util.List;
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
    static final String DEFAULT_SCOPE = "default";
    static final String DEFAULT_PROXY_ADDRESS = "127.0.0.1:8081";

    private final ProxyAddressRepository repository;
    private final RestTemplate restTemplate;

    @Autowired
    public ProxyAddressService(ProxyAddressRepository repository) {
        this(repository, createRestTemplate());
    }

    ProxyAddressService(ProxyAddressRepository repository, RestTemplate restTemplate) {
        this.repository = repository;
        this.restTemplate = restTemplate;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return new RestTemplate(factory);
    }

    public synchronized ProxyHomeVO getHomePage() {
        return getHomePage(DEFAULT_SCOPE);
    }

    public synchronized ProxyHomeVO getHomePage(String scopeId) {
        String scope = normalizeScope(scopeId);
        List<ProxyAddressRecord> records = loadOrCreateDefault(scope);
        return ProxyHomeVO.builder()
                .proxyAddrList(records.stream().map(ProxyAddressRecord::getAddress).toList())
                .currentProxyAddr(records.stream().filter(ProxyAddressRecord::isSelected)
                        .map(ProxyAddressRecord::getAddress).findFirst()
                        .orElse(records.get(0).getAddress()))
                .build();
    }

    public synchronized void addProxyAddr(String newProxyAddr) {
        addProxyAddr(DEFAULT_SCOPE, newProxyAddr);
    }

    public synchronized void addProxyAddr(String scopeId, String newProxyAddr) {
        String scope = normalizeScope(scopeId);
        String normalized = normalizeProxyAddr(newProxyAddr, "newProxyAddr");
        List<ProxyAddressRecord> existing = repository.findByScope(scope);
        if (existing.stream().anyMatch(record -> normalized.equals(record.getAddress()))) {
            return;
        }
        if (!repository.insert(scope, normalized, existing.isEmpty())) {
            throw new BusinessException(500, "Failed to persist Proxy address");
        }
        log.info("Added Proxy address {} for scope {}", normalized, scope);
    }

    public synchronized void removeProxyAddr(String proxyAddr) {
        removeProxyAddr(DEFAULT_SCOPE, proxyAddr);
    }

    public synchronized void removeProxyAddr(String scopeId, String proxyAddr) {
        String scope = normalizeScope(scopeId);
        String normalized = normalizeProxyAddr(proxyAddr, "proxyAddr");
        List<ProxyAddressRecord> before = repository.findByScope(scope);
        ProxyAddressRecord removed = before.stream()
                .filter(record -> normalized.equals(record.getAddress())).findFirst()
                .orElseThrow(() -> new BusinessException(404, "Proxy address not found: " + normalized));
        if (!repository.delete(scope, normalized)) {
            throw new BusinessException(404, "Proxy address not found: " + normalized);
        }
        if (removed.isSelected()) {
            repository.findByScope(scope).stream().findFirst()
                    .ifPresent(next -> repository.select(scope, next.getAddress()));
        }
        log.info("Removed Proxy address {} from scope {}", normalized, scope);
    }

    /**
     * Trigger a configuration hot-reload for the proxy at the given address.
     * POSTs to {@code http://<addr>/admin/reloadConfig}. Throws {@link BusinessException}
     * on transport or protocol failure so the caller receives a structured error response.
     */
    public void reloadConfig(String addr) {
        reloadConfig(DEFAULT_SCOPE, addr);
    }

    public void reloadConfig(String scopeId, String addr) {
        String scope = normalizeScope(scopeId);
        String normalized = normalizeProxyAddr(addr, "addr");
        synchronized (this) {
            if (repository.findByScope(scope).stream()
                    .noneMatch(record -> normalized.equals(record.getAddress()))) {
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

    private List<ProxyAddressRecord> loadOrCreateDefault(String scope) {
        List<ProxyAddressRecord> records = repository.findByScope(scope);
        if (!records.isEmpty()) {
            return records;
        }
        if (!repository.insert(scope, DEFAULT_PROXY_ADDRESS, true)) {
            throw new BusinessException(500, "Failed to initialize Proxy address");
        }
        records = repository.findByScope(scope);
        if (records.isEmpty()) {
            throw new BusinessException(500, "Proxy address persistence returned no records");
        }
        return records;
    }

    private String normalizeScope(String scopeId) {
        return scopeId == null || scopeId.isBlank() ? DEFAULT_SCOPE : scopeId.trim();
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
