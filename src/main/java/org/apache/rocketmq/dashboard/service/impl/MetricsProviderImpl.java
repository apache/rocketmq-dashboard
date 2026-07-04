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
package org.apache.rocketmq.dashboard.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.MetricsDataSourceConfig;
import org.apache.rocketmq.dashboard.model.MetricsHealthResult;
import org.apache.rocketmq.dashboard.service.MetricsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link MetricsProvider}.
 * <p>
 * Proxies all PromQL queries to an external Prometheus-compatible HTTP API.
 * Supports authentication modes: none, basic, bearer.
 * Uses JDK 17 built-in HttpClient (no extra dependency required).
 * </p>
 * <h3>Configuration source</h3>
 * <ul>
 *   <li>Data source URL is resolved from these sources (first match wins):
 *       <ol>
 *           <li>{@code rocketmq.config.metrics.datasource.url} (Spring Boot config)</li>
 *           <li>{@code ROCKETMQ_METRICS_DATASOURCE_URL} (system env)</li>
 *       </ol>
 *   </li>
 *   <li>Credentials come from either {@link MetricsDataSourceConfig} (loaded from
 *       {@code rocketmq.config.datasource.*} config keys) or system env vars.</li>
 * </ul>
 */
@Service
public class MetricsProviderImpl implements MetricsProvider {

    private static final Logger log = LoggerFactory.getLogger(MetricsProviderImpl.class);

    /** Minimum scrape interval (seconds) used when no explicit value is configured. */
    private static final int DEFAULT_SCRAPE_INTERVAL = 15;

    /** Connect timeout for HTTP requests (seconds). */
    private static final int CONNECT_TIMEOUT_S = 10;

    /** Read timeout for HTTP requests (seconds). */
    private static final int READ_TIMEOUT_S = 30;

    /** Prefix for RocketMQ 5.x OpenTelemetry prometheus-exporter metric families. */
    private static final String METRIC_FAMILY_PREFIX = "rocketmq_";

