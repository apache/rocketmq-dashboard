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
package com.rocketmq.studio.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SettingsService {

    private static final Set<String> PROMETHEUS_COMPATIBLE_TYPES = Set.of(
            "prometheus", "victoriametrics", "thanos", "mimir");
    private static final String PROMETHEUS_TEST_QUERY = "up";
    private static final Duration DATA_SOURCE_TEST_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DATA_SOURCE_TEST_READ_TIMEOUT = Duration.ofSeconds(5);

    private final SettingsRepository settingsRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public SettingsService(SettingsRepository settingsRepository, RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(DATA_SOURCE_TEST_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(DATA_SOURCE_TEST_READ_TIMEOUT);
        this.settingsRepository = settingsRepository;
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
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
    }


    public List<DataSourceVO> listDataSources() {
        log.debug("Listing all data sources");
        return settingsRepository.findAllDataSources();
    }


    public DataSourceVO createDataSource(DataSourceVO dataSource) {
        log.info("Creating data source: {}", dataSource.getName());
        dataSource.setKey(UUID.randomUUID().toString());
        return settingsRepository.saveDataSource(dataSource);
    }


    public DataSourceVO updateDataSource(DataSourceVO dataSource) {
        log.info("Updating data source: {}", dataSource.getKey());
        return settingsRepository.saveDataSource(dataSource);
    }


    public void deleteDataSource(String key) {
        log.info("Deleting data source: {}", key);
        settingsRepository.deleteDataSource(key);
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
                && PROMETHEUS_COMPATIBLE_TYPES.contains(type.replaceAll("\\s+", "").toLowerCase());
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
        return UriComponentsBuilder.fromUriString(normalized + "/api/v1/query")
                .queryParam("query", PROMETHEUS_TEST_QUERY)
                .build()
                .toUri();
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
