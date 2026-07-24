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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SettingsRepository settingsRepository;

    private SettingsService settingsService;

    private HttpServer prometheusServer;
    private String prometheusBaseUrl;

    @BeforeEach
    void setUp() throws IOException {
        settingsService = new SettingsService(settingsRepository, RestClient.builder(), new ObjectMapper());
        prometheusServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        prometheusBaseUrl = "http://127.0.0.1:" + prometheusServer.getAddress().getPort();
        prometheusServer.start();
    }

    @AfterEach
    void tearDown() {
        prometheusServer.stop(0);
    }

    @Test
    void getGeneralSettingsShouldReturnCurrentSettings() {
        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(30)
                .requireLogin(true)
                .llmProvider("openai")
                .model("gpt-4")
                .build();
        when(settingsRepository.loadGeneralSettings()).thenReturn(settings);

        GeneralSettingsVO result = settingsService.getGeneralSettings();

        assertThat(result.getTheme()).isEqualTo("dark");
        assertThat(result.isCompact()).isTrue();
        assertThat(result.isDesktopNotify()).isTrue();
        assertThat(result.isNotifySound()).isFalse();
        assertThat(result.getSessionTimeout()).isEqualTo(30);
        assertThat(result.isRequireLogin()).isTrue();
        assertThat(result.getLlmProvider()).isEqualTo("openai");
        assertThat(result.getModel()).isEqualTo("gpt-4");
    }

    @Test
    void saveGeneralSettingsShouldPreserveExistingApiKeyWhenOmitted() {
        GeneralSettingsVO existing = GeneralSettingsVO.builder()
                .apiKey("sk-existing")
                .build();
        GeneralSettingsVO update = GeneralSettingsVO.builder()
                .theme("light")
                .compact(false)
                .sessionTimeout(60)
                .build();
        when(settingsRepository.loadGeneralSettings()).thenReturn(existing);

        settingsService.saveGeneralSettings(update);

        assertThat(update.getApiKey()).isEqualTo("sk-existing");
        verify(settingsRepository).saveGeneralSettings(update);
    }

    @Test
    void saveGeneralSettingsShouldReplaceExistingApiKey() {
        GeneralSettingsVO existing = GeneralSettingsVO.builder()
                .apiKey("sk-existing")
                .build();
        GeneralSettingsVO update = GeneralSettingsVO.builder()
                .apiKey("sk-new")
                .build();
        when(settingsRepository.loadGeneralSettings()).thenReturn(existing);

        settingsService.saveGeneralSettings(update);

        assertThat(update.getApiKey()).isEqualTo("sk-new");
        verify(settingsRepository).saveGeneralSettings(update);
    }

    @Test
    void saveGeneralSettingsShouldClearApiKeyOnlyWhenExplicitlyRequested() {
        GeneralSettingsVO existing = GeneralSettingsVO.builder()
                .apiKey("sk-existing")
                .build();
        GeneralSettingsVO update = GeneralSettingsVO.builder()
                .clearApiKey(true)
                .build();
        when(settingsRepository.loadGeneralSettings()).thenReturn(existing);

        settingsService.saveGeneralSettings(update);

        assertThat(update.getApiKey()).isEmpty();
        assertThat(update.isClearApiKey()).isFalse();
        verify(settingsRepository).saveGeneralSettings(update);
    }

    @Test
    void saveGeneralSettingsShouldLetClearTakePrecedenceOverReplacementApiKey() {
        GeneralSettingsVO update = GeneralSettingsVO.builder()
                .apiKey("sk-new")
                .clearApiKey(true)
                .build();

        settingsService.saveGeneralSettings(update);

        assertThat(update.getApiKey()).isEmpty();
        assertThat(update.isClearApiKey()).isFalse();
        verify(settingsRepository).saveGeneralSettings(update);
    }

    @Test
    void listDataSourcesShouldReturnAllSources() {
        DataSourceVO ds1 = DataSourceVO.builder().key("ds-1").name("Production").type("rocketmq")
                .url("localhost:9876").status("connected").build();
        DataSourceVO ds2 = DataSourceVO.builder().key("ds-2").name("Staging").type("rocketmq")
                .url("staging:9876").status("disconnected").build();
        when(settingsRepository.findAllDataSources()).thenReturn(Arrays.asList(ds1, ds2));

        List<DataSourceVO> result = settingsService.listDataSources();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Production");
        assertThat(result.get(0).getStatus()).isEqualTo("connected");
        assertThat(result.get(1).getName()).isEqualTo("Staging");
        assertThat(result.get(1).getStatus()).isEqualTo("disconnected");
    }

    @Test
    void listDataSourcesShouldReturnEmptyListWhenNoSources() {
        when(settingsRepository.findAllDataSources()).thenReturn(Collections.emptyList());

        List<DataSourceVO> result = settingsService.listDataSources();

        assertThat(result).isEmpty();
    }

    @Test
    void createDataSourceShouldAssignKeyBeforeSaving() {
        DataSourceVO input = DataSourceVO.builder().name("New DS").type("rocketmq")
                .url("new-host:9876").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DataSourceVO result = settingsService.createDataSource(input);

        assertThat(result.getKey()).isNotBlank();
        assertThat(result.getName()).isEqualTo("New DS");
        verify(settingsRepository).saveDataSource(input);
    }

    @Test
    void createDataSourceShouldReplaceClientProvidedKey() {
        DataSourceVO input = DataSourceVO.builder().key("existing-key").name("New DS").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DataSourceVO result = settingsService.createDataSource(input);

        assertThat(result.getKey()).isNotBlank().isNotEqualTo("existing-key");
        verify(settingsRepository).saveDataSource(input);
    }

    @Test
    void updateDataSourceShouldDelegateToRepository() {
        DataSourceVO input = DataSourceVO.builder().key("ds-1").name("Updated DS").type("rocketmq")
                .url("updated-host:9876").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class))).thenReturn(input);

        DataSourceVO result = settingsService.updateDataSource(input);

        assertThat(result.getKey()).isEqualTo("ds-1");
        assertThat(result.getName()).isEqualTo("Updated DS");
        verify(settingsRepository).saveDataSource(input);
    }

    @Test
    void deleteDataSourceShouldDelegateToRepository() {
        doNothing().when(settingsRepository).deleteDataSource("ds-1");

        settingsService.deleteDataSource("ds-1");

        verify(settingsRepository).deleteDataSource("ds-1");
    }

    @Test
    void testConnectionShouldQueryPrometheusEndpoint() {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> requestQuery = new AtomicReference<>();
        prometheusServer.createContext("/api/v1/query", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}");
        });
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(prometheusBaseUrl)
                .type("Prometheus")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Connection successful");
        assertThat(requestPath.get()).isEqualTo("/api/v1/query");
        assertThat(requestQuery.get()).isEqualTo("query=up");
    }

    @Test
    void testConnectionShouldReturnPrometheusErrorDetails() {
        prometheusServer.createContext("/api/v1/query", exchange -> respond(exchange, 422,
                "{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"invalid query\"}"));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(prometheusBaseUrl)
                .type("VictoriaMetrics")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(
                "Prometheus query failed (bad_data): invalid query");
    }

    @Test
    void testConnectionShouldRejectInvalidUrl() {
        DataSourceTestResultVO result = settingsService.testDataSource(DataSourceTestDTO.builder()
                .url("ftp://example.com")
                .type("Prometheus")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(
                "Data source URL must start with http:// or https://");
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
