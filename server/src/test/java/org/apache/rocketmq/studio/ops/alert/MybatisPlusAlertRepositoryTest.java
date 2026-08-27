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
package org.apache.rocketmq.studio.ops.alert;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertRule;
import org.apache.rocketmq.studio.persistence.entity.RmqSystemAlert;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertRuleMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusAlertRepositoryTest {

    @Mock
    private RmqAlertRuleMapper ruleMapper;

    @Mock
    private RmqSystemAlertMapper alertMapper;

    @InjectMocks
    private MybatisPlusAlertRepository repository;

    @Test
    void replaceRuleShouldReportAConcurrentDelete() {
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).name("Lag").build();
        when(ruleMapper.selectById(1L)).thenReturn(new RmqAlertRule());
        when(ruleMapper.updateById(any(RmqAlertRule.class))).thenReturn(0);

        assertThat(repository.replaceRule(rule)).isFalse();
    }

    @Test
    void findRulePageShouldApplyFiltersOrderingAndDatabasePagination() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(1L);
        entity.setName("High Lag");
        entity.setEnabled(true);
        entity.setChannels("email");
        Page<RmqAlertRule> mapperPage = new Page<RmqAlertRule>(3, 20)
                .setRecords(List.of(entity))
                .setTotal(51);
        when(ruleMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mapperPage);

        PageResult<AlertRuleVO> result = repository.findRulePage("lag", true, 3, 20);

        ArgumentCaptor<IPage<RmqAlertRule>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RmqAlertRule>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ruleMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(51);
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getItems()).singleElement()
                .satisfies(rule -> {
                    assertThat(rule.getId()).isEqualTo(1L);
                    assertThat(rule.getName()).isEqualTo("High Lag");
                    assertThat(rule.isEnabled()).isTrue();
                });
        QueryWrapper<RmqAlertRule> query = (QueryWrapper<RmqAlertRule>) queryCaptor.getValue();
        query.getCustomSqlSegment();
        assertThat(query.getSqlSegment())
                .contains("name", "enabled", "ORDER BY name ASC,id ASC");
        assertThat(query.getParamNameValuePairs())
                .containsValue("%lag%")
                .containsValue(true);
        verify(ruleMapper, never()).selectList(any());
    }

    @Test
    void findRuleByIdShouldUsePrimaryKeyLookupWithoutFullListRead() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(9L);
        entity.setName("High Lag");
        entity.setEnabled(true);
        when(ruleMapper.selectById(9L)).thenReturn(entity);

        assertThat(repository.findRuleById(9L)).hasValueSatisfying(rule ->
                assertThat(rule.getId()).isEqualTo(9L));
        assertThat(repository.findRuleById(null)).isEmpty();
        verify(ruleMapper, never()).selectList(any());
    }

    @Test
    void findRulesByIdsShouldUseBoundedIdQuery() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(1L);
        entity.setName("High Lag");
        entity.setEnabled(true);
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        assertThat(repository.findRulesByIds(List.of(1L, 2L))).singleElement()
                .satisfies(rule -> assertThat(rule.getId()).isEqualTo(1L));
        assertThat(repository.findRulesByIds(null)).isEmpty();
        assertThat(repository.findRulesByIds(List.of())).isEmpty();
    }

    @Test
    void findAlertsShouldNormalizeStoredLevelValues() {
        RmqSystemAlert entity = new RmqSystemAlert();
        entity.setLevel(" WARNING ");
        when(alertMapper.selectList(any())).thenReturn(List.of(entity));

        assertThat(repository.findAlerts(null)).singleElement()
                .satisfies(alert -> assertThat(alert.getLevel()).isEqualTo(
                        org.apache.rocketmq.studio.common.domain.enums.AlertLevel.warning));
    }

    @Test
    void saveRuleShouldCanonicalizeChannelsBeforePersistence() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(1L)
                .name("Lag")
                .channels(Arrays.asList(" email ", null, "", "sms", "email", " sms "))
                .build();

        repository.saveRule(rule);

        verify(ruleMapper).insert(argThat((RmqAlertRule entity) ->
                entity != null && "email,sms".equals(entity.getChannels())));
    }

    @Test
    void findAllRulesShouldCanonicalizeLegacyStoredChannels() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(1L);
        entity.setName("Lag");
        entity.setChannels(" email ,,sms,email, sms ");
        when(ruleMapper.selectList(any())).thenReturn(List.of(entity));

        assertThat(repository.findAllRules()).singleElement()
                .satisfies(rule -> assertThat(rule.getChannels()).containsExactly("email", "sms"));
    }

    @Test
    void acknowledgeAlertShouldReportUpdateOutcomeWithoutInserting() {
        SystemAlertVO deleted = SystemAlertVO.builder().id(1L).acknowledged(true).build();
        SystemAlertVO existing = SystemAlertVO.builder().id(2L).acknowledged(true).build();
        when(alertMapper.updateById(argThat((RmqSystemAlert entity) ->
                entity != null && Long.valueOf(1L).equals(entity.getId()))))
                .thenReturn(0);
        when(alertMapper.updateById(argThat((RmqSystemAlert entity) ->
                entity != null && Long.valueOf(2L).equals(entity.getId()))))
                .thenReturn(1);

        assertThat(repository.acknowledgeAlert(deleted)).isFalse();
        assertThat(repository.acknowledgeAlert(existing)).isTrue();
        verify(alertMapper, never()).insert(any(RmqSystemAlert.class));
    }

    @Test
    void findAlertsShouldNormalizeLevelIndependentlyOfDefaultLocale() {
        when(alertMapper.selectList(any())).thenReturn(List.of());
        Locale originalLocale = Locale.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            repository.findAlerts("INFO");
        } finally {
            Locale.setDefault(originalLocale);
        }

        verify(alertMapper).selectList(argThat(MybatisPlusAlertRepositoryTest::hasInfoLevelParameter));
    }

    private static boolean hasInfoLevelParameter(Wrapper<RmqSystemAlert> query) {
        if (!(query instanceof QueryWrapper<?> queryWrapper)) {
            return false;
        }
        queryWrapper.getCustomSqlSegment();
        return queryWrapper.getParamNameValuePairs().containsValue("info");
    }
}
