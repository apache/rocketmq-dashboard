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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class InMemorySettingsRepository implements SettingsRepository {

    private GeneralSettingsVO generalSettings = GeneralSettingsVO.builder()
            .theme("system")
            .compact(false)
            .desktopNotify(true)
            .notifySound(false)
            .sessionTimeout(30)
            .requireLogin(false)
            .llmProvider("openai")
            .apiKey("")
            .model("gpt-4")
            .baseUrl("")
            .build();

    private final Map<String, DataSourceVO> dataSources = new ConcurrentHashMap<>();

    @Override
    public GeneralSettingsVO loadGeneralSettings() {
        return generalSettings;
    }

    @Override
    public void saveGeneralSettings(GeneralSettingsVO settings) {
        this.generalSettings = settings;
    }

    @Override
    public List<DataSourceVO> findAllDataSources() {
        return new ArrayList<>(dataSources.values());
    }

    @Override
    public DataSourceVO saveDataSource(DataSourceVO dataSource) {
        dataSources.put(dataSource.getKey(), dataSource);
        return dataSource;
    }

    @Override
    public void deleteDataSource(String key) {
        dataSources.remove(key);
    }

    @Override
    public Optional<DataSourceVO> findDataSourceByKey(String key) {
        return Optional.ofNullable(dataSources.get(key));
    }
}
