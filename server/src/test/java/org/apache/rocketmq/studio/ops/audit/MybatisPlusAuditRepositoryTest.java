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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusAuditRepositoryTest {

    @Mock
    private RmqOperationAuditMapper auditMapper;

    @InjectMocks
    private MybatisPlusAuditRepository repository;

    @Test
    void findPageUsesMapperPaginationAndPreservesAuditContext() {
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
                .contains("operation", "resource_type", "cluster_id", "result",
                        "ORDER BY gmt_create DESC,id DESC");
    }

    @Test
    void findFilterOptionsPreservesPersistedValuesFromOneQuery() {
        when(auditMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(
                Map.of("operation", "DELETE_TOPIC", "resource_type", "TOPIC",
                        "cluster_id", "prod-cn", "result", "SUCCESS"),
                Map.of("operation", " CREATE_TOPIC ", "resource_type", "GROUP",
                        "cluster_id", "prod-sh", "result", "FAILED"),
                Map.of("operation", "DELETE_TOPIC", "resource_type", "TOPIC",
                        "cluster_id", "", "result", "PARTIAL")));

        AuditFilterOptionsVO options = repository.findFilterOptions();

        assertThat(options.getOperationTypes()).containsExactly(" CREATE_TOPIC ", "DELETE_TOPIC");
        assertThat(options.getResourceTypes()).containsExactly("GROUP", "TOPIC");
        assertThat(options.getClusterIds()).containsExactly("prod-cn", "prod-sh");
        assertThat(options.getResults()).containsExactly("FAILED", "PARTIAL", "SUCCESS");
        ArgumentCaptor<Wrapper<RmqOperationAudit>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(auditMapper).selectMaps(queryCaptor.capture());
        assertThat(((QueryWrapper<RmqOperationAudit>) queryCaptor.getValue()).getSqlSelect())
                .contains("operation", "resource_type", "cluster_id", "result");
        assertThat(queryCaptor.getValue().getSqlSegment())
                .contains("GROUP BY operation,resource_type,cluster_id,result");
    }

}
