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
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import lombok.extern.slf4j.Slf4j;
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

    private final InstanceRepository instanceRepository;
    private final RestTemplate restTemplate;

    public ProxyAddressService(InstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restTemplate = new RestTemplate(factory);
    }

    public ProxyHomeVO getHomePage() {
        List<String> proxyAddrs = listConfiguredProxyEndpoints();
        return ProxyHomeVO.builder()
                .proxyAddrList(proxyAddrs)
                .currentProxyAddr(proxyAddrs.isEmpty() ? "" : proxyAddrs.get(0))
                .build();
    }

    /**
     * A managed Proxy instance owns its access endpoint. This compat surface returns the selected
     * instance endpoint without inventing additional cluster-global proxy addresses.
     */
    public ProxyHomeVO getHomePage(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (instance.getType() != InstanceType.PROXY) {
            throw new BusinessException(400, "Instance is not a Proxy instance: " + instanceId);
        }
        String endpoint = normalizeConfiguredEndpoint(instance.getEndpoint(), instanceId);
        return ProxyHomeVO.builder()
                .proxyAddrList(List.of(endpoint))
                .currentProxyAddr(endpoint)
                .build();
    }

    public void addProxyAddr(String newProxyAddr) {
        throw new UnsupportedOperationException("Update the managed instance endpoint instead");
    }

    public void removeProxyAddr(String proxyAddr) {
        throw new UnsupportedOperationException("Update the managed instance endpoint instead");
    }

    /**
     * Trigger a configuration hot-reload for the proxy at the given address.
     * POSTs to {@code http://<addr>/admin/reloadConfig}. Throws {@link BusinessException}
     * on transport or protocol failure so the caller receives a structured error response.
     */
    public void reloadConfig(String addr) {
        String normalized = normalizeProxyAddr(addr, "addr");
        if (listConfiguredProxyEndpoints().stream().noneMatch(normalized::equals)) {
            throw new BusinessException(400, "addr is not a configured proxy endpoint");
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

    private List<String> listConfiguredProxyEndpoints() {
        return instanceRepository.findByType(InstanceType.PROXY).stream()
                .map(InstanceVO::getEndpoint)
                .filter(endpoint -> endpoint != null && !endpoint.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String normalizeConfiguredEndpoint(String endpoint, String instanceId) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(409, "Proxy instance has no configured endpoint: " + instanceId);
        }
        return normalizeProxyAddr(endpoint, "endpoint");
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
