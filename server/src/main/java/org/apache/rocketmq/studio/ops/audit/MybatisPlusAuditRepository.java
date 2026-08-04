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
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL-backed audit repository (rmq_operation_audit). The VO's ipAddress has
 * no dedicated column and is not persisted.
 */
@Repository
public class MybatisPlusAuditRepository implements AuditRepository {

    private final RmqOperationAuditMapper auditMapper;

    public MybatisPlusAuditRepository(RmqOperationAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    public List<AuditRecordVO> findAll(String search, String operationType,
                                       LocalDateTime startDate, LocalDateTime endDate,
                                       String result) {
        QueryWrapper<RmqOperationAudit> query = new QueryWrapper<RmqOperationAudit>()
                .and(StringUtils.hasText(search), w -> w
                        .like("operator", search)
                        .or().like("resource_name", search)
                        .or().like("detail", search))
                .eq(StringUtils.hasText(operationType), "operation", operationType)
                .ge(startDate != null, "operated_at", startDate)
                .le(endDate != null, "operated_at", endDate)
                .eq(StringUtils.hasText(result), "result", result)
                .orderByDesc("operated_at");
        return auditMapper.selectList(query).stream()
                .map(MybatisPlusAuditRepository::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public void save(AuditRecordVO record) {
        RmqOperationAudit entity = new RmqOperationAudit();
        entity.setOperation(record.getOperationType());
        entity.setResourceType("GENERAL");
        entity.setResourceName(record.getTarget());
        entity.setDetail(record.getDetail());
        entity.setResult(record.getResult());
        entity.setOperator(record.getOperator());
        entity.setOperatedAt(record.getTimestamp() == null ? LocalDateTime.now() : record.getTimestamp());
        auditMapper.insert(entity);
    }

    @Override
    public int deleteBefore(LocalDateTime cutoff) {
        return Math.toIntExact(auditMapper.delete(
                new QueryWrapper<RmqOperationAudit>().lt("operated_at", cutoff)));
    }

    private static AuditRecordVO toVO(RmqOperationAudit entity) {
        AuditRecordVO vo = new AuditRecordVO();
        vo.setId(entity.getId() == null ? null : String.valueOf(entity.getId()));
        vo.setTimestamp(entity.getOperatedAt());
        vo.setOperator(entity.getOperator());
        vo.setOperationType(entity.getOperation());
        vo.setTarget(entity.getResourceName());
        vo.setDetail(entity.getDetail());
        vo.setResult(entity.getResult());
        return vo;
    }
}
