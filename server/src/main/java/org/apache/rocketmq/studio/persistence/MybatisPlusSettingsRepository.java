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
package org.apache.rocketmq.studio.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.persistence.entity.RmqDataSource;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.apache.rocketmq.studio.settings.SslSettingsRecord;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class MybatisPlusSettingsRepository implements SettingsRepository {

    private static final String SETTINGS_ID = "singleton";
    private static final String SSL_SETTINGS_ID = "ssl";

    private final RmqSettingsMapper settingsMapper;
    private final RmqDataSourceMapper dataSourceMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusSettingsRepository(RmqSettingsMapper settingsMapper,
                                         RmqDataSourceMapper dataSourceMapper,
                                         ObjectMapper objectMapper) {
        this.settingsMapper = settingsMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneralSettingsVO loadGeneralSettings() {
        RmqSettings entity = settingsMapper.selectById(SETTINGS_ID);
        if (entity == null || entity.getJson() == null) {
            return GeneralSettingsVO.builder()
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
        }
        try {
            return objectMapper.readValue(entity.getJson(), GeneralSettingsVO.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize general settings", e);
            throw new BusinessException(500, "Persisted general settings are invalid");
        }
    }

    @Override
    @Transactional
    public void saveGeneralSettings(GeneralSettingsVO settings) {
        saveSettingsJson(SETTINGS_ID, settings);
    }

    @Override
    public SslSettingsRecord loadSslSettings() {
        RmqSettings entity = settingsMapper.selectById(SSL_SETTINGS_ID);
        if (entity == null || entity.getJson() == null) {
            return SslSettingsRecord.defaults();
        }
        try {
            return objectMapper.readValue(entity.getJson(), SslSettingsRecord.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize SSL settings", e);
            throw new BusinessException(500, "Persisted SSL settings are invalid");
        }
    }

    @Override
    @Transactional
    public void saveSslSettings(SslSettingsRecord settings) {
        saveSettingsJson(SSL_SETTINGS_ID, settings);
    }

    private void saveSettingsJson(String id, Object settings) {
        try {
            String json = objectMapper.writeValueAsString(settings);
            RmqSettings entity = settingsMapper.selectById(id);
            if (entity == null) {
                entity = new RmqSettings();
                entity.setId(id);
                entity.setJson(json);
                entity.setUpdatedAt(LocalDateTime.now());
                settingsMapper.insert(entity);
            } else {
                entity.setJson(json);
                entity.setUpdatedAt(LocalDateTime.now());
                settingsMapper.updateById(entity);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize settings: {}", id, e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }

    @Override
    public List<DataSourceVO> findAllDataSources() {
        return dataSourceMapper.selectList(null).stream()
                .map(this::toDataSourceVO)
                .collect(Collectors.toList());
    }

    @Override
    public DataSourceVO saveDataSource(DataSourceVO dataSource) {
        RmqDataSource entity = new RmqDataSource();
        entity.setDsKey(dataSource.getKey());
        entity.setJson(toJson(dataSource));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        dataSourceMapper.insert(entity);
        return dataSource;
    }

    @Override
    public boolean replaceDataSource(DataSourceVO dataSource) {
        RmqDataSource existing = dataSourceMapper.selectById(dataSource.getKey());
        if (existing == null) {
            return false;
        }
        existing.setJson(toJson(dataSource));
        existing.setUpdatedAt(LocalDateTime.now());
        dataSourceMapper.updateById(existing);
        return true;
    }

    @Override
    public boolean deleteDataSource(String key) {
        return dataSourceMapper.deleteById(key) > 0;
    }

    @Override
    public Optional<DataSourceVO> findDataSourceByKey(String key) {
        RmqDataSource entity = dataSourceMapper.selectById(key);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDataSourceVO(entity));
    }

    private DataSourceVO toDataSourceVO(RmqDataSource entity) {
        try {
            DataSourceVO vo = objectMapper.readValue(entity.getJson(), DataSourceVO.class);
            vo.setKey(entity.getDsKey());
            return vo;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize data source: {}", entity.getDsKey(), e);
            throw new BusinessException(500, "Persisted data source is invalid: " + entity.getDsKey());
        }
    }

    private String toJson(DataSourceVO dataSource) {
        try {
            return objectMapper.writeValueAsString(dataSource);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize data source: {}", dataSource.getKey(), e);
            throw new RuntimeException("Failed to serialize data source", e);
        }
    }
}
