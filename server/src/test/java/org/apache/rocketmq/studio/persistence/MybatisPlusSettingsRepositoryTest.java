/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.persistence;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqDataSource;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    void shouldPageDataSourcesByModifiedTimeDescendingWithKeyTiebreaker() {
        RmqDataSource beta = new RmqDataSource();
        beta.setDsKey("ds-b");
        beta.setJson("{\"name\":\"Beta\",\"type\":\"Prometheus\",\"url\":\"http://beta\",\"auth\":\"None\"}");
        RmqDataSource alpha = new RmqDataSource();
        alpha.setDsKey("ds-a");
        alpha.setJson("{\"name\":\"Alpha\",\"type\":\"Prometheus\",\"url\":\"http://alpha\",\"auth\":\"None\"}");
        Page<RmqDataSource> mapperPage = new Page<RmqDataSource>(2, 1)
                .setRecords(java.util.List.of(beta, alpha))
                .setTotal(2);
        when(dataSourceMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mapperPage);

        PageResult<DataSourceVO> result = repository.findDataSourcesPage(2, 1);

        ArgumentCaptor<IPage<RmqDataSource>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RmqDataSource>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(dataSourceMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(1);
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getItems()).extracting(DataSourceVO::getKey).containsExactly("ds-b", "ds-a");
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("ORDER BY", "gmt_modified", "DESC", "ds_key", "ASC");
    }
}
