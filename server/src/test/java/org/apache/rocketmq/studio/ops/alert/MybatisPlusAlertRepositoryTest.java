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
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertNotificationOutboxMapper;
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
import java.util.Map;
import java.time.LocalDateTime;
import java.util.Optional;

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

    @Mock
    private RmqAlertNotificationOutboxMapper notificationOutboxMapper;

    @InjectMocks
    private MybatisPlusAlertRepository repository;

    @Test
    void replaceRuleShouldReportAConcurrentDeleteTest() {
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
    void insertRuleShouldCanonicalizeChannelsBeforePersistenceTest() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(1L)
                .name("Lag")
                .channels(Arrays.asList(" email ", null, "", "sms", "email", " sms "))
                .build();

        repository.insertRule(rule);

        verify(ruleMapper).insert(argThat((RmqAlertRule entity) ->
                entity != null && "email,sms".equals(entity.getChannels())));
    }

    @Test
    void findAllRulesShouldCanonicalizeLegacyStoredChannelsTest() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(1L);
        entity.setName("Lag");
        entity.setChannels(" email ,,sms,email, sms ");
        when(ruleMapper.selectList(any())).thenReturn(List.of(entity));

        assertThat(repository.findAllRules()).singleElement()
                .satisfies(rule -> assertThat(rule.getChannels()).containsExactly("email", "sms"));
    }

    @Test
    void findRulesPageShouldIncludeLegacyBusinessRulesAndApplyFiltersTest() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(1L);
        entity.setName("Consumer lag");
        entity.setMetric("consumer.lag.total");
        Page<RmqAlertRule> page = new Page<>(2, 10);
        page.setRecords(List.of(entity));
        page.setTotal(11);
        when(ruleMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<AlertRuleVO> result = repository.findRulesPage(
                new AlertRuleQuery(AlertDomain.BUSINESS, "lag", true, 2, 10));

        assertThat(result.getItems()).extracting(AlertRuleVO::getId).containsExactly(1L);
        assertThat(result.getTotal()).isEqualTo(11);
        verify(ruleMapper).selectPage(any(Page.class), argThat(MybatisPlusAlertRepositoryTest::hasBusinessRulePageFilters));
    }

    @Test
    void findRuleByIdShouldReturnOnlyTheRequestedRuleTest() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(7L);
        entity.setName("Lag");
        when(ruleMapper.selectById(7L)).thenReturn(entity);

        assertThat(repository.findRuleById(7L)).map(AlertRuleVO::getId).contains(7L);
        assertThat(repository.findRuleById(null)).isEqualTo(Optional.empty());
        verify(ruleMapper).selectById(7L);
    }

    @Test
    void findRulesByIdsShouldUseBatchLookupTest() {
        RmqAlertRule entity = new RmqAlertRule();
        entity.setId(7L);
        entity.setName("Lag");
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        assertThat(repository.findRulesByIds(List.of(7L, 8L)))
                .extracting(AlertRuleVO::getId)
                .containsExactly(7L);
        assertThat(repository.findRulesByIds(List.of())).isEmpty();
        verify(ruleMapper).selectList(any(Wrapper.class));
    }

    @Test
    void acknowledgeAlertShouldReportUpdateOutcomeWithoutInsertingTest() {
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
    void findAlertsShouldNormalizeLevelIndependentlyOfDefaultLocaleTest() {
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

    @Test
    void saveAlertShouldPersistCanonicalScopeLabelsTest() {
        SystemAlertVO alert = SystemAlertVO.builder().level(org.apache.rocketmq.studio.common.domain.enums.AlertLevel.warning)
                .labels(Map.of("topic", "orders", "brokerName", "broker-a")).build();

        repository.saveAlert(alert);

        verify(alertMapper).insert(argThat((RmqSystemAlert entity) -> entity != null
                && "{\"brokerName\":\"broker-a\",\"topic\":\"orders\"}".equals(entity.getLabelsJson())));
    }

    @Test
    void deleteAcknowledgedAlertsShouldRemovePendingDeliveriesFirstTest() {
        when(alertMapper.delete(any())).thenReturn(2);

        assertThat(repository.deleteAcknowledgedAlerts()).isEqualTo(2);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(notificationOutboxMapper, alertMapper);
        order.verify(notificationOutboxMapper).deleteForAcknowledgedAlerts();
        order.verify(alertMapper).delete(any());
    }

    @Test
    void findAlertsPageShouldApplyScopeLabelAndTimeFiltersTest() {
        when(alertMapper.selectPage(any(Page.class), any())).thenReturn(new Page<RmqSystemAlert>(1, 20));
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 2, 0, 0);

        repository.findAlertsPage(new SystemAlertQuery(null, null, "local", null,
                "brokerName", "broker-a", from, to, 1, 20));

        verify(alertMapper).selectPage(any(Page.class), argThat(MybatisPlusAlertRepositoryTest::hasScopeLabelAndTimeFilters));
    }

    @Test
    void findAlertsPageShouldAllowOmittedOptionalFiltersTest() {
        when(alertMapper.selectPage(any(Page.class), any())).thenReturn(new Page<RmqSystemAlert>(1, 20));

        repository.findAlertsPage(new SystemAlertQuery(null, null, null, null,
                null, null, null, null, 1, 20));

        verify(alertMapper).selectPage(any(Page.class), any());
    }

    private static boolean hasScopeLabelAndTimeFilters(Wrapper<RmqSystemAlert> query) {
        if (!(query instanceof QueryWrapper<?> queryWrapper)) {
            return false;
        }
        String sql = queryWrapper.getCustomSqlSegment();
        return sql.contains("JSON_CONTAINS(labels_json, JSON_OBJECT")
                && queryWrapper.getParamNameValuePairs().containsValue("brokerName")
                && queryWrapper.getParamNameValuePairs().containsValue("broker-a")
                && queryWrapper.getParamNameValuePairs().containsValue(LocalDateTime.of(2026, 8, 1, 0, 0))
                && queryWrapper.getParamNameValuePairs().containsValue(LocalDateTime.of(2026, 8, 2, 0, 0));
    }

    private static boolean hasInfoLevelParameter(Wrapper<RmqSystemAlert> query) {
        if (!(query instanceof QueryWrapper<?> queryWrapper)) {
            return false;
        }
        queryWrapper.getCustomSqlSegment();
        return queryWrapper.getParamNameValuePairs().containsValue("info");
    }

    private static boolean hasBusinessRulePageFilters(Wrapper<RmqAlertRule> query) {
        if (!(query instanceof QueryWrapper<?> queryWrapper)) {
            return false;
        }
        String sql = queryWrapper.getCustomSqlSegment();
        return sql.contains("domain IS NULL")
                && sql.contains("domain =")
                && sql.contains("name LIKE")
                && sql.contains("metric LIKE")
                && queryWrapper.getParamNameValuePairs().containsValue(AlertDomain.BUSINESS.name())
                && queryWrapper.getParamNameValuePairs().containsValue("%lag%")
                && queryWrapper.getParamNameValuePairs().containsValue(true);
    }
}
