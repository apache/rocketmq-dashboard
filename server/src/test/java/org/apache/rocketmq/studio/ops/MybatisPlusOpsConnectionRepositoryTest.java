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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MybatisPlusOpsConnectionRepositoryTest {

    private RmqSettingsMapper settingsMapper;
    private MybatisPlusOpsConnectionRepository repository;

    @BeforeEach
    void setUp() {
        settingsMapper = mock(RmqSettingsMapper.class);
        repository = new MybatisPlusOpsConnectionRepository(settingsMapper, new ObjectMapper());
    }

    @Test
    void loadShouldReturnEmptyWhenOpsConnectionSettingsDoNotExist() {
        when(settingsMapper.selectOne(any())).thenReturn(null);

        assertThat(repository.load()).isEmpty();
    }

    @Test
    void loadShouldRejectCorruptPersistedOpsConnectionSettings() {
        RmqSettings settings = new RmqSettings();
        settings.setSettingsKey("ops-connection");
        settings.setJson("{not-json");
        when(settingsMapper.selectOne(any())).thenReturn(settings);

        assertThatThrownBy(repository::load)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Persisted Ops runtime settings are invalid")
                .extracting("code")
                .isEqualTo(500);
    }

    @Test
    void saveShouldInsertOnlyTheOpsConnectionSettingsRow() throws Exception {
        when(settingsMapper.selectOne(any())).thenReturn(null);

        repository.save(new OpsConnectionSettings(List.of("ns1:9876"), "ns1:9876", true, false));

        ArgumentCaptor<RmqSettings> captor = ArgumentCaptor.forClass(RmqSettings.class);
        verify(settingsMapper).insert(captor.capture());
        assertThat(captor.getValue().getSettingsKey()).isEqualTo("ops-connection");
        assertThat(captor.getValue().getGmtCreate()).isNotNull();
        assertThat(captor.getValue().getGmtModified()).isNotNull();
        OpsConnectionSettings persisted = new ObjectMapper()
                .readValue(captor.getValue().getJson(), OpsConnectionSettings.class);
        assertThat(persisted.addresses()).containsExactly("ns1:9876");
        assertThat(persisted.useVIPChannel()).isTrue();
    }

    @Test
    void saveShouldUpdateExistingOpsConnectionSettingsRow() {
        RmqSettings existing = new RmqSettings();
        existing.setId(7L);
        existing.setSettingsKey("ops-connection");
        when(settingsMapper.selectOne(any())).thenReturn(existing);

        repository.save(new OpsConnectionSettings(List.of("ns1:9876"), "ns1:9876", false, true));

        verify(settingsMapper).updateById(argThat((RmqSettings entity) -> entity == existing
                && entity.getJson().contains("\"useTLS\":true")));
    }

    @Test
    void saveShouldUpdateTheOpsConnectionRowWhenConcurrentCreationWins() {
        RmqSettings concurrent = new RmqSettings();
        concurrent.setId(8L);
        concurrent.setSettingsKey("ops-connection");
        when(settingsMapper.selectOne(any())).thenReturn(null, concurrent);
        doThrow(new DuplicateKeyException("uk_settings_key"))
                .when(settingsMapper).insert(any(RmqSettings.class));

        repository.save(new OpsConnectionSettings(List.of("ns1:9876"), "ns1:9876", false, false));

        verify(settingsMapper).insert(argThat((RmqSettings entity) ->
                "ops-connection".equals(entity.getSettingsKey())));
        verify(settingsMapper).updateById(argThat((RmqSettings entity) -> entity == concurrent
                && entity.getJson().contains("\"currentNamesrv\":\"ns1:9876\"")));
    }

    @Test
    void updateShouldRetryAgainstConcurrentSettingsWhenInsertLosesTheRace() {
        RmqSettings concurrent = new RmqSettings();
        concurrent.setId(8L);
        concurrent.setSettingsKey("ops-connection");
        concurrent.setJson("""
                {"addresses":["existing:9876"],"currentNamesrv":"existing:9876",\
                "useVIPChannel":false,"useTLS":false}
                """);
        when(settingsMapper.selectOne(any())).thenReturn(null, concurrent);
        doThrow(new DuplicateKeyException("uk_settings_key"))
                .when(settingsMapper).insert(any(RmqSettings.class));
        AtomicInteger updaterCalls = new AtomicInteger();

        OpsConnectionSettings updated = repository.update(settings -> {
            updaterCalls.incrementAndGet();
            return new OpsConnectionSettings(List.of(settings.currentNamesrv(), "added:9876"),
                    settings.currentNamesrv(), true, false);
        }, () -> new OpsConnectionSettings(List.of("default:9876"), "default:9876", false, false),
                ignored -> { });

        assertThat(updaterCalls).hasValue(2);
        assertThat(updated.addresses()).containsExactly("existing:9876", "added:9876");
        verify(settingsMapper).updateById(argThat((RmqSettings entity) -> entity == concurrent
                && entity.getJson().contains("\"currentNamesrv\":\"existing:9876\"")
                && entity.getJson().contains("\"added:9876\"")));
    }

    @Test
    void repositoryQueriesOnlyTheOpsConnectionSettingsKey() {
        when(settingsMapper.selectOne(any())).thenReturn(null);

        repository.load();

        ArgumentCaptor<QueryWrapper<RmqSettings>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(settingsMapper).selectOne(captor.capture());
        QueryWrapper<RmqSettings> query = captor.getValue();
        query.getCustomSqlSegment();
        assertThat(query.getParamNameValuePairs()).containsValue("ops-connection");
    }
}
