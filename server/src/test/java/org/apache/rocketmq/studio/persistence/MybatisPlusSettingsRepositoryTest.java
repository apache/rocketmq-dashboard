/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqDataSource;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusSettingsRepositoryTest {

    private RmqSettingsMapper settingsMapper;
    private RmqDataSourceMapper dataSourceMapper;
    private MybatisPlusSettingsRepository repository;

    @BeforeEach
    void setUp() {
        settingsMapper = mock(RmqSettingsMapper.class);
        dataSourceMapper = mock(RmqDataSourceMapper.class);
        repository = new MybatisPlusSettingsRepository(settingsMapper, dataSourceMapper,
                new ObjectMapper());
    }

    @Test
    void shouldReturnDefaultsWhenGeneralSettingsDoNotExist() {
        when(settingsMapper.selectOne(any())).thenReturn(null);

        GeneralSettingsVO settings = repository.loadGeneralSettings();

        assertThat(settings.getTheme()).isEqualTo("system");
        assertThat(settings.isRequireLogin()).isFalse();
    }

    @Test
    void shouldRejectCorruptPersistedGeneralSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("{not-json");
        when(settingsMapper.selectOne(any())).thenReturn(settings);

        assertThatThrownBy(repository::loadGeneralSettings)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted general settings are invalid")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void shouldRejectNullPersistedGeneralSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("null");
        when(settingsMapper.selectOne(any())).thenReturn(settings);

        assertThatThrownBy(repository::loadGeneralSettings)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted general settings are invalid")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void shouldReadValidPersistedGeneralSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("{\"theme\":\"dark\",\"requireLogin\":true}");
        when(settingsMapper.selectOne(any())).thenReturn(settings);

        GeneralSettingsVO loaded = repository.loadGeneralSettings();

        assertThat(loaded.getTheme()).isEqualTo("dark");
        assertThat(loaded.isRequireLogin()).isTrue();
    }

    @Test
    void shouldRejectNullPersistedDataSource() {
        RmqDataSource dataSource = new RmqDataSource();
        dataSource.setDsKey("metrics-prod");
        dataSource.setJson("null");
        when(dataSourceMapper.selectOne(any())).thenReturn(dataSource);

        assertThatThrownBy(() -> repository.findDataSourceByKey("metrics-prod"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted data source is invalid: metrics-prod")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void shouldRejectCorruptPersistedDataSource() {
        RmqDataSource dataSource = new RmqDataSource();
        dataSource.setDsKey("metrics-prod");
        dataSource.setJson("{not-json");
        when(dataSourceMapper.selectOne(any())).thenReturn(dataSource);

        assertThatThrownBy(() -> repository.findDataSourceByKey("metrics-prod"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted data source is invalid: metrics-prod")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void shouldReportWhenDataSourceDisappearsDuringReplacement() {
        RmqDataSource existing = new RmqDataSource();
        existing.setDsKey("metrics-prod");
        when(dataSourceMapper.selectOne(any())).thenReturn(existing);
        when(dataSourceMapper.updateById(existing)).thenReturn(0);
        DataSourceVO replacement = DataSourceVO.builder()
                .key("metrics-prod")
                .name("Production metrics")
                .type("prometheus")
                .url("https://metrics.example.com")
                .build();

        assertThat(repository.replaceDataSource(replacement)).isFalse();
    }

    @Test
    void shouldPersistAndReloadLlmApiKeyTest() {
        RmqSettings stored = new RmqSettings();
        when(settingsMapper.selectOne(any())).thenReturn(null).thenReturn(stored);
        when(settingsMapper.insert(any(RmqSettings.class))).thenAnswer(invocation -> {
            RmqSettings entity = invocation.getArgument(0);
            stored.setJson(entity.getJson());
            return 1;
        });

        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("system")
                .llmProvider("tongyi")
                .llmEngine("claude-code")
                .apiKey("sk-roundtrip-token")
                .build();
        repository.saveGeneralSettings(settings);

        assertThat(stored.getJson()).contains("sk-roundtrip-token");
        assertThat(repository.loadGeneralSettings().getApiKey()).isEqualTo("sk-roundtrip-token");
    }
    @Test
    void shouldEscapeLikeWildcardsInDataSourceInventoryQueries() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<RmqDataSource> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(java.util.List.of());
        page.setTotal(0);
        when(dataSourceMapper.selectPage(any(), any())).thenReturn(page);

        repository.findDataSources("a_b 100%", "pro_%", 1, 10);

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RmqDataSource>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class);
        verify(dataSourceMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("ESCAPE");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("a\\_b 100\\%", "pro\\_\\%");
    }

}