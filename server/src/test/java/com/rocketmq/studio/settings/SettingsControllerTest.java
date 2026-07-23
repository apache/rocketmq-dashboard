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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
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

    @Test
    void getGeneralSettingsShouldReturnSettings() throws Exception {
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
    void saveGeneralSettingsShouldReturnSuccess() throws Exception {
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
    void saveGeneralSettingsShouldAcceptExplicitApiKeyClearWithoutBindingResponseState() throws Exception {
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
    void saveGeneralSettingsShouldRejectIncompleteReplacement() throws Exception {
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
    void listDataSourcesShouldReturnAllSources() throws Exception {
        DataSourceVO ds1 = DataSourceVO.builder().key("ds-1").name("Production").type("rocketmq")
                .url("prod:9876").status("connected").build();
        DataSourceVO ds2 = DataSourceVO.builder().key("ds-2").name("Staging").type("rocketmq")
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
    void listDataSourcesShouldReturnEmptyList() throws Exception {
        when(settingsService.listDataSources()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/settings/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void createDataSourceShouldReturnCreatedSource() throws Exception {
        DataSourceVO input = DataSourceVO.builder().name("New DS").type("rocketmq")
                .url("new-host:9876").build();
        DataSourceVO created = DataSourceVO.builder().key("ds-new").name("New DS").type("rocketmq")
                .url("new-host:9876").status("connected").build();
        when(settingsService.createDataSource(any(DataSourceVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/settings/datasources/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.key", is("ds-new")))
                .andExpect(jsonPath("$.data.name", is("New DS")))
                .andExpect(jsonPath("$.data.status", is("connected")));
    }

    @Test
    void updateDataSourceShouldReturnUpdatedSource() throws Exception {
        DataSourceVO input = DataSourceVO.builder().key("ds-1").name("Updated DS").type("rocketmq")
                .url("updated:9876").build();
        when(settingsService.updateDataSource(any(DataSourceVO.class))).thenReturn(input);

        mockMvc.perform(post("/api/settings/datasources/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)))
                .andExpect(jsonPath("$.data.key", is("ds-1")))
                .andExpect(jsonPath("$.data.name", is("Updated DS")));
    }

    @Test
    void deleteDataSourceShouldReturnSuccess() throws Exception {
        doNothing().when(settingsService).deleteDataSource("ds-1");

        mockMvc.perform(post("/api/settings/datasources/delete")
                        .param("key", "ds-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(200)));

        verify(settingsService).deleteDataSource("ds-1");
    }

    @Test
    void testDataSourceShouldReturnTestResult() throws Exception {
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
}
