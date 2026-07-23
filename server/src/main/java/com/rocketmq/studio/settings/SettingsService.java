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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsRepository settingsRepository;


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
        log.info("Testing data source connection: url={}, type={}", request.getUrl(), request.getType());
        // Stub: always return success for now
        return DataSourceTestResultVO.builder()
                .success(true)
                .message("Connection successful")
                .build();
    }
}
