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
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.UrlHostGuard;
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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SettingsService {

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
    private static final Set<String> SSL_PROTOCOLS = Set.of("TLSv1.2", "TLSv1.3");
    private static final Set<String> SSL_CLIENT_AUTH = Set.of("none", "want", "need");
    private static final Set<String> SSL_STORE_TYPES = Set.of("JKS", "PKCS12");

    private final SettingsRepository settingsRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final OperationAuditService operationAuditService;

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
        return settingsRepository.loadGeneralSettings();
    }


    public synchronized void saveGeneralSettings(GeneralSettingsVO settings) {
        log.info("Saving general settings");
        GeneralSettingsVO currentSettings = settingsRepository.loadGeneralSettings();
        if (settings.isClearApiKey()) {
            settings.setApiKey("");
        } else if (!StringUtils.hasText(settings.getApiKey()) && currentSettings != null) {
            settings.setApiKey(currentSettings.getApiKey());
        }
        settings.setClearApiKey(false);
        settingsRepository.saveGeneralSettings(settings);
        recordSettingsAudit();
    }

    private void recordSettingsAudit() {
        try {
            operationAuditService.record("UPDATE_SETTINGS", "SETTINGS", "general",
                    null, "General settings updated", "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record general settings audit: {}", auditFailure.getMessage());
        }
    }

    public SslSettingsVO getSslSettings() {
        log.debug("Loading SSL settings");
        return toSslSettingsVO(settingsRepository.loadSslSettings());
    }

    public synchronized SslSettingsVO saveSslSettings(SslSettingsUpdateDTO request) {
        if (request == null) {
            throw new BusinessException(400, "SSL settings request is required");
        }
        SslSettingsRecord updated = mergeSslSettings(settingsRepository.loadSslSettings(), request);
        validateSslSettingsRecord(updated);
        settingsRepository.saveSslSettings(updated);
        recordSslSettingsAudit(updated);
        return toSslSettingsVO(updated);
    }

    public SslSettingsValidationResultVO validateSslSettings(SslSettingsUpdateDTO request) {
        if (request == null) {
            throw new BusinessException(400, "SSL settings request is required");
        }
        SslSettingsRecord settings = mergeSslSettings(settingsRepository.loadSslSettings(), request);
        validateSslSettingsRecord(settings);
        if (!settings.isEnabled()) {
            return SslSettingsValidationResultVO.builder()
                    .success(true)
                    .message("SSL/TLS is disabled")
                    .warnings(List.of())
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        try {
            loadKeyStore("KeyStore", settings.getKeyStoreType(), settings.getKeyStorePath(),
                    settings.getKeyStorePassword());
            if (!AUTH_NONE.equals(settings.getClientAuth())) {
                loadKeyStore("TrustStore", settings.getTrustStoreType(), settings.getTrustStorePath(),
                        settings.getTrustStorePassword());
            } else if (StringUtils.hasText(settings.getTrustStorePath())) {
                warnings.add("TrustStore is configured but client authentication is disabled");
            }
        } catch (IllegalArgumentException exception) {
            return SslSettingsValidationResultVO.builder()
                    .success(false)
                    .message(exception.getMessage())
                    .warnings(warnings)
                    .build();
        }

        return SslSettingsValidationResultVO.builder()
                .success(true)
                .message("SSL/TLS keystore settings are valid")
                .warnings(warnings)
                .build();
    }

    private void recordSslSettingsAudit(SslSettingsRecord settings) {
        try {
            operationAuditService.record("UPDATE_SSL_SETTINGS", "SETTINGS", "ssl",
                    null, "enabled=" + settings.isEnabled() + ", protocol=" + settings.getProtocol()
                            + ", clientAuth=" + settings.getClientAuth(), "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record SSL settings audit: {}", auditFailure.getMessage());
        }
    }

    private SslSettingsVO toSslSettingsVO(SslSettingsRecord settings) {
        SslSettingsRecord normalized = settings == null ? SslSettingsRecord.defaults() : settings;
        return SslSettingsVO.builder()
                .enabled(normalized.isEnabled())
                .protocol(defaultIfBlank(normalized.getProtocol(), "TLSv1.3"))
                .clientAuth(defaultIfBlank(normalized.getClientAuth(), AUTH_NONE))
                .keyStoreType(defaultIfBlank(normalized.getKeyStoreType(), "PKCS12"))
                .keyStorePath(defaultIfBlank(normalized.getKeyStorePath(), ""))
                .keyStorePasswordConfigured(StringUtils.hasText(normalized.getKeyStorePassword()))
                .trustStoreType(defaultIfBlank(normalized.getTrustStoreType(), "PKCS12"))
                .trustStorePath(defaultIfBlank(normalized.getTrustStorePath(), ""))
                .trustStorePasswordConfigured(StringUtils.hasText(normalized.getTrustStorePassword()))
                .restartRequired(normalized.isEnabled())
                .build();
    }

    private SslSettingsRecord mergeSslSettings(SslSettingsRecord current, SslSettingsUpdateDTO request) {
        SslSettingsRecord existing = current == null ? SslSettingsRecord.defaults() : current;
        String keyStorePassword = existing.getKeyStorePassword();
        if (request.isClearKeyStorePassword()) {
            keyStorePassword = "";
        } else if (StringUtils.hasText(request.getKeyStorePassword())) {
            keyStorePassword = request.getKeyStorePassword();
        }

        String trustStorePassword = existing.getTrustStorePassword();
        if (request.isClearTrustStorePassword()) {
            trustStorePassword = "";
        } else if (StringUtils.hasText(request.getTrustStorePassword())) {
            trustStorePassword = request.getTrustStorePassword();
        }

        return SslSettingsRecord.builder()
                .enabled(request.getEnabled() == null ? existing.isEnabled() : request.getEnabled())
                .protocol(normalizeSslProtocol(defaultIfBlank(request.getProtocol(), existing.getProtocol())))
                .clientAuth(normalizeSslClientAuth(defaultIfBlank(request.getClientAuth(), existing.getClientAuth())))
                .keyStoreType(normalizeSslStoreType(defaultIfBlank(request.getKeyStoreType(),
                        existing.getKeyStoreType())))
                .keyStorePath(defaultIfBlank(request.getKeyStorePath(), existing.getKeyStorePath()).trim())
                .keyStorePassword(defaultIfBlank(keyStorePassword, ""))
                .trustStoreType(normalizeSslStoreType(defaultIfBlank(request.getTrustStoreType(),
                        existing.getTrustStoreType())))
                .trustStorePath(defaultIfBlank(request.getTrustStorePath(), existing.getTrustStorePath()).trim())
                .trustStorePassword(defaultIfBlank(trustStorePassword, ""))
                .build();
    }

    private void validateSslSettingsRecord(SslSettingsRecord settings) {
        if (!settings.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(settings.getKeyStorePath())) {
            throw new BusinessException(400, "KeyStore path is required when SSL/TLS is enabled");
        }
        if (!AUTH_NONE.equals(settings.getClientAuth()) && !StringUtils.hasText(settings.getTrustStorePath())) {
            throw new BusinessException(400,
                    "TrustStore path is required when SSL/TLS client authentication is enabled");
        }
    }

    private void loadKeyStore(String label, String type, String rawPath, String password) {
        if (!StringUtils.hasText(rawPath)) {
            throw new IllegalArgumentException(label + " path is required");
        }
        Path path = Path.of(rawPath.trim()).normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label + " file does not exist: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(label + " file is not readable: " + path);
        }
        try (InputStream input = Files.newInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(input, StringUtils.hasText(password) ? password.toCharArray() : null);
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalArgumentException(label + " cannot be loaded: " + exception.getMessage(), exception);
        }
    }

    private String normalizeSslProtocol(String protocol) {
        String normalized = defaultIfBlank(protocol, "TLSv1.3").trim();
        if (!SSL_PROTOCOLS.contains(normalized)) {
            throw new BusinessException(400, "SSL protocol must be one of TLSv1.2, TLSv1.3");
        }
        return normalized;
    }

    private String normalizeSslClientAuth(String clientAuth) {
        String normalized = defaultIfBlank(clientAuth, AUTH_NONE).trim().toLowerCase(Locale.ROOT);
        if (!SSL_CLIENT_AUTH.contains(normalized)) {
            throw new BusinessException(400, "SSL client authentication must be one of none, want, need");
        }
        return normalized;
    }

    private String normalizeSslStoreType(String storeType) {
        String normalized = defaultIfBlank(storeType, "PKCS12").trim().toUpperCase(Locale.ROOT);
        if (!SSL_STORE_TYPES.contains(normalized)) {
            throw new BusinessException(400, "SSL store type must be one of JKS, PKCS12");
        }
        return normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value;
        }
        return fallback == null ? "" : fallback;
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


    public List<DataSourceVO> listDataSources() {
        log.debug("Listing all data sources");
        return settingsRepository.findAllDataSources();
    }


    public DataSourceVO createDataSource(DataSourceVO dataSource) {
        if (dataSource == null) {
            throw new BusinessException(400, "Data source request is required");
        }
        log.info("Creating data source: {}", dataSource.getName());
        dataSource.setKey(UUID.randomUUID().toString());
        validateDataSourceUrl(dataSource.getUrl());
        DataSourceVO saved = settingsRepository.saveDataSource(dataSource);
        recordDataSourceAudit("CREATE_DATA_SOURCE", saved);
        return saved;
    }


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
                    .uri(prometheusQueryUri(request.getUrl()))
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

    private URI prometheusQueryUri(String baseUrl) throws URISyntaxException {
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
        return UriComponentsBuilder.fromUriString(normalized + "/api/v1/query")
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
            // Unresolvable host: let the connection attempt surface the real connectivity error.
            return true;
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
