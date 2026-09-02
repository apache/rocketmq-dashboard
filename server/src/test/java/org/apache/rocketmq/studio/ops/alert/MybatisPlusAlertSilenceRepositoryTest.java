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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
                .contains("starts_at", "ends_at", "domain IS NULL", "rule_id IS NULL", "instance_id IS NULL")
                .contains("ORDER BY ends_at DESC,id DESC");
        assertThat(query.getParamNameValuePairs())
                .containsValue(now)
                .containsValue(AlertDomain.CLUSTER.name())
                .containsValue(5L)
                .containsValue("local");
    }
}
