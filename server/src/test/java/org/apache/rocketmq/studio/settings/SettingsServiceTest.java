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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private static final String PROMETHEUS_BASE_URL = "http://192.0.2.1:9090";
    private static final String PROMETHEUS_QUERY_URL = PROMETHEUS_BASE_URL + "/api/v1/query?query=up";
    private static final String VICTORIA_METRICS_QUERY_URL =
            PROMETHEUS_BASE_URL + "/select/0/prometheus/api/v1/query?query=up";
    private static final String MIMIR_QUERY_URL = PROMETHEUS_BASE_URL + "/prometheus/api/v1/query?query=up";
    private static final String PROMETHEUS_SUCCESS_BODY =
            "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}";

    @Mock
    private SettingsRepository settingsRepository;

    @Mock
    private OperationAuditService operationAuditService;

    private SettingsService settingsService;

    private MockRestServiceServer prometheusServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        prometheusServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        settingsService = new SettingsService(settingsRepository, restClientBuilder.build(),
                new ObjectMapper(), operationAuditService);
    }


    @AfterEach
    void tearDown() {
        prometheusServer.verify();
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
    void saveGeneralSettingsShouldSucceedWhenAuditRecordingFails() {
        GeneralSettingsVO update = GeneralSettingsVO.builder()
                .theme("dark")
                .build();
        doThrow(new IllegalStateException("audit unavailable"))
                .when(operationAuditService)
                .record("UPDATE_SETTINGS", "SETTINGS", "general",
                        null, "General settings updated", "SUCCESS", null);

        assertThatCode(() -> settingsService.saveGeneralSettings(update))
                .doesNotThrowAnyException();

        verify(settingsRepository).saveGeneralSettings(update);
        verify(operationAuditService).record("UPDATE_SETTINGS", "SETTINGS", "general",
                null, "General settings updated", "SUCCESS", null);
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
                .url("http://10.1.2.3").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DataSourceVO result = settingsService.createDataSource(input);

        assertThat(result.getKey()).isNotBlank();
        assertThat(result.getName()).isEqualTo("New DS");
        verify(settingsRepository).saveDataSource(input);
        verify(operationAuditService).record("CREATE_DATA_SOURCE", "METRICS_DATA_SOURCE", result.getKey(),
                null, "name=New DS, type=rocketmq, instanceCount=0", "SUCCESS", null);
    }

    @Test
    void createDataSourceShouldReplaceClientProvidedKey() {
        DataSourceVO input = DataSourceVO.builder().key("existing-key").name("New DS")
                .url("http://10.1.2.3").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DataSourceVO result = settingsService.createDataSource(input);

        assertThat(result.getKey()).isNotBlank().isNotEqualTo("existing-key");
        verify(settingsRepository).saveDataSource(input);
    }

    @Test
    void createDataSourceShouldRejectNullRequest() {
        assertThatThrownBy(() -> settingsService.createDataSource(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source request is required")
                .extracting("code")
                .isEqualTo(400);

        verifyNoInteractions(settingsRepository);
    }

    @Test
    void createDataSourceShouldRejectLoopbackUrl() {
        DataSourceVO input = DataSourceVO.builder().name("Loopback DS").type("rocketmq")
                .url("http://127.0.0.1:9090").build();

        assertThatThrownBy(() -> settingsService.createDataSource(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("local, loopback or metadata address")
                .extracting("code")
                .isEqualTo(400);
        verify(settingsRepository, never()).saveDataSource(any());
    }

    @Test
    void updateDataSourceShouldRejectMetadataUrl() {
        DataSourceVO input = DataSourceVO.builder().key("ds-1").name("Metadata DS").type("rocketmq")
                .url("http://169.254.169.254/latest/meta-data/").build();

        assertThatThrownBy(() -> settingsService.updateDataSource(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("local, loopback or metadata address")
                .extracting("code")
                .isEqualTo(400);
        verify(settingsRepository, never()).replaceDataSource(any());
    }

    @Test
    void updateDataSourceShouldDelegateToRepository() {
        DataSourceVO input = DataSourceVO.builder().key("ds-1").name("Updated DS").type("rocketmq")
                .url("http://10.1.2.3").build();
        when(settingsRepository.replaceDataSource(input)).thenReturn(true);

        DataSourceVO result = settingsService.updateDataSource(input);

        assertThat(result.getKey()).isEqualTo("ds-1");
        assertThat(result.getName()).isEqualTo("Updated DS");
        verify(settingsRepository).replaceDataSource(input);
        verify(operationAuditService).record("UPDATE_DATA_SOURCE", "METRICS_DATA_SOURCE", "ds-1",
                null, "name=Updated DS, type=rocketmq, instanceCount=0", "SUCCESS", null);
    }

    @Test
    void updateDataSourceShouldRejectNullRequest() {
        assertThatThrownBy(() -> settingsService.updateDataSource(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source request is required")
                .extracting("code")
                .isEqualTo(400);

        verifyNoInteractions(settingsRepository);
    }

    @Test
    void updateDataSourceShouldRejectUnknownKey() {
        SettingsService service = new SettingsService(settingsRepository, RestClient.builder(), new ObjectMapper(), operationAuditService);
        DataSourceVO input = DataSourceVO.builder().key("missing").name("Unexpected DS").type("rocketmq")
                .url("http://10.1.2.3").build();

        assertThatThrownBy(() -> service.updateDataSource(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source not found: missing")
                .extracting("code")
                .isEqualTo(404);
        assertThat(service.listDataSources()).isEmpty();
    }

    @Test
    void updateDataSourceShouldRejectBlankKey() {
        SettingsService service = new SettingsService(settingsRepository, RestClient.builder(), new ObjectMapper(),
                operationAuditService);
        DataSourceVO input = DataSourceVO.builder().key(" ").name("Unexpected DS").type("rocketmq")
                .url("http://10.1.2.3").build();

        assertThatThrownBy(() -> service.updateDataSource(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source key is required")
                .extracting("code")
                .isEqualTo(400);
        assertThat(service.listDataSources()).isEmpty();
    }

    @Test
    void deleteDataSourceShouldDelegateToRepository() {
        when(settingsRepository.deleteDataSource("ds-1")).thenReturn(true);

        settingsService.deleteDataSource("ds-1");

        verify(settingsRepository).deleteDataSource("ds-1");
        verify(operationAuditService).record("DELETE_DATA_SOURCE", "METRICS_DATA_SOURCE", "ds-1",
                null, "key=ds-1", "SUCCESS", null);
    }

    @Test
    void deleteDataSourceShouldRejectUnknownKey() {
        SettingsService service = new SettingsService(settingsRepository, RestClient.builder(), new ObjectMapper(),
                operationAuditService);

        assertThatThrownBy(() -> service.deleteDataSource("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source not found: missing")
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void deleteDataSourceShouldRejectBlankKey() {
        assertThatThrownBy(() -> settingsService.deleteDataSource(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Data source key is required")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void testConnectionShouldQueryPrometheusEndpoint() {
        prometheusServer.expect(requestTo(PROMETHEUS_QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(PROMETHEUS_SUCCESS_BODY, MediaType.APPLICATION_JSON));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("Prometheus")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Connection successful");
    }

    @Test
    void testConnectionShouldApplyBasicAuthentication() {
        String expectedAuthorization = "Basic "
                + Base64.getEncoder().encodeToString("prom:secret".getBytes(StandardCharsets.UTF_8));
        prometheusServer.expect(requestTo(PROMETHEUS_QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuthorization))
                .andRespond(withSuccess(PROMETHEUS_SUCCESS_BODY, MediaType.APPLICATION_JSON));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("Prometheus")
                .auth("Basic Auth")
                .username("prom")
                .password("secret")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testConnectionShouldNormalizeIdentifiersIndependentlyOfDefaultLocale() {
        String expectedAuthorization = "Basic "
                + Base64.getEncoder().encodeToString("prom:secret".getBytes(StandardCharsets.UTF_8));
        prometheusServer.expect(requestTo(MIMIR_QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuthorization))
                .andRespond(withSuccess(PROMETHEUS_SUCCESS_BODY, MediaType.APPLICATION_JSON));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("MIMIR")
                .auth("Basic Auth")
                .username("prom")
                .password("secret")
                .build();
        Locale originalLocale = Locale.getDefault();

        DataSourceTestResultVO result;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            result = settingsService.testDataSource(request);
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testConnectionShouldApplyBearerAuthentication() {
        prometheusServer.expect(requestTo(PROMETHEUS_QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess(PROMETHEUS_SUCCESS_BODY, MediaType.APPLICATION_JSON));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("Prometheus")
                .auth("Bearer Token")
                .bearerToken("token-1")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void testConnectionShouldRejectLocalhostHostname() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("http://localhost:9090")
                .type("Prometheus")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("local or private address");
    }

    @Test
    void testConnectionShouldRejectLinkLocalMetadataAddress() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("http://169.254.169.254/latest/meta-data/")
                .type("Prometheus")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("local or private address");
    }

    @Test
    void testConnectionShouldRejectAwsImdsIpv6Address() {
        DataSourceTestDTO compressedRequest = DataSourceTestDTO.builder()
                .url("http://[fd00:ec2::254]/latest/meta-data/")
                .type("Prometheus")
                .build();
        DataSourceTestDTO expandedRequest = DataSourceTestDTO.builder()
                .url("http://[fd00:0ec2:0000:0000:0000:0000:0000:0254]/latest/meta-data/")
                .type("Prometheus")
                .build();

        DataSourceTestResultVO compressedResult = settingsService.testDataSource(compressedRequest);
        DataSourceTestResultVO expandedResult = settingsService.testDataSource(expandedRequest);

        assertThat(compressedResult.isSuccess()).isFalse();
        assertThat(compressedResult.getMessage()).contains("local or private address");
        assertThat(expandedResult.isSuccess()).isFalse();
        assertThat(expandedResult.getMessage()).contains("local or private address");
    }

    @Test
    void testConnectionShouldRejectAlibabaCloudMetadataAddress() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("http://100.100.100.200/latest/meta-data/")
                .type("Prometheus")
                .build();
        DataSourceTestDTO ipv4MappedRequest = DataSourceTestDTO.builder()
                .url("http://[::ffff:100.100.100.200]/latest/meta-data/")
                .type("Prometheus")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);
        DataSourceTestResultVO ipv4MappedResult = settingsService.testDataSource(ipv4MappedRequest);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("local or private address");
        assertThat(ipv4MappedResult.isSuccess()).isFalse();
        assertThat(ipv4MappedResult.getMessage()).contains("local or private address");
    }

    @Test
    void dataSourceAddressPolicyShouldRejectMixedSafeAndLoopbackResults() throws Exception {
        InetAddress[] addresses = {
                InetAddress.getByName("192.0.2.1"),
                InetAddress.getByName("127.0.0.1")
        };

        assertThat(settingsService.areAllowedDataSourceAddresses(addresses)).isFalse();
    }

    @Test
    void dataSourceAddressPolicyShouldAcceptAllSafeResults() throws Exception {
        InetAddress[] addresses = {
                InetAddress.getByName("192.0.2.1"),
                InetAddress.getByName("198.51.100.1")
        };

        assertThat(settingsService.areAllowedDataSourceAddresses(addresses)).isTrue();
    }

    @Test
    void testConnectionShouldRejectIncompleteBasicAuthentication() {
        DataSourceTestResultVO result = settingsService.testDataSource(DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("Prometheus")
                .auth("Basic Auth")
                .username("prom")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo(
                "Basic authentication requires username and password");
    }

    @Test
    void testConnectionShouldRejectMissingBearerToken() {
        DataSourceTestResultVO result = settingsService.testDataSource(DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("Prometheus")
                .auth("Bearer Token")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Bearer authentication requires token");
    }

    @Test
    void testConnectionShouldReturnPrometheusErrorDetails() {
        prometheusServer.expect(requestTo(VICTORIA_METRICS_QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"error\",\"errorType\":\"bad_data\",\"error\":\"invalid query\"}"));
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
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

    @Test
    void testConnectionShouldRejectUnsupportedType() {
        DataSourceTestResultVO result = settingsService.testDataSource(DataSourceTestDTO.builder()
                .url(PROMETHEUS_BASE_URL)
                .type("rocketmq")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Unsupported data source type: rocketmq");
    }

}
