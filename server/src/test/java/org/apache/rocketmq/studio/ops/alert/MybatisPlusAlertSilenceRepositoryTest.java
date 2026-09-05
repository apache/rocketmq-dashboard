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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqAlertSilence;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertSilenceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusAlertSilenceRepositoryTest {

    @Mock
    private RmqAlertSilenceMapper mapper;

    @Test
    void findPageShouldUseDatabasePaginationAndStableInventoryOrderingTest() {
        MybatisPlusAlertSilenceRepository repository = new MybatisPlusAlertSilenceRepository(
                mapper, new ObjectMapper());
        RmqAlertSilence entity = new RmqAlertSilence();
        entity.setId(9L);
        entity.setReason("maintenance");
        Page<RmqAlertSilence> mapperPage = new Page<RmqAlertSilence>(3, 20)
                .setRecords(List.of(entity))
                .setTotal(41);
        when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mapperPage);

        PageResult<AlertSilenceVO> result = repository.findPage(3, 20);

        ArgumentCaptor<IPage<RmqAlertSilence>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RmqAlertSilence>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(3);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(20);
        assertThat(result.getTotal()).isEqualTo(41);
        assertThat(result.getItems()).singleElement()
                .satisfies(silence -> assertThat(silence.getReason()).isEqualTo("maintenance"));
        QueryWrapper<RmqAlertSilence> query = (QueryWrapper<RmqAlertSilence>) queryCaptor.getValue();
        query.getCustomSqlSegment();
        assertThat(query.getSqlSegment()).contains("ORDER BY ends_at DESC,id DESC");
        verify(mapper, never()).selectList(any());
    }

    @Test
    void findActiveCandidatesShouldFilterTimeAndWildcardScopeInSqlTest() {
        MybatisPlusAlertSilenceRepository repository = new MybatisPlusAlertSilenceRepository(
                mapper, new ObjectMapper());
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 10, 0);
        RmqAlertSilence entity = new RmqAlertSilence();
        entity.setId(7L);
        entity.setDomain(AlertDomain.CLUSTER.name());
        entity.setRuleId(5L);
        entity.setInstanceId("local");
        entity.setStartsAt(now.minusMinutes(1));
        entity.setEndsAt(now.plusMinutes(1));
        entity.setCreatedBy("admin");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        List<AlertSilenceVO> candidates = repository.findActiveCandidates(
                AlertDomain.CLUSTER, 5L, "local", now);

        assertThat(candidates).singleElement()
                .satisfies(silence -> assertThat(silence.getId()).isEqualTo(7L));
        ArgumentCaptor<Wrapper<RmqAlertSilence>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(queryCaptor.capture());
        QueryWrapper<RmqAlertSilence> query = (QueryWrapper<RmqAlertSilence>) queryCaptor.getValue();
        query.getCustomSqlSegment();
        assertThat(query.getSqlSegment())
                .contains("starts_at", "ends_at", "recurrence", "recurrence_until", "domain IS NULL",
                        "rule_id IS NULL", "instance_id IS NULL")
                .contains("ORDER BY ends_at DESC,id DESC");
        assertThat(query.getParamNameValuePairs())
                .containsValue(now)
                .containsValue(AlertDomain.CLUSTER.name())
                .containsValue(5L)
                .containsValue("local");
    }

    @Test
    void saveShouldPersistRecurringScheduleFieldsTest() {
        MybatisPlusAlertSilenceRepository repository = new MybatisPlusAlertSilenceRepository(
                mapper, new ObjectMapper());
        AlertSilenceVO silence = AlertSilenceVO.builder()
                .domain(AlertDomain.BUSINESS).startsAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .endsAt(LocalDateTime.of(2026, 9, 1, 11, 0)).recurrence(AlertSilenceRecurrence.WEEKLY)
                .timeZone("Asia/Shanghai").recurrenceDays(Set.of(5, 1))
                .recurrenceUntil(LocalDateTime.of(2026, 10, 1, 0, 0)).createdBy("admin").build();
        when(mapper.insert(any(RmqAlertSilence.class))).thenAnswer(invocation -> {
            RmqAlertSilence entity = invocation.getArgument(0);
            entity.setId(31L);
            return 1;
        });

        AlertSilenceVO saved = repository.save(silence);

        ArgumentCaptor<RmqAlertSilence> captor = ArgumentCaptor.forClass(RmqAlertSilence.class);
        verify(mapper).insert(captor.capture());
        assertThat(saved.getId()).isEqualTo(31L);
        assertThat(captor.getValue().getRecurrence()).isEqualTo("WEEKLY");
        assertThat(captor.getValue().getTimeZone()).isEqualTo("Asia/Shanghai");
        assertThat(captor.getValue().getRecurrenceDaysJson()).isEqualTo("[1,5]");
        assertThat(captor.getValue().getRecurrenceUntil()).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
    }

    @Test
    void findAllShouldRestoreRecurringScheduleAndLegacyDefaultsTest() {
        MybatisPlusAlertSilenceRepository repository = new MybatisPlusAlertSilenceRepository(
                mapper, new ObjectMapper());
        RmqAlertSilence recurring = new RmqAlertSilence();
        recurring.setId(31L);
        recurring.setRecurrence("WEEKLY");
        recurring.setTimeZone("UTC");
        recurring.setRecurrenceDaysJson("[1,3,5]");
        recurring.setRecurrenceUntil(LocalDateTime.of(2026, 10, 1, 0, 0));
        RmqAlertSilence legacy = new RmqAlertSilence();
        legacy.setId(30L);
        when(mapper.selectList(any())).thenReturn(List.of(recurring, legacy));

        List<AlertSilenceVO> restored = repository.findAll();

        assertThat(restored.get(0).getRecurrence()).isEqualTo(AlertSilenceRecurrence.WEEKLY);
        assertThat(restored.get(0).getRecurrenceDays()).containsExactlyInAnyOrder(1, 3, 5);
        assertThat(restored.get(1).getRecurrence()).isEqualTo(AlertSilenceRecurrence.ONCE);
        assertThat(restored.get(1).getRecurrenceDays()).isEmpty();
    }

    @Test
    void deleteByIdShouldReportWhetherARowWasRemovedTest() {
        RmqAlertSilenceMapper mapper = mock(RmqAlertSilenceMapper.class);
        when(mapper.deleteById(1L)).thenReturn(1);
        when(mapper.deleteById(2L)).thenReturn(0);
        MybatisPlusAlertSilenceRepository repository =
                new MybatisPlusAlertSilenceRepository(mapper, new ObjectMapper());

        assertThat(repository.deleteById(1L)).isTrue();
        assertThat(repository.deleteById(2L)).isFalse();
        verify(mapper).deleteById(1L);
        verify(mapper).deleteById(2L);
    }
}
