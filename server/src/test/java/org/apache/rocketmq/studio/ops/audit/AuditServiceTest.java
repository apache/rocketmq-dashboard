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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditRepository);
    }

    @Test
    void rejectsOutOfRangePagination() {
        assertThatThrownBy(() -> service.queryLogs(0, 20, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> service.queryLogs(1, 101, null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> service.queryLogs(1, 20, null, null, null, null,
                "2026-02-01", "2026-01-01", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startDate");
    }

    @Test
    void rejectsCleanupWindowsOutsideAllowedBounds() {
        assertThatThrownBy(() -> service.cleanupLogs(0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("beforeDays");
        assertThatThrownBy(() -> service.cleanupLogs(366))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("beforeDays");
    }

    @Test
    void refusesExportWhenRecordsExceedTheCap() {
        PageResult<AuditRecordVO> oversized = PageResult.of(
                List.of(), 10_001, 1, 10_000);
        when(auditRepository.findPage(null, null, null, null, null, null, null, 1, 10_000))
                .thenReturn(oversized);

        assertThatThrownBy(() -> service.exportLogs(null, null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maximum");
    }
}
