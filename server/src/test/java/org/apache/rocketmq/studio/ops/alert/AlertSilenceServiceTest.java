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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertSilenceServiceTest {
    @Mock
    private AlertSilenceRepository repository;
    @Mock
    private OperationAuditService operationAuditService;

    @Test
    void createsInstanceScopedSilenceAndMatchesOnlyItsScopeTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        LocalDateTime start = LocalDateTime.of(2026, 8, 22, 9, 0);
        LocalDateTime end = start.plusHours(1);
        when(repository.save(any())).thenAnswer(invocation -> {
            AlertSilenceVO silence = invocation.getArgument(0);
            silence.setId(7L);
            return silence;
        });

        CreateAlertSilenceDTO request = new CreateAlertSilenceDTO();
        request.setDomain(AlertDomain.BUSINESS);
        request.setRuleId(3L);
        request.setInstanceId(" local ");
        request.setStartsAt(start.atOffset(ZoneOffset.UTC));
        request.setEndsAt(end.atOffset(ZoneOffset.UTC));
        request.setReason("maintenance");
        AlertSilenceVO created = service.create(request);

        ArgumentCaptor<AlertSilenceVO> captured = ArgumentCaptor.forClass(AlertSilenceVO.class);
        org.mockito.Mockito.verify(repository).save(captured.capture());
        assertThat(captured.getValue().getInstanceId()).isEqualTo("local");
        assertThat(captured.getValue().getStartsAt()).isEqualTo(start);
        assertThat(created.getId()).isEqualTo(7L);
        when(repository.findAll()).thenReturn(List.of(created));

        AlertRuleVO rule = AlertRuleVO.builder().id(3L).domain(AlertDomain.BUSINESS).build();
        assertThat(service.isActive(rule, "local", start.plusMinutes(1))).isTrue();
        assertThat(service.isActive(rule, "other", start.plusMinutes(1))).isFalse();
        assertThat(service.isActive(rule, "local", end)).isFalse();
    }

    @Test
    void rejectsEmptyOrReversedTimeWindowTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        CreateAlertSilenceDTO request = new CreateAlertSilenceDTO();
        request.setStartsAt(LocalDateTime.of(2026, 8, 22, 10, 0).atOffset(ZoneOffset.UTC));
        request.setEndsAt(request.getStartsAt());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Silence end time must be after start time");
    }

    @Test
    void normalizesOffsetInputToUtcBeforePersistenceTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        CreateAlertSilenceDTO request = new CreateAlertSilenceDTO();
        request.setStartsAt(java.time.OffsetDateTime.parse("2026-08-22T09:00:00-07:00"));
        request.setEndsAt(java.time.OffsetDateTime.parse("2026-08-22T10:00:00-07:00"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request);

        org.mockito.Mockito.verify(repository).save(org.mockito.ArgumentMatchers.argThat(silence ->
                LocalDateTime.of(2026, 8, 22, 16, 0).equals(silence.getStartsAt())
                        && LocalDateTime.of(2026, 8, 22, 17, 0).equals(silence.getEndsAt())));
    }

    @Test
    void matchesOnlyWhenAllConfiguredResourceLabelsMatchTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 10, 0);
        AlertSilenceVO silence = AlertSilenceVO.builder().domain(AlertDomain.CLUSTER).ruleId(5L)
                .instanceId("local").labels(Map.of("brokerName", "broker-a"))
                .startsAt(now.minusMinutes(1)).endsAt(now.plusMinutes(1)).createdBy("admin").build();
        when(repository.findAll()).thenReturn(List.of(silence));

        AlertRuleVO rule = AlertRuleVO.builder().id(5L).domain(AlertDomain.CLUSTER).build();
        assertThat(service.isActive(rule, "local", Map.of("brokerName", "broker-a", "cluster", "Default"), now))
                .isTrue();
        assertThat(service.isActive(rule, "local", Map.of("brokerName", "broker-b"), now)).isFalse();
        assertThat(service.isActive(rule, "local", Map.of(), now)).isFalse();
    }
}
