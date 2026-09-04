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
package org.apache.rocketmq.studio.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.MetricsBackendType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.util.UrlHostGuard;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class SettingsService {

    private static final String REDACTED_NOTIFICATION_WEBHOOK = "******";
    private static final List<byte[]> CLOUD_METADATA_ADDRESSES = List.of(
            new byte[] {
                (byte) 0xfd, 0x00, 0x0e, (byte) 0xc2,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x02, 0x54
            }, // AWS IMDS IPv6: fd00:ec2::254
            new byte[] {100, 100, 100, (byte) 200}); // Alibaba Cloud ECS metadata
    private static final Set<String> PROMETHEUS_COMPATIBLE_TYPES = Set.of(
            "prometheus", "victoriametrics", "thanos", "mimir", "cortex", "arms");
    private static final String PROMETHEUS_TEST_QUERY = "up";
    private static final String AUTH_NONE = "none";
    private static final String AUTH_BASIC = "basic auth";
    private static final String AUTH_BEARER = "bearer token";
    private static final Duration DATA_SOURCE_TEST_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DATA_SOURCE_TEST_READ_TIMEOUT = Duration.ofSeconds(5);

    private final SettingsRepository settingsRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OperationAuditService operationAuditService;

    @Autowired
    public SettingsService(SettingsRepository settingsRepository, RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper, OperationAuditService operationAuditService) {
        this(settingsRepository, buildDataSourceRestClient(restClientBuilder), objectMapper, operationAuditService);
    }

    SettingsService(SettingsRepository settingsRepository, RestClient restClient,
                    ObjectMapper objectMapper, OperationAuditService operationAuditService) {
        this.settingsRepository = settingsRepository;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.operationAuditService = operationAuditService;
    }

    private static RestClient buildDataSourceRestClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new DataSourceClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DATA_SOURCE_TEST_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(DATA_SOURCE_TEST_READ_TIMEOUT);
        return restClientBuilder.requestFactory(requestFactory).build();
    }


    public GeneralSettingsVO getGeneralSettings() {
        log.debug("Loading general settings");
        GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
        if (AuthenticatedUserContext.currentUserIsAdminOrSystem()) {
            return settings;
        }
        return redactNotificationWebhooks(settings);
    }

    private GeneralSettingsVO redactNotificationWebhooks(GeneralSettingsVO settings) {
        if (settings == null) {
            return null;
        }
        return settings.toBuilder()
                .dingtalkWebhook(StringUtils.hasText(settings.getDingtalkWebhook())
                        ? REDACTED_NOTIFICATION_WEBHOOK
                        : settings.getDingtalkWebhook())
                .smsWebhook(StringUtils.hasText(settings.getSmsWebhook())
                        ? REDACTED_NOTIFICATION_WEBHOOK
                        : settings.getSmsWebhook())
                .build();
    }


    public synchronized void saveGeneralSettings(GeneralSettingsVO settings) {
        log.info("Saving general settings");
        validateLlmBaseUrl(settings.getBaseUrl());
        GeneralSettingsVO currentSettings = settingsRepository.loadGeneralSettings();
        if (settings.isClearApiKey()) {
            settings.setApiKey("");
        } else if (!StringUtils.hasText(settings.getApiKey()) && currentSettings != null) {
            settings.setApiKey(currentSettings.getApiKey());
        }
        if (settings.isClearDingtalkSigningSecret()) {
            settings.setDingtalkSigningSecret("");
        } else if (!StringUtils.hasText(settings.getDingtalkSigningSecret()) && currentSettings != null) {
            settings.setDingtalkSigningSecret(currentSettings.getDingtalkSigningSecret());
        }
        if (currentSettings != null) {
            if (!StringUtils.hasText(settings.getLlmEngine())) {
                settings.setLlmEngine(currentSettings.getLlmEngine());
            }
            if (!StringUtils.hasText(settings.getDeploymentName())) {
                settings.setDeploymentName(currentSettings.getDeploymentName());
            }
            if (!StringUtils.hasText(settings.getApiVersion())) {
                settings.setApiVersion(currentSettings.getApiVersion());
            }
            if (!StringUtils.hasText(settings.getAwsRegion())) {
                settings.setAwsRegion(currentSettings.getAwsRegion());
            }
            if (settings.getMaxTokens() == null) {
                settings.setMaxTokens(currentSettings.getMaxTokens());
            }
            if (settings.getTemperature() == null) {
                settings.setTemperature(currentSettings.getTemperature());
            }
        }
        settings.setClearApiKey(false);
        settings.setClearDingtalkSigningSecret(false);
        settingsRepository.saveGeneralSettings(settings);
        recordSettingsAudit();
    }

    private void validateLlmBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return;
        }
        try {
            UrlHostGuard.check(baseUrl, true);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, "Invalid LLM base URL: " + exception.getMessage());
        }
    }

    private void recordSettingsAudit() {
        try {
            operationAuditService.record("UPDATE_SETTINGS", "SETTINGS", "general",
                    null, "General settings updated", "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record general settings audit: {}", auditFailure.getMessage());
        }
    }

    private void recordDataSourceAudit(String operation, DataSourceVO dataSource) {
        if (dataSource == null) {
            return;
        }
        String detail = String.format("name=%s, type=%s, instanceCount=%d", dataSource.getName(),
                dataSource.getType(), dataSource.getInstanceIds() == null ? 0 : dataSource.getInstanceIds().size());
        recordDataSourceAudit(operation, dataSource.getKey(), detail);
    }

    private void recordDataSourceDeleteAudit(String key) {
        recordDataSourceAudit("DELETE_DATA_SOURCE", key, "key=" + key);
    }

    private void recordDataSourceAudit(String operation, String key, String detail) {
        try {
            operationAuditService.record(operation, "METRICS_DATA_SOURCE", key, null, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record data source audit operation={} key={}: {}", operation, key,
                    auditFailure.getMessage());
        }
    }


    // The full-list endpoint is hit by every metrics tab on first paint and
    // every time the datasource dropdown re-fetches. Caching it with the
    // write paths evicted below keeps the user-visible list correct while
    // removing a per-tab DB round trip.
    @Cacheable("data-sources")
    public List<DataSourceVO> listDataSources() {
        log.debug("Listing all data sources");
        return settingsRepository.findAllDataSources();
    }

    @Cacheable(value = "data-sources", key = "'page:' + #search + ':' + #type + ':' + #page + ':' + #pageSize")
    public PageResult<DataSourceVO> listDataSources(String search, String type, int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
        log.debug("Listing data sources, search={}, type={}, page={}, pageSize={}",
                search, type, page, pageSize);
        return settingsRepository.findDataSources(search, type, page, pageSize);
    }


    @CacheEvict(value = "data-sources", allEntries = true)
    public DataSourceVO createDataSource(DataSourceVO dataSource) {
        if (dataSource == null) {
            throw new BusinessException(400, "Data source request is required");
        }
        log.info("Creating data source: {}", dataSource.getName());
        validateDataSourceUrl(dataSource.getUrl());
        DataSourceVO saved = settingsRepository.saveDataSource(dataSource);
        recordDataSourceAudit("CREATE_DATA_SOURCE", saved);
        return saved;
    }


    @CacheEvict(value = "data-sources", allEntries = true)
    public DataSourceVO updateDataSource(DataSourceVO dataSource) {
        if (dataSource == null) {
            throw new BusinessException(400, "Data source request is required");
        }
        String key = normalizeDataSourceKey(dataSource.getKey());
        dataSource.setKey(key);
        log.info("Updating data source: {}", key);
        validateDataSourceUrl(dataSource.getUrl());
        if (!settingsRepository.replaceDataSource(dataSource)) {
            throw new BusinessException(404, "Data source not found: " + key);
        }
        recordDataSourceAudit("UPDATE_DATA_SOURCE", dataSource);
        return dataSource;
    }

    private void validateDataSourceUrl(String url) {
        try {
            UrlHostGuard.check(url, false);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400, exception.getMessage());
        }
    }


    @CacheEvict(value = "data-sources", allEntries = true)
    public void deleteDataSource(String key) {
        String normalizedKey = normalizeDataSourceKey(key);
        log.info("Deleting data source: {}", normalizedKey);
        if (!settingsRepository.deleteDataSource(normalizedKey)) {
            throw new BusinessException(404, "Data source not found: " + normalizedKey);
        }
        recordDataSourceDeleteAudit(normalizedKey);
    }


    public DataSourceVO getDataSource(String key) {
        String normalizedKey = normalizeDataSourceKey(key);
        log.debug("Loading data source: {}", normalizedKey);
        return settingsRepository.findDataSourceByKey(normalizedKey)
                .orElseThrow(() -> new BusinessException(404, "Data source not found: " + normalizedKey));
    }


    public DataSourceTestResultVO testDataSource(DataSourceTestDTO request) {
        log.info("Testing data source connection: type={}", request == null ? null : request.getType());
        if (request == null) {
            return failed("Data source test request is required");
        }
        if (!isPrometheusCompatible(request.getType())) {
            return failed("Unsupported data source type: " + request.getType());
        }

        try {
            JsonNode response = restClient.get()
                    .uri(prometheusQueryUri(request.getUrl(), request.getType()))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> applyAuthentication(headers, request))
                    .retrieve()
                    .body(JsonNode.class);
            return prometheusSuccess(response);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return failed(exception.getMessage());
        } catch (RestClientResponseException exception) {
            return failed(prometheusErrorMessage(exception));
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                return failed("Prometheus connection timed out");
            }
            return failed("Failed to connect to Prometheus");
        } catch (RestClientException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                return failed("Prometheus connection timed out");
            }
            return failed("Prometheus connection failed");
        }
    }

    private boolean isPrometheusCompatible(String type) {
        return StringUtils.hasText(type)
                && PROMETHEUS_COMPATIBLE_TYPES.contains(
                        type.replaceAll("\\s+", "").toLowerCase(Locale.ROOT));
    }

    private String normalizeDataSourceKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(400, "Data source key is required");
        }
        return key.trim();
    }

    private void applyAuthentication(HttpHeaders headers, DataSourceTestDTO request) {
        String auth = normalizeAuth(request.getAuth());
        if (AUTH_NONE.equals(auth)) {
            return;
        }
        if (AUTH_BASIC.equals(auth)) {
            if (!StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
                throw new IllegalArgumentException("Basic authentication requires username and password");
            }
            headers.setBasicAuth(request.getUsername().trim(), request.getPassword());
            return;
        }
        if (AUTH_BEARER.equals(auth)) {
            if (!StringUtils.hasText(request.getBearerToken())) {
                throw new IllegalArgumentException("Bearer authentication requires token");
            }
            headers.setBearerAuth(request.getBearerToken().trim());
            return;
        }
        throw new IllegalArgumentException("Unsupported data source authentication: " + request.getAuth());
    }

    private String normalizeAuth(String auth) {
        if (!StringUtils.hasText(auth)) {
            return AUTH_NONE;
        }
        return auth.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private URI prometheusQueryUri(String baseUrl, String providerType) throws URISyntaxException {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Data source URL is required");
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        URI baseUri = new URI(normalized);
        if (!"http".equalsIgnoreCase(baseUri.getScheme()) && !"https".equalsIgnoreCase(baseUri.getScheme())) {
            throw new IllegalArgumentException("Data source URL must start with http:// or https://");
        }
        if (!isAllowedDataSourceHost(baseUri.getHost())) {
            throw new IllegalArgumentException(
                    "Data source URL must not point to a local or private address");
        }
        String queryPath = MetricsBackendType.fromProviderType(providerType).getInstantQueryPath();
        return UriComponentsBuilder.fromUriString(normalized + queryPath)
                .queryParam("query", PROMETHEUS_TEST_QUERY)
                .build()
                .toUri();
    }

    /**
     * SSRF guard: the test endpoint performs a server-side HTTP request to an attacker-supplied
     * URL. The hostname {@code localhost}, loopback IPs (127.x.x.x, ::1), link-local addresses
     * (169.254.x.x, fe80:: — the cloud metadata range), and known metadata endpoints not covered
     * by Java's address categories are never legitimate Prometheus endpoints and are rejected.
     * Private site-local ranges stay allowed because on-premise Prometheus servers live on the
     * internal network and the endpoint itself requires admin rights. Package-private so tests
     * can admit the loopback-bound embedded test server.
     */
    boolean isAllowedDataSourceHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized)) {
            return false;
        }
        try {
            return areAllowedDataSourceAddresses(InetAddress.getAllByName(normalized));
        } catch (UnknownHostException exception) {
            // Fail closed: an unresolvable host must not be handed to the connection
            // layer (this used to return true, creating a blind reachability oracle that
            // differed from UrlHostGuard.check, which also fails closed).
            return false;
        }
    }

    boolean areAllowedDataSourceAddresses(InetAddress[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (InetAddress address : addresses) {
            if (address == null
                    || address.isAnyLocalAddress()
                    || address.isLinkLocalAddress()
                    || address.isLoopbackAddress()
                    || isKnownCloudMetadataAddress(address)) {
                return false;
            }
        }
        return true;
    }

    private boolean isKnownCloudMetadataAddress(InetAddress address) {
        return CLOUD_METADATA_ADDRESSES.stream()
                .anyMatch(metadataAddress -> Arrays.equals(address.getAddress(), metadataAddress));
    }

    private DataSourceTestResultVO prometheusSuccess(JsonNode response) {
        if (response != null && "success".equals(response.path("status").asText())) {
            return DataSourceTestResultVO.builder()
                    .success(true)
                    .message("Connection successful")
                    .build();
        }
        return failed(prometheusBodyError(response));
    }

    private String prometheusErrorMessage(RestClientResponseException exception) {
        try {
            return prometheusBodyError(objectMapper.readTree(exception.getResponseBodyAsString()));
        } catch (IOException ignored) {
            return "Prometheus query failed";
        }
    }

    private String prometheusBodyError(JsonNode response) {
        String errorType = response == null ? "" : response.path("errorType").asText();
        String error = response == null ? "" : response.path("error").asText();
        if (StringUtils.hasText(error)) {
            return StringUtils.hasText(errorType)
                    ? "Prometheus query failed (" + errorType + "): " + error
                    : "Prometheus query failed: " + error;
        }
        return "Prometheus query failed";
    }

    private DataSourceTestResultVO failed(String message) {
        return DataSourceTestResultVO.builder()
                .success(false)
                .message(message)
                .build();
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
