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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SettingsRepository settingsRepository;

    @InjectMocks
    private SettingsService settingsService;

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
    void saveGeneralSettingsShouldDelegateToRepository() {
        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("light")
                .compact(false)
                .sessionTimeout(60)
                .build();
        doNothing().when(settingsRepository).saveGeneralSettings(settings);

        settingsService.saveGeneralSettings(settings);

        verify(settingsRepository).saveGeneralSettings(settings);
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
    void createDataSourceShouldDelegateToRepository() {
        DataSourceVO input = DataSourceVO.builder().name("New DS").type("rocketmq")
                .url("new-host:9876").build();
        DataSourceVO saved = DataSourceVO.builder().key("ds-new").name("New DS").type("rocketmq")
                .url("new-host:9876").status("connected").build();
        when(settingsRepository.saveDataSource(any(DataSourceVO.class))).thenReturn(saved);

        DataSourceVO result = settingsService.createDataSource(input);

        assertThat(result.getKey()).isEqualTo("ds-new");
        assertThat(result.getName()).isEqualTo("New DS");
        assertThat(result.getStatus()).isEqualTo("connected");
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
    void testConnectionShouldReturnSuccess() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("localhost:9876")
                .type("rocketmq")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Connection successful");
    }

    @Test
    void testConnectionShouldReturnSuccessForAnyInput() {
        DataSourceTestDTO request = DataSourceTestDTO.builder()
                .url("invalid-host:9999")
                .type("unknown")
                .auth("bad-auth")
                .build();

        DataSourceTestResultVO result = settingsService.testDataSource(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Connection successful");
    }
}
