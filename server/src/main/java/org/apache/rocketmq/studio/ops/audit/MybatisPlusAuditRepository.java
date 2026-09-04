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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** MySQL-backed audit repository (rmq_operation_audit). */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAuditRepository implements AuditRepository {

    private static final long FILTER_OPTIONS_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

    /**
     * Maximum number of hot-spot buckets returned for the byOperation /
     * byResourceType breakdowns. Kept in sync with the dashboard, which
     * renders the top N entries of each breakdown.
     */
    static final int HOTSPOT_LIMIT = 5;

    private final RmqOperationAuditMapper auditMapper;
    private volatile CachedFilterOptions cachedFilterOptions;

    @Override
    public PageResult<AuditRecordVO> findPage(String search, String operationType,
                                              String resourceType, String clusterId,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              String result, int page, int pageSize) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .and(StringUtils.hasText(search), w -> w
                        .like("operator", search)
                        .or().like("resource_name", search)
                        .or().like("detail", search))
                .eq(StringUtils.hasText(operationType), "operation", operationType)
                .eq(StringUtils.hasText(resourceType), "resource_type", resourceType)
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .ge(startDate != null, "gmt_create", startDate)
                .le(endDate != null, "gmt_create", endDate)
                .eq(StringUtils.hasText(result), "result", result)
                .orderByDesc("gmt_create", "id");
        Page<RmqOperationAudit> resultPage = auditMapper.selectPage(
                new Page<>(page, pageSize), query);
        List<AuditRecordVO> records = resultPage.getRecords().stream()
                .map(MybatisPlusAuditRepository::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, resultPage.getTotal(), page, pageSize);
    }

    @Override
    public AuditFilterOptionsVO findFilterOptions() {
        long now = System.nanoTime();
        CachedFilterOptions cached = cachedFilterOptions;
        if (isCacheValid(cached, now)) {
            return cached.options();
        }
        synchronized (this) {
            cached = cachedFilterOptions;
            if (isCacheValid(cached, now)) {
                return cached.options();
            }
            AuditFilterOptionsVO options = loadFilterOptions();
            cachedFilterOptions = new CachedFilterOptions(options, now);
            return options;
        }
    }

    private AuditFilterOptionsVO loadFilterOptions() {
        List<Map<String, Object>> values = auditMapper.selectMaps(
                new QueryWrapper<RmqOperationAudit>()
                        .select("operation", "resource_type", "cluster_id", "result")
                        .groupBy("operation", "resource_type", "cluster_id", "result"));
        return AuditFilterOptionsVO.builder()
                .operationTypes(findDistinctValues(values, "operation"))
                .resourceTypes(findDistinctValues(values, "resource_type"))
                .clusterIds(findDistinctValues(values, "cluster_id"))
                .results(findDistinctValues(values, "result"))
                .build();
    }

    @Override
    public AuditSummaryVO summarize(String search, String operationType, String resourceType,
                                    String clusterId, LocalDateTime startDate, LocalDateTime endDate,
                                    String result) {
        Consumer<QueryWrapper<RmqOperationAudit>> filters = query -> applyFilters(query, search,
                operationType, resourceType, clusterId, startDate, endDate, result);

        // One GROUP BY result query computes total / SUCCESS / FAILED / PARTIAL in a single
        // round trip instead of four separate COUNT(*) statements. Note that when the caller
        // already filters on a specific result, the buckets for the other outcomes are
        // intentionally reported as zero, because those rows are filtered out.
        Map<String, Long> resultCounts = resultCounts(filters);
        long total = resultCounts.values().stream().mapToLong(Long::longValue).sum();

        // COUNT(DISTINCT operator) is evaluated inside the database so matching rows are
        // never materialized into the application just to count operators.
        long uniqueOperators = countDistinctOperators(filters);

        LocalDateTime latestAt = latestOperatedAt(filters);

        return AuditSummaryVO.builder()
                .total(total)
                .successful(resultCounts.getOrDefault("SUCCESS", 0L))
                .failed(resultCounts.getOrDefault("FAILED", 0L))
                .partial(resultCounts.getOrDefault("PARTIAL", 0L))
                .uniqueOperators(uniqueOperators)
                .latestAt(latestAt)
                .byOperation(groupCounts("operation", filters))
                .byResourceType(groupCounts("resource_type", filters))
                .build();
    }

    private Map<String, Long> resultCounts(Consumer<QueryWrapper<RmqOperationAudit>> filters) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .select("result", "COUNT(*) AS result_count")
                .groupBy("result");
        filters.accept(query);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : auditMapper.selectMaps(query)) {
            String key = mapValue(row, "result");
            counts.merge(key, parseCount(row, "result_count"), Long::sum);
        }
        return counts;
    }

    private long countDistinctOperators(Consumer<QueryWrapper<RmqOperationAudit>> filters) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .select("COUNT(DISTINCT operator) AS operator_count")
                .isNotNull("operator");
        filters.accept(query);
        List<Map<String, Object>> rows = auditMapper.selectMaps(query);
        if (rows.isEmpty()) {
            return 0L;
        }
        String value = mapValue(rows.get(0), "operator_count");
        return value.isEmpty() ? 0L : Long.parseLong(value);
    }

    private LocalDateTime latestOperatedAt(Consumer<QueryWrapper<RmqOperationAudit>> filters) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .select("gmt_create").orderByDesc("gmt_create").last("LIMIT 1");
        filters.accept(query);
        List<RmqOperationAudit> rows = auditMapper.selectList(query);
        return rows.isEmpty() ? null : rows.get(0).getGmtCreate();
    }

    private List<AuditSummaryBucketVO> groupCounts(
            String column, Consumer<QueryWrapper<RmqOperationAudit>> filters) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .select(column + " AS bucket_name", "COUNT(*) AS bucket_count")
                .isNotNull(column)
                .groupBy(column);
        filters.accept(query);
        return auditMapper.selectMaps(query).stream()
                .map(row -> AuditSummaryBucketVO.builder()
                        .name(mapValue(row, "bucket_name"))
                        .count(parseCount(row, "bucket_count"))
                        .build())
                .filter(bucket -> StringUtils.hasText(bucket.getName()))
                .sorted((left, right) -> {
                    int countOrder = Long.compare(right.getCount(), left.getCount());
                    return countOrder != 0 ? countOrder : left.getName().compareTo(right.getName());
                })
                .limit(HOTSPOT_LIMIT)
                .toList();
    }

    /**
     * Reads an aggregate column value from a result row using case-insensitive key
     * matching, because JDBC drivers are free to return label casing differently.
     */
    private long parseCount(Map<String, Object> row, String key) {
        String value = mapValue(row, key);
        if (value.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private String mapValue(Map<String, Object> row, String key) {
        return row.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .findFirst()
                .orElse("");
    }

    private void applyFilters(QueryWrapper<RmqOperationAudit> query, String search,
                              String operationType, String resourceType, String clusterId,
                              LocalDateTime startDate, LocalDateTime endDate, String result) {
        query.and(StringUtils.hasText(search), w -> w
                        .like("operator", search)
                        .or().like("resource_name", search)
                        .or().like("detail", search))
                .eq(StringUtils.hasText(operationType), "operation", operationType)
                .eq(StringUtils.hasText(resourceType), "resource_type", resourceType)
                .eq(StringUtils.hasText(clusterId), "cluster_id", clusterId)
                .ge(startDate != null, "gmt_create", startDate)
                .le(endDate != null, "gmt_create", endDate)
                .eq(StringUtils.hasText(result), "result", result);
    }

    @Override
    public void save(AuditRecordVO record) {
        RmqOperationAudit entity = new RmqOperationAudit();
        entity.setOperation(record.getOperationType());
        entity.setResourceType(record.getResourceType() == null ? "GENERAL" : record.getResourceType());
        entity.setResourceName(record.getTarget());
        entity.setClusterId(record.getClusterId());
        entity.setDetail(record.getDetail());
        entity.setResult(record.getResult());
        entity.setErrorMessage(record.getErrorMessage());
        entity.setOperator(record.getOperator());
        LocalDateTime timestamp = record.getTimestamp() == null ? LocalDateTime.now() : record.getTimestamp();
        entity.setGmtCreate(timestamp);
        entity.setGmtModified(timestamp);
        auditMapper.insert(entity);
        cachedFilterOptions = null;
    }

    private static boolean isCacheValid(CachedFilterOptions cached, long now) {
        return cached != null && now - cached.createdAtNanos() < FILTER_OPTIONS_CACHE_TTL_NANOS;
    }

    private record CachedFilterOptions(AuditFilterOptionsVO options, long createdAtNanos) {
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff, int batchSize, int maxBatches) {
        int totalDeleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<RmqOperationAudit> expired = auditMapper.selectList(new QueryWrapper<RmqOperationAudit>()
                    .select("id")
                    .lt("gmt_create", cutoff)
                    .orderByAsc("gmt_create", "id")
                    .last("LIMIT " + batchSize));
            if (expired.isEmpty()) {
                break;
            }
            List<Long> ids = expired.stream()
                    .map(RmqOperationAudit::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (ids.isEmpty()) {
                break;
            }
            int deleted = auditMapper.deleteByIds(ids);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return totalDeleted;
    }

    private List<String> findDistinctValues(List<Map<String, Object>> rows, String column) {
        return rows.stream()
                .map(row -> row.get(column))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private static AuditRecordVO toVO(RmqOperationAudit entity) {
        AuditRecordVO vo = new AuditRecordVO();
        vo.setId(entity.getId());
        vo.setTimestamp(entity.getGmtCreate());
        vo.setOperator(entity.getOperator());
        vo.setOperationType(entity.getOperation());
        vo.setResourceType(entity.getResourceType());
        vo.setTarget(entity.getResourceName());
        vo.setClusterId(entity.getClusterId());
        vo.setDetail(entity.getDetail());
        vo.setResult(entity.getResult());
        vo.setErrorMessage(entity.getErrorMessage());
        return vo;
    }
}
