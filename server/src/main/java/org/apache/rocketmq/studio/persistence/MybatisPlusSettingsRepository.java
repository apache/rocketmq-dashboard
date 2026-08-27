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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.persistence.entity.RmqDataSource;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class MybatisPlusSettingsRepository implements SettingsRepository {

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

    /**
     * The settings table holds a single row; load it regardless of its auto-increment id.
     */
    private RmqSettings findSingletonSettings() {
        return settingsMapper.selectOne(new QueryWrapper<RmqSettings>()
                .orderByAsc("id")
                .last("LIMIT 1"));
    }

    @Override
    public GeneralSettingsVO loadGeneralSettings() {
        RmqSettings entity = findSingletonSettings();
        if (entity == null || entity.getJson() == null) {
            return GeneralSettingsVO.builder()
                    .theme("system")
                    .compact(false)
                    .desktopNotify(true)
                    .notifySound(false)
                    .sessionTimeout(30)
                    .requireLogin(false)
                    .llmProvider("tongyi")
                    .apiKey("")
                    .model("qwen3.8-max")
                    .baseUrl("")
                    .build();
        }
        try {
            GeneralSettingsVO settings = objectMapper.readValue(entity.getJson(), GeneralSettingsVO.class);
            if (settings == null) {
                throw new BusinessException(500, "Persisted general settings are invalid");
            }
            return settings;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize general settings", e);
            throw new BusinessException(500, "Persisted general settings are invalid");
        }
    }

    @Override
    @Transactional
    public void saveGeneralSettings(GeneralSettingsVO settings) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(settings);
            if (org.springframework.util.StringUtils.hasText(settings.getApiKey())) {
                // apiKey is WRITE_ONLY (hidden from API responses), so plain serialization
                // drops it; re-add it here or the configured LLM token is lost on restart.
                node.put("apiKey", settings.getApiKey());
            }
            if (org.springframework.util.StringUtils.hasText(settings.getDingtalkSigningSecret())) {
                // The signing secret is also WRITE_ONLY and must be retained in persisted settings.
                node.put("dingtalkSigningSecret", settings.getDingtalkSigningSecret());
            }
            String json = objectMapper.writeValueAsString(node);
            RmqSettings entity = findSingletonSettings();
            if (entity == null) {
                entity = new RmqSettings();
                entity.setJson(json);
                entity.setGmtCreate(LocalDateTime.now());
                entity.setGmtModified(LocalDateTime.now());
                settingsMapper.insert(entity);
            } else {
                entity.setJson(json);
                entity.setGmtModified(LocalDateTime.now());
                settingsMapper.updateById(entity);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize general settings", e);
            throw new RuntimeException("Failed to save settings", e);
        }
    }

    @Override
    public List<DataSourceVO> findAllDataSources() {
        return dataSourceMapper.selectList(new QueryWrapper<RmqDataSource>().orderByAsc("id")).stream()
                .map(this::toDataSourceVO)
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<DataSourceVO> findDataSources(String search, String type, int page, int pageSize) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String normalizedType = type == null || type.isBlank() ? null : type.trim();
        QueryWrapper<RmqDataSource> query = new QueryWrapper<RmqDataSource>()
                .like(normalizedSearch != null, "json", normalizedSearch)
                .apply(normalizedType != null,
                        "LOWER(json) LIKE CONCAT('%\"type\":\"', LOWER({0}), '\"%')", normalizedType)
                .orderByDesc("gmt_modified", "id");
        Page<RmqDataSource> result = dataSourceMapper.selectPage(new Page<>(page, pageSize), query);
        return PageResult.of(result.getRecords().stream().map(this::toDataSourceVO).toList(),
                result.getTotal(), page, pageSize);
    }

    @Override
    @Transactional
    public DataSourceVO saveDataSource(DataSourceVO dataSource) {
        RmqDataSource entity = new RmqDataSource();
        // The ds_key business key is derived from the auto-increment id: insert first with a
        // temporary unique key, then publish "ds-<id>" once the id is known.
        entity.setDsKey("ds-tmp-" + System.currentTimeMillis() + "-" + Math.floorMod(System.nanoTime(), 1_000_000));
        entity.setJson(toJson(dataSource));
        entity.setGmtCreate(LocalDateTime.now());
        entity.setGmtModified(LocalDateTime.now());
        dataSourceMapper.insert(entity);
        String dsKey = "ds-" + entity.getId();
        entity.setDsKey(dsKey);
        dataSource.setKey(dsKey);
        entity.setJson(toJson(dataSource));
        dataSourceMapper.updateById(entity);
        return dataSource;
    }

    @Override
    public boolean replaceDataSource(DataSourceVO dataSource) {
        RmqDataSource existing = selectByDsKey(dataSource.getKey());
        if (existing == null) {
            return false;
        }
        existing.setJson(toJson(dataSource));
        existing.setGmtModified(LocalDateTime.now());
        return dataSourceMapper.updateById(existing) > 0;
    }

    @Override
    public boolean deleteDataSource(String key) {
        return dataSourceMapper.delete(
                new QueryWrapper<RmqDataSource>().eq("ds_key", key)) > 0;
    }

    @Override
    public Optional<DataSourceVO> findDataSourceByKey(String key) {
        RmqDataSource entity = selectByDsKey(key);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(toDataSourceVO(entity));
    }

    private RmqDataSource selectByDsKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return dataSourceMapper.selectOne(new QueryWrapper<RmqDataSource>()
                .eq("ds_key", key)
                .last("LIMIT 1"));
    }

    private DataSourceVO toDataSourceVO(RmqDataSource entity) {
        try {
            DataSourceVO vo = objectMapper.readValue(entity.getJson(), DataSourceVO.class);
            if (vo == null) {
                throw new BusinessException(500, "Persisted data source is invalid: " + entity.getDsKey());
            }
            vo.setKey(entity.getDsKey());
            return vo;
        } catch (JsonProcessingException | IllegalArgumentException e) {
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