    /** Required metric families checked during health probe. */
    private static final List<String> REQUIRED_METRIC_FAMILIES = Arrays.asList(
            "rocketmq_remoting_",
            "rocketmq_broker_",
            "rocketmq_proxy_",
            "rocketmq_client_",
            "rocketmq_topic_",
            "rocketmq_group_"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Resource
    private RMQConfigure rmqConfigure;

    /** Resolved data source URL; lazily initialized. */
    private volatile String datasourceUrl;

    /** Authentication type (resolved from env / config). */
    private volatile String authType;

    /** Basic-auth username. */
    private volatile String username;

    /** Basic-auth password. */
    private volatile String password;

    /** Bearer token. */
    private volatile String bearerToken;

    /** Cached label list to avoid repeated discovery calls. */
    private transient volatile List<String> cachedLabelNames;

    private transient volatile long cacheExpireMs;

    public MetricsProviderImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ======================== Lifecycle / Config ========================

    /**
     * Re-initialize provider configuration from environment / config keys.
     * Call after any runtime config change.
     */
    public synchronized void refresh() {
        this.datasourceUrl = resolveDatasourceUrl();
        this.authType = resolveAuthType();
        this.username = resolveUsername();
        this.password = resolvePassword();
        this.bearerToken = resolveBearerToken();
        this.cachedLabelNames = null;
        this.cacheExpireMs = 0;
        if (datasourceUrl != null) {
            log.info("MetricsProvider initialized -> url={}, authType={}", datasourceUrl, authType);
        } else {
            log.warn("MetricsProvider NOT initialized — no data source URL configured.");
        }
    }

    private String resolveDatasourceUrl() {
        try {
            // Check Spring-managed config keys
            String[] keys = {"rocketmq.config.metrics.datasource.url", "rocketmq.config.datasource.url"};
            for (String key : keys) {
                Object prop = getSpringProperty(key);
                if (prop != null && !prop.toString().isEmpty()) {
                    return normalizeUrl(prop.toString());
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve datasource URL from Spring config: {}", e.getMessage());
        }
        // Fallback to system environment variable
        String env = System.getenv("ROCKETMQ_METRICS_DATASOURCE_URL");
        if (env != null && !env.isEmpty()) {
            return normalizeUrl(env);
        }
        return null;
    }

    private String resolveAuthType() {
        try {
            String val = (String) getSpringProperty("rocketmq.config.metrics.datasource.auth.type");
            if (val != null) {
                return val.toLowerCase();
            }
        } catch (Exception ignored) {
        }
        String env = System.getenv("ROCKETMQ_METRICS_AUTH_TYPE");
        return env != null ? env.toLowerCase() : "none";
    }

    private String resolveUsername() {
        try {
            String val = (String) getSpringProperty("rocketmq.config.metrics.datasource.username");
            if (val != null) return val;
        } catch (Exception ignored) {
        }
        return System.getenv("ROCKETMQ_METRICS_USERNAME");
    }

    private String resolvePassword() {
        try {
            String val = (String) getSpringProperty("rocketmq.config.metrics.datasource.password");
            if (val != null) return val;
        } catch (Exception ignored) {
        }
        return System.getenv("ROCKETMQ_METRICS_PASSWORD");
    }

    private String resolveBearerToken() {
        try {
            String val = (String) getSpringProperty("rocketmq.config.metrics.datasource.bearer.token");
            if (val != null) return val;
        } catch (Exception ignored) {
        }
        return System.getenv("ROCKETMQ_METRICS_BEARER_TOKEN");
    }

    /**
     * Retrieve a Spring property value by key (works with @ConfigurationProperties beans).
     * This is a minimal fallback for environments where we cannot inject Environment.
     */
    @SuppressWarnings("unchecked")
    private Object getSpringProperty(String key) {
        // Try via System.getProperty as a quick path
        String propVal = System.getProperty(key);
        if (propVal != null && !propVal.isEmpty()) {
            return propVal;
        }
        // Try via RMQConfigure getter — some frameworks expose arbitrary properties
        // through the configure object; we attempt reflection-based access generically.
        try {
            java.lang.reflect.Method m = rmqConfigure.getClass()
                    .getMethod("get" + capitalize(key));
            return m.invoke(rmqConfigure);
        } catch (Exception e) {
            // Not available; fall through
        }
        return null;
    }

    private static String normalizeUrl(String raw) {
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ======================== Query Proxy ========================

    @Override
    public Object queryInstant(String promQL, long time, double step) {
        ensureInitialized();
        String endpoint = "/api/v1/query";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", promQL);
        params.put("time", String.valueOf(time));
        String url = buildApiUrl(endpoint, params);
        return executeGet(url, "instant query: " + truncate(promQL, 80));
    }

    @Override
    public Object queryRange(String promQL, long startTime, long endTime, double step) {
        ensureInitialized();
        String endpoint = "/api/v1/query_range";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", promQL);
        params.put("start", String.valueOf(startTime));
        params.put("end", String.valueOf(endTime));
        params.put("step", String.valueOf(step));
        String url = buildApiUrl(endpoint, params);
        return executeGet(url, "range query: " + truncate(promQL, 80));
    }

    @Override
    public List<String> queryLabelValues(String metricName, String labelKey) {
        ensureInitialized();
        String endpoint = String.format("/api/v1/label/%s/values", URLEncoder.encode(labelKey, StandardCharsets.UTF_8));
        String url = datasourceUrl + endpoint;
        Object resp = executeRawGet(url, "label-values: " + labelKey);
        return parseStringList(resp, "values");
    }

    @Override
    public List<String> listMetricFamilies() {
        // Use cache for efficiency
        long now = System.currentTimeMillis();
        if (cachedLabelNames != null && (now - cacheExpireMs) < 300_000) { // 5 min cache
            return cachedLabelNames;
        }
        ensureInitialized();
        // Fetch all label names and filter for rocketmq_* families
        String endpoint = "/api/v1/labels";
        String url = datasourceUrl + endpoint;
        Object resp = executeRawGet(url, "list-labels");
        
        List<Map<String, Object>> labels;
        try {
            Map<String, Object> respMap = objectMapper.convertValue(resp, new TypeReference<Map<String, Object>>() {});
            Object data = respMap.get("data");
            if (data instanceof List) {
                labels = objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
            } else {
                labels = Collections.emptyList();
            }
        } catch (IllegalArgumentException e) {
            labels = Collections.emptyList();
        }
        
        List<String> familyNames = labels.stream()
                .map(l -> (String) l.get("name"))
                .filter(Objects::nonNull)
                .filter(n -> n.startsWith(METRIC_FAMILY_PREFIX))
                .distinct()
                .collect(Collectors.toList());
        
        cachedLabelNames = familyNames;
        cacheExpireMs = now;
        return familyNames;
    }

    @Override
    public MetricsHealthResult healthCheck() {
        MetricsHealthResult result = new MetricsHealthResult();
        result.setConnected(false);
        long startTs = System.currentTimeMillis();

        if (datasourceUrl == null) {
            result.setStatusMessage("Data source URL not configured.");
            return result;
        }

        try {
            // Step 1: connectivity test via instant query
            String endpoint = "/api/v1/query";
            Map<String, String> params = new LinkedHashMap<>();
            params.put("query", "up{job=\"rocketmq\"}");
            String url = buildApiUrl(endpoint, params);
            
            Object resp = executeRawGet(url, "health-check-connectivity");
            
            Map<String, Object> respMap = objectMapper.convertValue(resp, new TypeReference<Map<String, Object>>() {});
            String status = (String) respMap.get("status");
            
            if (!"success".equals(status)) {
                result.setStatusMessage("Data source returned error: " + respMap.getOrDefault("errorText", "unknown"));
                return result;
            }

            long latency = System.currentTimeMillis() - startTs;
            result.setQueryLatencyMs(latency);
            result.setConnected(true);

            // Step 2: discover available metric families
            List<String> allLabels = listMetricFamilies();
            Set<String> discoveredSet = new HashSet<>(allLabels);
            
            // Also scan targets for additional info
            try {
                String targetEndpoint = "/api/v1/targets";
                String targetUrl = datasourceUrl + targetEndpoint;
                Object targetResp = executeRawGet(targetUrl, "health-check-targets");
                Map<String, Object> targetMap = objectMapper.convertValue(targetResp, new TypeReference<Map<String, Object>>() {});
                Object activeTargets = ((Map<String, Object>) targetMap.getOrDefault("data", Collections.emptyMap())).get("activeTargets");
                if (activeTargets instanceof List) {
                    List<?> targets = (List<?>) activeTargets;
                    List<String> targetNames = targets.stream()
                            .map(t -> {
                                Map<String, Object> tMap = objectMapper.convertValue(t, new TypeReference<Map<String, Object>>() {});
                                return (String) tMap.getOrDefault("__name__", "");
                            })
                            .filter(n -> !n.isEmpty() && n.startsWith(METRIC_FAMILY_PREFIX))
                            .distinct()
                            .collect(Collectors.toList());
                    targetNames.forEach(discoveredSet::add);
                }
            } catch (Exception e) {
                log.debug("Could not probe targets during health check: {}", e.getMessage());
            }
            
            result.setAvailableMetricFamilies(new ArrayList<>(discoveredSet));

            // Step 3: check required metric families
            List<String> missing = REQUIRED_METRIC_FAMILIES.stream()
                    .filter(prefix -> discoveredSet.stream().noneMatch(m -> m.startsWith(prefix)))
                    .collect(Collectors.toList());
            result.setMissingMetricFamilies(missing);

            if (!missing.isEmpty()) {
                result.setStatusMessage(
                        String.format("Connected OK (latency %d ms), but %d required metric family groups are missing: %s",
                                latency, missing.size(), missing));
            } else {
                result.setStatusMessage(String.format("Healthy — connection established in %d ms, all %d metric family groups detected.",
                        latency, REQUIRED_METRIC_FAMILIES.size()));
            }

        } catch (ServiceException se) {
            long latency = System.currentTimeMillis() - startTs;
            result.setQueryLatencyMs(latency);
            result.setConnected(false);
            result.setStatusMessage(String.format("Connection failed: %s", se.getMessage()));
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTs;
            result.setQueryLatencyMs(latency);
            result.setConnected(false);
            result.setStatusMessage(String.format("Health check error: %s", e.getMessage()));
            log.error("MetricsProvider healthCheck failed", e);
        }

        return result;
    }

    // ======================== Internal Helpers ========================

    private void ensureInitialized() {
        if (datasourceUrl == null) {
            refresh();
        }
        if (datasourceUrl == null) {
            throw new ServiceException(500, "Metrics data source URL is not configured. "
                    + "Set 'rocketmq.config.metrics.datasource.url' or env 'ROCKETMQ_METRICS_DATASOURCE_URL'.");
        }
    }

    private String buildApiUrl(String endpoint, Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder(datasourceUrl);
        sb.append(endpoint);
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            sb.append(first ? '?' : '&');
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    /** Execute a GET request, auto-applying auth headers and parsing JSON. */
    private Object executeGet(String url, String description) {
        Object raw = executeRawGet(url, description);
        try {
            return objectMapper.convertValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    private Object executeRawGet(String url, String description) {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_S))
                .GET();

        // Apply authentication headers
        applyAuthHeaders(reqBuilder);

        HttpRequest request = reqBuilder.build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode != 200) {
                String body = response.body();
                String msg = String.format("Prometheus API returned HTTP %d for %s. Body: %s",
                        statusCode, description, truncate(body, 500));
                throw new ServiceException(statusCode, msg);
            }

            return objectMapper.readValue(response.body(), Object.class);

        } catch (ServiceException e) {
            throw e; // re-throw our own exceptions directly
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(502, "Failed to reach data source for " + description + ": " + e.getMessage());
        }
    }

    private void applyAuthHeaders(HttpRequest.Builder builder) {
        if ("basic".equalsIgnoreCase(authType) && username != null && password != null) {
            String cred = username + ":" + password;
            String encoded = java.util.Base64.getEncoder()
                    .encodeToString(cred.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        } else if ("bearer".equalsIgnoreCase(authType) && bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        // none = no auth header needed
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(Object resp, String field) {
        if (!(resp instanceof Map)) {
            return Collections.emptyList();
        }
        Map<String, Object> map = (Map<String, Object>) resp;
        Object data = map.get(field);
        if (data instanceof List) {
            return ((List<?>) data).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
