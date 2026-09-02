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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** MySQL-backed audit repository (rmq_operation_audit). */
@RequiredArgsConstructor
@Repository
public class MybatisPlusAuditRepository implements AuditRepository {

    private static final long FILTER_OPTIONS_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);

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
