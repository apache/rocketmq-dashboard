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
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.alert.NotificationOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private NotificationOutboxService notificationOutboxService;

    @Test
    void getGeneralSettingsShouldReturnSettingsTest() throws Exception {
        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(30)
                .requireLogin(true)
                .llmProvider("openai")
                .apiKey("sk-xxx")
                .model("gpt-4")
                .baseUrl("https://api.openai.com")
                .build();
        when(settingsService.getGeneralSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/settings/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.message", is("success")))
                .andExpect(jsonPath("$.data.theme", is("dark")))
                .andExpect(jsonPath("$.data.compact", is(true)))
                .andExpect(jsonPath("$.data.desktopNotify", is(true)))
                .andExpect(jsonPath("$.data.notifySound", is(false)))
                .andExpect(jsonPath("$.data.sessionTimeout", is(30)))
                .andExpect(jsonPath("$.data.requireLogin", is(true)))
                .andExpect(jsonPath("$.data.llmProvider", is("openai")))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist())
                .andExpect(jsonPath("$.data.apiKeyConfigured", is(true)))
                .andExpect(jsonPath("$.data.clearApiKey").doesNotExist())
                .andExpect(jsonPath("$.data.model", is("gpt-4")));
    }

    @Test
    void getGeneralSettingsShouldRedactNotificationWebhooksForReadersTest() throws Exception {
        AuthenticatedUserContext.setUser("reader", false);
        try {
            GeneralSettingsVO settings = GeneralSettingsVO.builder()
                    .theme("dark")
                    .compact(true)
                    .desktopNotify(true)
                    .notifySound(false)
                    .sessionTimeout(30)
                    .requireLogin(true)
                    .llmProvider("openai")
                    .dingtalkWebhook("https://oapi.dingtalk.com/robot/send?access_token=secret")
                    .emailRecipients("ops@example.com")
                    .smsWebhook("https://sms.example.test/notify")
                    .model("gpt-4")
                    .baseUrl("https://api.openai.com")
                    .build();
            when(settingsService.getGeneralSettings()).thenReturn(settings.toBuilder()
                    .dingtalkWebhook("******")
                    .smsWebhook("******")
                    .build());

            mockMvc.perform(get("/api/settings/general"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dingtalkWebhook", is("******")))
                    .andExpect(jsonPath("$.data.dingtalkWebhookConfigured", is(true)))
                    .andExpect(jsonPath("$.data.emailRecipients", is("ops@example.com")))
                    .andExpect(jsonPath("$.data.smsWebhook", is("******")))
                    .andExpect(jsonPath("$.data.smsWebhookConfigured", is(true)));
        } finally {
            AuthenticatedUserContext.clear();
        }
    }

    @Test
    void saveGeneralSettingsShouldReturnSuccessTest() throws Exception {
        doNothing().when(settingsService).saveGeneralSettings(any(GeneralSettingsVO.class));

        mockMvc.perform(post("/api/settings/general/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "theme": "light",
                                  "compact": false,
                                  "desktopNotify": true,
                                  "notifySound": false,
                                  "sessionTimeout": 60,
                                  "requireLogin": true,
                                  "llmProvider": "openai",
                                  "apiKey": "sk-new",
                                  "apiKeyConfigured": false,
                                  "model": "gpt-4",
                                  "baseUrl": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(settingsService).saveGeneralSettings(argThat(settings ->
                "sk-new".equals(settings.getApiKey()) && settings.isApiKeyConfigured()));
    }

    @Test
    void saveGeneralSettingsShouldAcceptExplicitApiKeyClearWithoutBindingResponseStateTest() throws Exception {
        doNothing().when(settingsService).saveGeneralSettings(any(GeneralSettingsVO.class));

        mockMvc.perform(post("/api/settings/general/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "theme": "light",
                                  "compact": false,
                                  "desktopNotify": true,
                                  "notifySound": false,
                                  "sessionTimeout": 60,
                                  "requireLogin": true,
                                  "llmProvider": "openai",
                                  "clearApiKey": true,
                                  "apiKeyConfigured": true,
                                  "model": "gpt-4",
                                  "baseUrl": ""
                                }
                                """))
                .andExpect(status().isOk());

        verify(settingsService).saveGeneralSettings(argThat(settings ->
                settings.isClearApiKey() && !settings.isApiKeyConfigured()));
    }

    @Test
    void saveGeneralSettingsShouldRejectIncompleteReplacementTest() throws Exception {
        mockMvc.perform(post("/api/settings/general/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "theme": "light",
                                  "apiKey": "sk-new"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(settingsService);
    }

    @Test
    void listDataSourcesShouldReturnAllSourcesTest() throws Exception {
        DataSourceVO ds1 = DataSourceVO.builder().key("ds-1").name("Production").type("Prometheus")
                .url("prod:9876").status("connected").build();
        DataSourceVO ds2 = DataSourceVO.builder().key("ds-2").name("Staging").type("Prometheus")
                .url("staging:9876").status("disconnected").build();
        when(settingsService.listDataSources()).thenReturn(Arrays.asList(ds1, ds2));

        mockMvc.perform(get("/api/settings/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].key", is("ds-1")))
                .andExpect(jsonPath("$.data[0].name", is("Production")))
                .andExpect(jsonPath("$.data[0].status", is("connected")))
                .andExpect(jsonPath("$.data[1].key", is("ds-2")))
                .andExpect(jsonPath("$.data[1].name", is("Staging")));
    }

    @Test
    void listDataSourcesShouldReturnEmptyListTest() throws Exception {
        when(settingsService.listDataSources()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/settings/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listDataSourcesPageShouldBindFiltersAndPaginationTest() throws Exception {
        DataSourceVO ds1 = DataSourceVO.builder().key("ds-1").name("Production").type("Prometheus")
                .url("prod:9876").status("connected").build();
        PageResult<DataSourceVO> page = PageResult.of(List.of(ds1), 1, 2, 20);
        when(settingsService.listDataSources("prod", "prometheus", 2, 20)).thenReturn(page);

        mockMvc.perform(get("/api/settings/datasources/page")
                        .param("search", "prod")
                        .param("type", "prometheus")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.page", is(2)))
                .andExpect(jsonPath("$.data.size", is(20)))
                .andExpect(jsonPath("$.data.items[0].key", is("ds-1")));

        verify(settingsService).listDataSources("prod", "prometheus", 2, 20);
    }

    @Test
    void createDataSourceShouldReturnCreatedSourceTest() throws Exception {
        DataSourceVO input = DataSourceVO.builder().name("New DS").type("Prometheus")
                .url("new-host:9876").instanceIds(List.of("instance-a", "instance-b")).build();
        DataSourceVO created = DataSourceVO.builder().key("ds-new").name("New DS").type("Prometheus")
                .url("new-host:9876").status("connected")
                .instanceIds(List.of("instance-a", "instance-b")).build();
        when(settingsService.createDataSource(any(DataSourceVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.key", is("ds-new")))
                .andExpect(jsonPath("$.data.name", is("New DS")))
                .andExpect(jsonPath("$.data.status", is("connected")));

        verify(settingsService).createDataSource(argThat(dataSource ->
                List.of("instance-a", "instance-b").equals(dataSource.getInstanceIds())));
    }

    @Test
    void createDataSourceShouldRejectMissingUrlTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New DS",
                                  "type": "prometheus"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("url is required")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void createDataSourceShouldRejectUnsupportedMetricsTypeTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New DS",
                                  "type": "unsupported",
                                  "url": "http://metrics.example.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Unsupported metrics data source type")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void createDataSourceShouldRejectUnsupportedAuthenticationTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New DS",
                                  "type": "Prometheus",
                                  "url": "http://metrics.example.test",
                                  "auth": "API Key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Unsupported metrics data source authentication")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void createDataSourceShouldRejectNullRequestBodyTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid request body")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void updateDataSourceShouldReturnUpdatedSourceTest() throws Exception {
        DataSourceVO input = DataSourceVO.builder().key("ds-1").name("Updated DS").type("Prometheus")
                .url("updated:9876").instanceIds(List.of("instance-b")).build();
        when(settingsService.updateDataSource(any(DataSourceVO.class))).thenReturn(input);

        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.key", is("ds-1")))
                .andExpect(jsonPath("$.data.name", is("Updated DS")));

        verify(settingsService).updateDataSource(argThat(dataSource ->
                List.of("instance-b").equals(dataSource.getInstanceIds())));
    }

    @Test
    void updateDataSourceShouldRejectMissingNameTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "ds-1",
                                  "type": "Prometheus",
                                  "url": "updated:9876"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("name is required")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void updateDataSourceShouldRejectUnsupportedMetricsTypeTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "ds-1",
                                  "name": "Updated DS",
                                  "type": "unsupported",
                                  "url": "http://metrics.example.test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Unsupported metrics data source type")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void updateDataSourceShouldRejectUnsupportedAuthenticationTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "ds-1",
                                  "name": "Updated DS",
                                  "type": "Prometheus",
                                  "url": "http://metrics.example.test",
                                  "auth": "API Key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Unsupported metrics data source authentication")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void updateDataSourceShouldRejectNullRequestBodyTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Invalid request body")));

        verifyNoInteractions(settingsService);
    }

    @Test
    void deleteDataSourceShouldReturnSuccessTest() throws Exception {
        doNothing().when(settingsService).deleteDataSource("ds-1");

        mockMvc.perform(post("/api/settings/datasources/delete")
                        .param("key", "ds-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        verify(settingsService).deleteDataSource("ds-1");
    }

    @Test
    void deleteDataSourceShouldRejectMissingKeyTest() throws Exception {
        doThrow(new BusinessException(400, "Data source key is required"))
                .when(settingsService).deleteDataSource(null);

        mockMvc.perform(post("/api/settings/datasources/delete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("Data source key is required")));

        verify(settingsService).deleteDataSource(null);
    }

    @Test
    void deleteDataSourceShouldRejectUnknownKeyTest() throws Exception {
        doThrow(new BusinessException(404, "Data source not found: missing"))
                .when(settingsService).deleteDataSource("missing");

        mockMvc.perform(post("/api/settings/datasources/delete")
                        .param("key", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is(404)))
                .andExpect(jsonPath("$.message", is("Data source not found: missing")));

        verify(settingsService).deleteDataSource("missing");
    }

    @Test
    void dataSourceShouldReturnTestResultTest() throws Exception {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("localhost:9876")
                .type("rocketmq")
                .build();
        DataSourceTestResultVO testResult = DataSourceTestResultVO.builder()
                .success(true)
                .message("Connection successful")
                .build();
        when(settingsService.testDataSource(any(DataSourceTestDTO.class))).thenReturn(testResult);

        mockMvc.perform(post("/api/settings/datasources/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.success", is(true)))
                .andExpect(jsonPath("$.data.message", is("Connection successful")));
    }

    @Test
    void dataSourceShouldRejectMissingTypeTest() throws Exception {
        mockMvc.perform(post("/api/settings/datasources/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "localhost:9876"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is(400)))
                .andExpect(jsonPath("$.message", is("type is required")));

        verifyNoInteractions(settingsService);
    }
    @Test
    void testNotificationShouldDelegateForChannel() throws Exception {
        mockMvc.perform(post("/api/settings/general/test-notification")
                        .param("channel", "email"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationOutboxService).sendTestMessage("email");
    }

    @Test
    void testNotificationShouldRejectMissingChannel() throws Exception {
        mockMvc.perform(post("/api/settings/general/test-notification"))
                .andExpect(status().isBadRequest());
    }

}
