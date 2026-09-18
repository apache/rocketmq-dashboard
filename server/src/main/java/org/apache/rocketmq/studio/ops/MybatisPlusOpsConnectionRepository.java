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

package org.apache.rocketmq.studio.ops;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Slf4j
@Repository
public class MybatisPlusOpsConnectionRepository implements OpsConnectionRepository {

    static final String OPS_CONNECTION_SETTINGS_KEY = "ops-connection";

    private final RmqSettingsMapper settingsMapper;
    private final ObjectMapper objectMapper;

    public MybatisPlusOpsConnectionRepository(RmqSettingsMapper settingsMapper, ObjectMapper objectMapper) {
        this.settingsMapper = settingsMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OpsConnectionSettings> load() {
        RmqSettings entity = findSettings();
        if (entity == null || entity.getJson() == null) {
            return Optional.empty();
        }
        try {
            OpsConnectionSettings settings = objectMapper.readValue(entity.getJson(), OpsConnectionSettings.class);
            if (settings == null) {
                throw new BusinessException(500, "Persisted Ops runtime settings are invalid");
            }
            return Optional.of(settings);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.error("Failed to deserialize Ops runtime settings", exception);
            throw new BusinessException(500, "Persisted Ops runtime settings are invalid");
        }
    }

    @Override
    @Transactional
    public void save(OpsConnectionSettings settings) {
        String json = toJson(settings);
        RmqSettings entity = findSettings();
        LocalDateTime now = LocalDateTime.now();
        if (entity == null) {
            try {
                insertRaw(json, now);
            } catch (DuplicateKeyException duplicateKey) {
                RmqSettings concurrent = findSettings();
                if (concurrent == null) {
                    throw duplicateKey;
                }
                concurrent.setJson(json);
                concurrent.setGmtModified(LocalDateTime.now());
                settingsMapper.updateById(concurrent);
            }
        } else {
            entity.setJson(json);
            entity.setGmtModified(now);
            settingsMapper.updateById(entity);
        }
    }

    @Override
    @Transactional
    public OpsConnectionSettings update(UnaryOperator<OpsConnectionSettings> updater,
                                        Supplier<OpsConnectionSettings> defaultSettings,
                                        Consumer<OpsConnectionSettings> validator) {
        return updateLocked(updater, defaultSettings, validator, false);
    }

    private RmqSettings findSettings() {
        return settingsMapper.selectOne(new QueryWrapper<RmqSettings>()
                .eq("settings_key", OPS_CONNECTION_SETTINGS_KEY)
                .last("LIMIT 1"));
    }

    private RmqSettings findSettingsForUpdate() {
        return settingsMapper.selectOne(new QueryWrapper<RmqSettings>()
                .eq("settings_key", OPS_CONNECTION_SETTINGS_KEY)
                .last("LIMIT 1 FOR UPDATE"));
    }

    private OpsConnectionSettings updateLocked(UnaryOperator<OpsConnectionSettings> updater,
                                               Supplier<OpsConnectionSettings> defaultSettings,
                                               Consumer<OpsConnectionSettings> validator,
                                               boolean retriedAfterConcurrentInsert) {
        RmqSettings entity = findSettingsForUpdate();
        OpsConnectionSettings current = entity == null ? defaultSettings.get() : toSettings(entity);
        OpsConnectionSettings updated = Objects.requireNonNull(updater.apply(current),
                "updater must return settings");
        validator.accept(updated);
        String json = toJson(updated);
        if (entity == null) {
            try {
                insertRaw(json, LocalDateTime.now());
            } catch (DuplicateKeyException duplicateKey) {
                if (retriedAfterConcurrentInsert) {
                    throw duplicateKey;
                }
                return updateLocked(updater, defaultSettings, validator, true);
            }
        } else {
            entity.setJson(json);
            entity.setGmtModified(LocalDateTime.now());
            settingsMapper.updateById(entity);
        }
        return updated;
    }

    private void insertRaw(String json, LocalDateTime now) {
        RmqSettings entity = new RmqSettings();
        entity.setSettingsKey(OPS_CONNECTION_SETTINGS_KEY);
        entity.setJson(json);
        entity.setGmtCreate(now);
        entity.setGmtModified(now);
        settingsMapper.insert(entity);
    }

    private OpsConnectionSettings toSettings(RmqSettings entity) {
        try {
            OpsConnectionSettings settings = objectMapper.readValue(entity.getJson(), OpsConnectionSettings.class);
            if (settings == null) {
                throw new BusinessException(500, "Persisted Ops runtime settings are invalid");
            }
            return settings;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            log.error("Failed to deserialize Ops runtime settings", exception);
            throw new BusinessException(500, "Persisted Ops runtime settings are invalid");
        }
    }

    private String toJson(OpsConnectionSettings settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize Ops runtime settings", exception);
            throw new RuntimeException("Failed to save Ops runtime settings", exception);
        }
    }
}
