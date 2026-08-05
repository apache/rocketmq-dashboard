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

import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    @AfterEach
    void clearAuthenticatedUser() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void recordShouldCaptureAuthenticatedOperator() {
        AuthenticatedUserContext.setUsername("operator-user");

        auditService.record("CREATE", "topic-a", "created topic", "SUCCESS");

        ArgumentCaptor<AuditRecordVO> captor = ArgumentCaptor.forClass(AuditRecordVO.class);
        verify(auditRepository).save(captor.capture());
        assertThat(captor.getValue().getOperator()).isEqualTo("operator-user");
    }

    @Test
    void queryLogsDelegatesPaginationAndFiltersToRepository() {
        AuditRecordVO record = AuditRecordVO.builder().operationType("CREATE").build();
        when(auditRepository.findPage(eq("topic-a"), eq("CREATE"), isNull(), isNull(), eq("SUCCESS"),
                eq(2), eq(20))).thenReturn(PageResult.of(List.of(record), 21, 2, 20));

        PageResult<AuditRecordVO> result = auditService.queryLogs(
                2, 20, "topic-a", "CREATE", null, null, "SUCCESS");

        assertThat(result.getItems()).containsExactly(record);
        assertThat(result.getTotal()).isEqualTo(21);
        verify(auditRepository).findPage(eq("topic-a"), eq("CREATE"), isNull(), isNull(), eq("SUCCESS"),
                eq(2), eq(20));
    }

    @Test
    void queryLogsParsesDateRangeBeforeDelegating() {
        when(auditRepository.findPage(isNull(), isNull(), any(LocalDateTime.class), any(LocalDateTime.class),
                isNull(), eq(1), eq(10))).thenReturn(PageResult.empty(1, 10));

        auditService.queryLogs(1, 10, null, null, "2026-08-01", "2026-08-02", null);

        ArgumentCaptor<LocalDateTime> start = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> end = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).findPage(isNull(), isNull(), start.capture(), end.capture(), isNull(), eq(1), eq(10));
        assertThat(start.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        assertThat(end.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 2, 23, 59, 59, 999_999_999));
    }

    @Test
    void queryLogsRejectsInvalidPageBounds() {
        assertThatThrownBy(() -> auditService.queryLogs(0, 10, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("page must be greater than 0");
        assertThatThrownBy(() -> auditService.queryLogs(1, 101, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");
    }

    @Test
    void queryLogsRejectsInvalidDateRange() {
        assertThatThrownBy(() -> auditService.queryLogs(1, 10, null, null, "2026-08-02", "2026-08-01", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("startDate must not be after endDate");
    }

    @Test
    void exportLogsIncludesPersistedAuditContextAndEscapesCsvCells() {
        AuditRecordVO record = AuditRecordVO.builder()
                .timestamp(LocalDateTime.of(2026, 8, 1, 9, 30))
                .operator("=cmd")
                .operationType("DELETE")
                .resourceType("TOPIC")
                .target("topic,a")
                .clusterId("prod-cn")
                .detail("removed \"topic\"")
                .result("FAILED")
                .errorMessage("=denied")
                .build();
        when(auditRepository.findPage(eq("topic"), eq("DELETE"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("FAILED"), eq(1), eq(10_000)))
                .thenReturn(PageResult.of(List.of(record), 1, 1, 10_000));

        String csv = auditService.exportLogs("topic", "DELETE", "2026-08-01", "2026-08-02", "FAILED");

        assertThat(csv).contains("resourceType,target,clusterId,detail,result,errorMessage")
                .contains("\"'=cmd\",\"DELETE\",\"TOPIC\",\"topic,a\",\"prod-cn\"")
                .contains("\"'=denied\"");
    }

    @Test
    void exportLogsRejectsResultsBeyondBound() {
        when(auditRepository.findPage(isNull(), isNull(), isNull(), isNull(), isNull(), eq(1), eq(10_000)))
                .thenReturn(PageResult.of(List.of(), 10_001, 1, 10_000));

        assertThatThrownBy(() -> auditService.exportLogs(null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Audit log export exceeds the maximum of 10000 records; narrow the filters");
    }

    @Test
    void cleanupLogsRejectsNonPositiveRetention() {
        assertThatThrownBy(() -> auditService.cleanupLogs(0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("beforeDays must be greater than 0");
    }
}
