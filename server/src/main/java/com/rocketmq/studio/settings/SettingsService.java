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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SettingsService {

    private static final String PROMETHEUS_QUERY_PATH = "/api/v1/query?query=vector%281%29";
    private static final Set<String> PROMETHEUS_COMPATIBLE_TYPES =
            Set.of("prometheus", "victoriametrics", "thanos");
    private static final Duration CONNECTION_TEST_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CONNECTION_TEST_READ_TIMEOUT = Duration.ofSeconds(5);

    private final SettingsRepository settingsRepository;
    private final RestClient restClient;

    public SettingsService(SettingsRepository settingsRepository, RestClient.Builder restClientBuilder) {
        this.settingsRepository = settingsRepository;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECTION_TEST_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(CONNECTION_TEST_READ_TIMEOUT);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
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
        log.info("Testing data source connection: type={}", request.getType());
        if (!isPrometheusCompatible(request.getType())) {
            return failure("Unsupported data source type");
        }
        if (StringUtils.hasText(request.getAuth()) && !"none".equalsIgnoreCase(request.getAuth().strip())) {
            return failure("Credentials are required to test authenticated data sources");
        }

        URI testUri;
        try {
            testUri = buildPrometheusQueryUri(request.getUrl());
        } catch (IllegalArgumentException exception) {
            return failure("Invalid data source URL");
        }

        try {
            JsonNode response = restClient.get()
                    .uri(testUri)
                    .retrieve()
                    .body(JsonNode.class);
            if (!isSuccessfulPrometheusResponse(response)) {
                return failure("Data source returned an invalid Prometheus response");
            }
            return success();
        } catch (RestClientResponseException exception) {
            log.warn("Data source connection test returned HTTP {}", exception.getStatusCode().value());
            return failure("Connection failed: HTTP " + exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            if (hasCause(exception, SocketTimeoutException.class)) {
                return failure("Connection test timed out");
            }
            return failure("Failed to connect to data source");
        } catch (RestClientException exception) {
            return failure("Data source connection test failed");
        }
    }

    private boolean isPrometheusCompatible(String type) {
        return StringUtils.hasText(type)
                && PROMETHEUS_COMPATIBLE_TYPES.contains(type.strip().toLowerCase(Locale.ROOT));
    }

    private URI buildPrometheusQueryUri(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Data source URL is required");
        }
        String baseUrl = url.strip();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        URI baseUri = URI.create(baseUrl);
        boolean supportedScheme = "http".equalsIgnoreCase(baseUri.getScheme())
                || "https".equalsIgnoreCase(baseUri.getScheme());
        if (!supportedScheme || !StringUtils.hasText(baseUri.getRawAuthority())
                || baseUri.getRawUserInfo() != null || baseUri.getRawQuery() != null
                || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid data source URL");
        }
        return URI.create(baseUrl + PROMETHEUS_QUERY_PATH);
    }

    private boolean isSuccessfulPrometheusResponse(JsonNode response) {
        if (response == null || !"success".equals(response.path("status").asText())) {
            return false;
        }
        JsonNode data = response.path("data");
        return data.isObject()
                && StringUtils.hasText(data.path("resultType").asText())
                && data.path("result").isArray();
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

    private DataSourceTestResultVO success() {
        return DataSourceTestResultVO.builder()
                .success(true)
                .message("Connection successful")
                .build();
    }

    private DataSourceTestResultVO failure(String message) {
        return DataSourceTestResultVO.builder()
                .success(false)
                .message(message)
                .build();
    }
}
