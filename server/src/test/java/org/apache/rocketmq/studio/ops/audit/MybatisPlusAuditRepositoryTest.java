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
package org.apache.rocketmq.studio.ops.audit;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusAuditRepositoryTest {

    @Mock
    private RmqOperationAuditMapper auditMapper;

    @InjectMocks
    private MybatisPlusAuditRepository repository;

    @Test
    void findPageUsesMapperPaginationAndPreservesAuditContextTest() {
        RmqOperationAudit entity = new RmqOperationAudit();
        entity.setId(42L);
        entity.setOperation("DELETE_TOPIC");
        entity.setResourceType("TOPIC");
        entity.setResourceName("orders");
        entity.setClusterId("prod-cn");
        entity.setDetail("delete requested");
        entity.setResult("FAILED");
        entity.setErrorMessage("denied");
        entity.setGmtCreate(LocalDateTime.of(2026, 8, 4, 10, 15));
        Page<RmqOperationAudit> mapperPage = new Page<RmqOperationAudit>(2, 25)
                .setRecords(List.of(entity))
                .setTotal(126);
        when(auditMapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mapperPage);

        PageResult<AuditRecordVO> result = repository.findPage(
                "orders", "DELETE_TOPIC", "TOPIC", "prod-cn", null, null, "FAILED", 2, 25);

        ArgumentCaptor<IPage<RmqOperationAudit>> pageCaptor = ArgumentCaptor.forClass(IPage.class);
        ArgumentCaptor<Wrapper<RmqOperationAudit>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectPage(pageCaptor.capture(), queryCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(2);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(25);
        assertThat(result.getTotal()).isEqualTo(126);
        AuditRecordVO record = result.getItems().get(0);
        assertThat(record.getId()).isEqualTo(42L);
        assertThat(record.getResourceType()).isEqualTo("TOPIC");
        assertThat(record.getClusterId()).isEqualTo("prod-cn");
        assertThat(record.getErrorMessage()).isEqualTo("denied");
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("operation", "resource_type", "cluster_id", "result", "gmt_create", "id");
    }

    @Test
    void findFilterOptionsPreservesPersistedValuesAndCachesTheResultTest() {
        when(auditMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(
                Map.of("operation", "DELETE_TOPIC", "resource_type", "TOPIC",
                        "cluster_id", "prod-cn", "result", "SUCCESS"),
                Map.of("operation", " CREATE_TOPIC ", "resource_type", "GROUP",
                        "cluster_id", "prod-sh", "result", "FAILED"),
                Map.of("operation", "DELETE_TOPIC", "resource_type", "TOPIC",
                        "cluster_id", "", "result", "PARTIAL")));

        AuditFilterOptionsVO options = repository.findFilterOptions();
        AuditFilterOptionsVO cachedOptions = repository.findFilterOptions();

        assertThat(options.getOperationTypes()).containsExactly(" CREATE_TOPIC ", "DELETE_TOPIC");
        assertThat(options.getResourceTypes()).containsExactly("GROUP", "TOPIC");
        assertThat(options.getClusterIds()).containsExactly("prod-cn", "prod-sh");
        assertThat(options.getResults()).containsExactly("FAILED", "PARTIAL", "SUCCESS");
        assertThat(cachedOptions).isSameAs(options);
        ArgumentCaptor<Wrapper<RmqOperationAudit>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectMaps(queryCaptor.capture());
        assertThat(((QueryWrapper<RmqOperationAudit>) queryCaptor.getValue()).getSqlSelect())
                .contains("operation", "resource_type", "cluster_id", "result");
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("GROUP BY operation,resource_type,cluster_id,result");
    }

    @Test
    void deleteBeforeShouldUseBoundedIdBatchesTest() {
        RmqOperationAudit audit1 = auditRecord(1L);
        RmqOperationAudit audit2 = auditRecord(2L);
        RmqOperationAudit audit3 = auditRecord(3L);
        when(auditMapper.selectList(any()))
                .thenReturn(List.of(audit1, audit2), List.of(audit3));
        when(auditMapper.deleteByIds(List.of(1L, 2L))).thenReturn(2);
        when(auditMapper.deleteByIds(List.of(3L))).thenReturn(1);

        int deleted = repository.deleteBefore(LocalDateTime.of(2026, 8, 1, 0, 0), 2, 5);

        ArgumentCaptor<Wrapper<RmqOperationAudit>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper, times(2)).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0).getSqlSegment())
                .contains("gmt_create", "ORDER BY gmt_create ASC,id ASC", "LIMIT 2");
        verify(auditMapper).deleteByIds(List.of(1L, 2L));
        verify(auditMapper).deleteByIds(List.of(3L));
        verify(auditMapper, never()).delete(any());
        assertThat(deleted).isEqualTo(3);
    }

    @Test
    void deleteBeforeShouldStopAfterConfiguredMaxBatchesTest() {
        when(auditMapper.selectList(any()))
                .thenReturn(List.of(auditRecord(1L), auditRecord(2L)))
                .thenReturn(List.of(auditRecord(3L), auditRecord(4L)))
                .thenReturn(List.of(auditRecord(5L), auditRecord(6L)));
        when(auditMapper.deleteByIds(List.of(1L, 2L))).thenReturn(2);
        when(auditMapper.deleteByIds(List.of(3L, 4L))).thenReturn(2);

        int deleted = repository.deleteBefore(LocalDateTime.of(2026, 8, 1, 0, 0), 2, 2);

        verify(auditMapper, times(2)).selectList(any());
        verify(auditMapper).deleteByIds(List.of(1L, 2L));
        verify(auditMapper).deleteByIds(List.of(3L, 4L));
        assertThat(deleted).isEqualTo(4);
    }

    @Test
    void saveInvalidatesCachedFilterOptionsTest() {
        when(auditMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());

        repository.findFilterOptions();
        repository.save(AuditRecordVO.builder().operationType("CREATE_TOPIC").build());
        repository.findFilterOptions();

        verify(auditMapper, org.mockito.Mockito.times(2)).selectMaps(any(Wrapper.class));
        verify(auditMapper).insert(any(RmqOperationAudit.class));
    }

    private static RmqOperationAudit auditRecord(Long id) {
        RmqOperationAudit audit = new RmqOperationAudit();
        audit.setId(id);
        return audit;
    }

    @Test
    void summarizeAggregatesResultCountsInOneGroupByAndCountsOperatorsInSqlTest() {
        RmqOperationAudit latest = new RmqOperationAudit();
        latest.setGmtCreate(LocalDateTime.of(2026, 8, 17, 12, 0));

        when(auditMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(
                        row("result", "SUCCESS", "result_count", 5L),
                        row("result", "FAILED", "result_count", 2L),
                        row("result", "PARTIAL", "result_count", 1L)))
                .thenReturn(List.of(row("operator_count", 3L)))
                .thenReturn(List.of(
                        row("bucket_name", "DELETE_TOPIC", "bucket_count", 4L),
                        row("bucket_name", "CREATE_TOPIC", "bucket_count", 4L)))
                .thenReturn(List.of(row("bucket_name", "TOPIC", "bucket_count", 8L)));
        when(auditMapper.selectList(any(Wrapper.class))).thenReturn(List.of(latest));

        AuditSummaryVO summary = repository.summarize(
                null, null, null, null, null, null, null);

        assertThat(summary.getTotal()).isEqualTo(8);
        assertThat(summary.getSuccessful()).isEqualTo(5);
        assertThat(summary.getFailed()).isEqualTo(2);
        assertThat(summary.getPartial()).isEqualTo(1);
        assertThat(summary.getUniqueOperators()).isEqualTo(3);
        assertThat(summary.getLatestAt()).isEqualTo(LocalDateTime.of(2026, 8, 17, 12, 0));
        // Equal-count buckets fall back to name ordering for a stable dashboard.
        assertThat(summary.getByOperation()).extracting(AuditSummaryBucketVO::getName)
                .containsExactly("CREATE_TOPIC", "DELETE_TOPIC");
        assertThat(summary.getByResourceType()).extracting(AuditSummaryBucketVO::getName)
                .containsExactly("TOPIC");

        // The result aggregation no longer issues one COUNT(*) per outcome.
        verify(auditMapper, never()).selectCount(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<RmqOperationAudit>> mapsCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper, times(4)).selectMaps(mapsCaptor.capture());
        List<QueryWrapper<RmqOperationAudit>> queries = mapsCaptor.getAllValues().stream()
                .map(wrapper -> (QueryWrapper<RmqOperationAudit>) wrapper)
                .toList();
        assertThat(queries.get(0).getSqlSelect()).contains("COUNT(*)");
        assertThat(queries.get(0).getSqlSegment()).contains("GROUP BY result");
        assertThat(queries.get(1).getSqlSelect()).contains("COUNT(DISTINCT operator)");
        assertThat(queries.get(2).getSqlSegment()).contains("GROUP BY operation");
        assertThat(queries.get(3).getSqlSegment()).contains("GROUP BY resource_type");
    }

    @Test
    void summarizeOrdersHotspotsByCountThenNameAndCapsAtTheDashboardLimitTest() {
        // Mirrors the top-N list rendered by AuditSummaryCards on the dashboard.
        assertThat(MybatisPlusAuditRepository.HOTSPOT_LIMIT).isEqualTo(5);

        when(auditMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(row("operator_count", 0L)))
                .thenReturn(List.of(
                        row("bucket_name", "op-a", "bucket_count", 10L),
                        row("bucket_name", "op-b", "bucket_count", 7L),
                        row("bucket_name", "op-c", "bucket_count", 7L),
                        row("bucket_name", "op-d", "bucket_count", 3L),
                        row("bucket_name", "op-e", "bucket_count", 3L),
                        row("bucket_name", "op-f", "bucket_count", 3L),
                        row("bucket_name", "op-g", "bucket_count", 1L)))
                .thenReturn(List.of());
        when(auditMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        AuditSummaryVO summary = repository.summarize(
                null, null, null, null, null, null, null);

        assertThat(summary.getByOperation()).extracting(AuditSummaryBucketVO::getName)
                .containsExactly("op-a", "op-b", "op-c", "op-d", "op-e");
        assertThat(summary.getByOperation()).extracting(AuditSummaryBucketVO::getCount)
                .containsExactly(10L, 7L, 7L, 3L, 3L);
    }

    @Test
    void summarizeReadsAggregateLabelsCaseInsensitivelyTest() {
        // JDBC drivers may return result-set label casing differently; the mapping must not care.
        when(auditMapper.selectMaps(any(Wrapper.class)))
                .thenReturn(List.of(row("RESULT", "SUCCESS", "RESULT_COUNT", 2L)))
                .thenReturn(List.of(row("Operator_Count", 1L)))
                .thenReturn(List.of(row("BUCKET_NAME", "TOPIC", "BUCKET_COUNT", 2L)))
                .thenReturn(List.of(row("Bucket_Name", "GROUP", "bucket_count", 2L)));
        when(auditMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        AuditSummaryVO summary = repository.summarize(
                null, null, null, null, null, null, null);

        assertThat(summary.getSuccessful()).isEqualTo(2);
        assertThat(summary.getUniqueOperators()).isEqualTo(1);
        assertThat(summary.getByOperation()).extracting(
                AuditSummaryBucketVO::getName, AuditSummaryBucketVO::getCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("TOPIC", 2L));
        assertThat(summary.getByResourceType()).extracting(
                AuditSummaryBucketVO::getName, AuditSummaryBucketVO::getCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("GROUP", 2L));
    }

    @Test
    void summarizeAppliesEveryFilterToEachAggregateQueryTest() {
        when(auditMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of());
        when(auditMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        LocalDateTime startDate = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(2026, 8, 31, 23, 59);
        repository.summarize("orders", "DELETE_TOPIC", "TOPIC", "prod-cn",
                startDate, endDate, "SUCCESS");

        List<Wrapper<RmqOperationAudit>> queries = new java.util.ArrayList<>();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<RmqOperationAudit>> mapsCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper, times(4)).selectMaps(mapsCaptor.capture());
        queries.addAll(mapsCaptor.getAllValues());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<RmqOperationAudit>> listCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectList(listCaptor.capture());
        queries.add(listCaptor.getValue());

        for (Wrapper<RmqOperationAudit> query : queries) {
            String segment = query.getSqlSegment();
            assertThat(segment).contains("operator LIKE", "resource_name LIKE", "detail LIKE");
            assertThat(segment).contains("operation =", "resource_type =", "cluster_id =");
            assertThat(segment).contains("result =", "gmt_create >=", "gmt_create <=");
        }
    }

    /** Builds a {@code Map<String, Object>} row so mocked result maps keep an explicit type. */
    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put((String) keyValues[i], keyValues[i + 1]);
        }
        return result;
    }

}
