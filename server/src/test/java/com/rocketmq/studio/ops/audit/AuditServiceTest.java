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
package com.rocketmq.studio.ops.audit;

import com.rocketmq.studio.common.domain.PageResult;
import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
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

    @Test
    void queryLogsShouldReturnFirstPage() {
        AuditRecordVO r1 = AuditRecordVO.builder()
                .operator("admin").operationType("CREATE").target("topic-a").result("SUCCESS").build();
        AuditRecordVO r2 = AuditRecordVO.builder()
                .operator("admin").operationType("DELETE").target("topic-b").result("SUCCESS").build();
        AuditRecordVO r3 = AuditRecordVO.builder()
                .operator("user1").operationType("UPDATE").target("topic-c").result("FAILURE").build();
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Arrays.asList(r1, r2, r3));

        PageResult<AuditRecordVO> result = auditService.queryLogs(1, 2, null, null, null, null, null);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(2);
        assertThat(result.getItems().get(0).getOperationType()).isEqualTo("CREATE");
        assertThat(result.getItems().get(1).getOperationType()).isEqualTo("DELETE");
    }

    @Test
    void queryLogsShouldReturnSecondPage() {
        AuditRecordVO r1 = AuditRecordVO.builder().operationType("CREATE").build();
        AuditRecordVO r2 = AuditRecordVO.builder().operationType("DELETE").build();
        AuditRecordVO r3 = AuditRecordVO.builder().operationType("UPDATE").build();
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Arrays.asList(r1, r2, r3));

        PageResult<AuditRecordVO> result = auditService.queryLogs(2, 2, null, null, null, null, null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getItems().get(0).getOperationType()).isEqualTo("UPDATE");
    }

    @Test
    void queryLogsShouldReturnEmptyPageWhenNoRecords() {
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        PageResult<AuditRecordVO> result = auditService.queryLogs(1, 10, null, null, null, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    void queryLogsShouldReturnEmptyWhenPageExceedsTotal() {
        AuditRecordVO r1 = AuditRecordVO.builder().operationType("CREATE").build();
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(r1));

        PageResult<AuditRecordVO> result = auditService.queryLogs(5, 10, null, null, null, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void queryLogsShouldRejectNonPositivePage() {
        assertThatThrownBy(() -> auditService.queryLogs(0, 10, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("page must be greater than 0")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void queryLogsShouldRejectNonPositivePageSize() {
        assertThatThrownBy(() -> auditService.queryLogs(1, 0, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be greater than 0")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void queryLogsShouldAvoidOffsetOverflow() {
        AuditRecordVO record = AuditRecordVO.builder().operationType("CREATE").build();
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(record));

        PageResult<AuditRecordVO> result = auditService.queryLogs(
                Integer.MAX_VALUE, Integer.MAX_VALUE, null, null, null, null, null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void queryLogsShouldPassSearchFilterToRepository() {
        when(auditRepository.findAll(eq("topic-a"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        auditService.queryLogs(1, 10, "topic-a", null, null, null, null);

        verify(auditRepository).findAll(eq("topic-a"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void queryLogsShouldPassOperationTypeFilterToRepository() {
        when(auditRepository.findAll(isNull(), eq("CREATE"), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        auditService.queryLogs(1, 10, null, "CREATE", null, null, null);

        verify(auditRepository).findAll(isNull(), eq("CREATE"), isNull(), isNull(), isNull());
    }

    @Test
    void queryLogsShouldPassResultFilterToRepository() {
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), eq("SUCCESS")))
                .thenReturn(Collections.emptyList());

        auditService.queryLogs(1, 10, null, null, null, null, "SUCCESS");

        verify(auditRepository).findAll(isNull(), isNull(), isNull(), isNull(), eq("SUCCESS"));
    }

    @Test
    void queryLogsShouldParseDateRange() {
        when(auditRepository.findAll(isNull(), isNull(), any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(Collections.emptyList());

        auditService.queryLogs(1, 10, null, null, "2025-01-01", "2025-01-31", null);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).findAll(isNull(), isNull(), startCaptor.capture(), endCaptor.capture(), isNull());

        assertThat(startCaptor.getValue()).isEqualTo(LocalDateTime.of(2025, 1, 1, 0, 0, 0));
        assertThat(endCaptor.getValue().getYear()).isEqualTo(2025);
        assertThat(endCaptor.getValue().getMonthValue()).isEqualTo(1);
        assertThat(endCaptor.getValue().getDayOfMonth()).isEqualTo(31);
        assertThat(endCaptor.getValue().getHour()).isEqualTo(23);
        assertThat(endCaptor.getValue().getMinute()).isEqualTo(59);
    }

    @Test
    void queryLogsShouldHandleInvalidDateFormat() {
        when(auditRepository.findAll(isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        PageResult<AuditRecordVO> result = auditService.queryLogs(1, 10, null, null, "invalid-date", null, null);

        assertThat(result).isNotNull();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void cleanupLogsShouldDeleteOldRecords() {
        when(auditRepository.deleteBefore(any(LocalDateTime.class))).thenReturn(42);

        int result = auditService.cleanupLogs(30);

        assertThat(result).isEqualTo(42);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).deleteBefore(captor.capture());

        LocalDateTime cutoff = captor.getValue();
        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        assertThat(cutoff).isCloseTo(expected, org.assertj.core.api.Assertions.within(2, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void cleanupLogsShouldReturnZeroWhenNoOldRecords() {
        when(auditRepository.deleteBefore(any(LocalDateTime.class))).thenReturn(0);

        int result = auditService.cleanupLogs(90);

        assertThat(result).isZero();
    }

    @Test
    void cleanupLogsShouldRejectNonPositiveRetention() {
        assertThatThrownBy(() -> auditService.cleanupLogs(0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("beforeDays must be greater than 0")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        assertThatThrownBy(() -> auditService.cleanupLogs(-1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("beforeDays must be greater than 0");
    }

    @Test
    void queryLogsShouldHandleAllFiltersTogether() {
        when(auditRepository.findAll(eq("admin"), eq("DELETE"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("FAILURE")))
                .thenReturn(Collections.emptyList());

        auditService.queryLogs(1, 10, "admin", "DELETE", "2025-06-01", "2025-06-30", "FAILURE");

        verify(auditRepository).findAll(eq("admin"), eq("DELETE"), any(LocalDateTime.class),
                any(LocalDateTime.class), eq("FAILURE"));
    }
}
