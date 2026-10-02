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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The audit table is the only growing studio table without a scheduled retention sweep: the
 * keep-forever default must leave it strictly alone, and an opted-in retention has to reuse the
 * bounded delete of the manual cleanup endpoint instead of unbounded growth.
 */
@ExtendWith(MockitoExtension.class)
class AuditRetentionSweepTest {

    private static final int CLEANUP_BATCH_SIZE = 500;
    private static final int CLEANUP_MAX_BATCHES = 20;

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void sweepIsScheduledWithAnHourlyDefault() {
        Scheduled scheduled = sweepMethod().getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${studio.audit.retention-cleanup-interval:PT1H}");
    }

    @Test
    void sweepKeepsEveryRowWhileRetentionIsUnset() throws Exception {
        configureRetention(0);

        assertThat(runSweep()).isZero();

        verifyNoInteractions(auditRepository);
    }

    @Test
    void sweepDeletesRowsOlderThanTheConfiguredRetention() throws Exception {
        configureRetention(90);
        when(auditRepository.deleteBefore(any(LocalDateTime.class), eq(CLEANUP_BATCH_SIZE),
                eq(CLEANUP_MAX_BATCHES))).thenReturn(42);

        assertThat(runSweep()).isEqualTo(42);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).deleteBefore(cutoff.capture(), eq(CLEANUP_BATCH_SIZE),
                eq(CLEANUP_MAX_BATCHES));
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(90).minusMinutes(1),
                LocalDateTime.now().minusDays(90).plusMinutes(1));
    }

    @Test
    void sweepClampsRetentionAboveTheManualMaximum() throws Exception {
        configureRetention(3650);
        when(auditRepository.deleteBefore(any(LocalDateTime.class), anyInt(), anyInt())).thenReturn(7);

        assertThat(runSweep()).isEqualTo(7);

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditRepository).deleteBefore(cutoff.capture(), eq(CLEANUP_BATCH_SIZE),
                eq(CLEANUP_MAX_BATCHES));
        assertThat(cutoff.getValue()).isBetween(LocalDateTime.now().minusDays(365).minusMinutes(1),
                LocalDateTime.now().minusDays(365).plusMinutes(1));
    }

    @Test
    void sweepSwallowsRepositoryFailures() throws Exception {
        configureRetention(30);
        when(auditRepository.deleteBefore(any(LocalDateTime.class), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("audit table is unreachable"));

        assertThat(runSweep()).isZero();
    }

    private void configureRetention(int retentionDays) {
        try {
            ReflectionTestUtils.setField(auditService, "retentionDays", retentionDays);
        } catch (RuntimeException error) {
            throw new AssertionError(
                    "AuditService has no studio.audit.retention-days retention setting", error);
        }
    }

    private int runSweep() throws Exception {
        return (Integer) sweepMethod().invoke(auditService);
    }

    private static Method sweepMethod() {
        try {
            return AuditService.class.getMethod("purgeExpiredAuditLogs");
        } catch (NoSuchMethodException error) {
            throw new AssertionError(
                    "AuditService has no scheduled audit retention sweep purgeExpiredAuditLogs()",
                    error);
        }
    }
}
