/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqDataSource;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SslSettingsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
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
        when(settingsMapper.selectById("singleton")).thenReturn(null);

        GeneralSettingsVO settings = repository.loadGeneralSettings();

        assertThat(settings.getTheme()).isEqualTo("system");
        assertThat(settings.isRequireLogin()).isFalse();
    }

    @Test
    void shouldRejectCorruptPersistedGeneralSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("{not-json");
        when(settingsMapper.selectById("singleton")).thenReturn(settings);

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
        when(settingsMapper.selectById("singleton")).thenReturn(settings);

        GeneralSettingsVO loaded = repository.loadGeneralSettings();

        assertThat(loaded.getTheme()).isEqualTo("dark");
        assertThat(loaded.isRequireLogin()).isTrue();
    }

    @Test
    void shouldReturnDefaultsWhenSslSettingsDoNotExist() {
        when(settingsMapper.selectById("ssl")).thenReturn(null);

        SslSettingsRecord settings = repository.loadSslSettings();

        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getProtocol()).isEqualTo("TLSv1.3");
        assertThat(settings.getKeyStoreType()).isEqualTo("PKCS12");
    }

    @Test
    void shouldReadValidPersistedSslSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("""
                {
                  "enabled": true,
                  "protocol": "TLSv1.2",
                  "clientAuth": "need",
                  "keyStoreType": "PKCS12",
                  "keyStorePath": "/etc/server.p12",
                  "keyStorePassword": "%s"
                }
                """.formatted(CredentialUtils.encodeBase64("secret")));
        when(settingsMapper.selectById("ssl")).thenReturn(settings);

        SslSettingsRecord loaded = repository.loadSslSettings();

        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getProtocol()).isEqualTo("TLSv1.2");
        assertThat(loaded.getKeyStorePassword()).isEqualTo("secret");
    }

    @Test
    void shouldTolerateLegacyPlainTextSslPasswords() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("""
                {
                  "enabled": true,
                  "protocol": "TLSv1.2",
                  "clientAuth": "none",
                  "keyStoreType": "PKCS12",
                  "keyStorePath": "/etc/server.p12",
                  "keyStorePassword": "legacy-secret"
                }
                """);
        when(settingsMapper.selectById("ssl")).thenReturn(settings);

        SslSettingsRecord loaded = repository.loadSslSettings();

        assertThat(loaded.getKeyStorePassword()).isEqualTo("legacy-secret");
    }

    @Test
    void shouldTolerateLegacyPlainTextSslPasswordThatLooksBase64Decodable() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("""
                {
                  "enabled": true,
                  "protocol": "TLSv1.2",
                  "clientAuth": "none",
                  "keyStoreType": "PKCS12",
                  "keyStorePath": "/etc/server.p12",
                  "keyStorePassword": "secret"
                }
                """);
        when(settingsMapper.selectById("ssl")).thenReturn(settings);

        SslSettingsRecord loaded = repository.loadSslSettings();

        assertThat(loaded.getKeyStorePassword()).isEqualTo("secret");
    }

    @Test
    void shouldSaveSslSettingsUnderDedicatedSettingsRow() {
        when(settingsMapper.selectById("ssl")).thenReturn(null);
        SslSettingsRecord settings = SslSettingsRecord.defaults();
        settings.setEnabled(true);
        settings.setKeyStorePath("/etc/server.p12");
        settings.setKeyStorePassword("secret");

        repository.saveSslSettings(settings);

        verify(settingsMapper).insert(argThat((RmqSettings entity) ->
                "ssl".equals(entity.getId())
                        && entity.getJson().contains("/etc/server.p12")
                        && entity.getJson().contains(CredentialUtils.encodeBase64("secret"))
                        && !entity.getJson().contains("\"keyStorePassword\":\"secret\"")));
    }

    @Test
    void shouldRejectCorruptPersistedSslSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setJson("{not-json");
        when(settingsMapper.selectById("ssl")).thenReturn(settings);

        assertThatThrownBy(repository::loadSslSettings)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted SSL settings are invalid")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void shouldRejectCorruptPersistedDataSource() {
        RmqDataSource dataSource = new RmqDataSource();
        dataSource.setDsKey("metrics-prod");
        dataSource.setJson("{not-json");
        when(dataSourceMapper.selectById("metrics-prod")).thenReturn(dataSource);

        assertThatThrownBy(() -> repository.findDataSourceByKey("metrics-prod"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted data source is invalid: metrics-prod")
                .extracting("code")
                .isEqualTo(500);
    }
}
