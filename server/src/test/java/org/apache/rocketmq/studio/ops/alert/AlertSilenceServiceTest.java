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
import org.apache.rocketmq.studio.common.domain.PageResult;
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
        when(repository.findActiveCandidates(AlertDomain.BUSINESS, 3L, "local", start.plusMinutes(1)))
                .thenReturn(List.of(created));
        when(repository.findActiveCandidates(AlertDomain.BUSINESS, 3L, "other", start.plusMinutes(1)))
                .thenReturn(List.of());
        when(repository.findActiveCandidates(AlertDomain.BUSINESS, 3L, "local", end))
                .thenReturn(List.of());

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
        when(repository.findActiveCandidates(AlertDomain.CLUSTER, 5L, "local", now)).thenReturn(List.of(silence));

        AlertRuleVO rule = AlertRuleVO.builder().id(5L).domain(AlertDomain.CLUSTER).build();
        assertThat(service.isActive(rule, "local", Map.of("brokerName", "broker-a", "cluster", "Default"), now))
                .isTrue();
        assertThat(service.isActive(rule, "local", Map.of("brokerName", "broker-b"), now)).isFalse();
        assertThat(service.isActive(rule, "local", Map.of(), now)).isFalse();
    }

    @Test
    void listPageShouldValidateAndDelegateToRepositoryPaginationTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        AlertSilenceVO silence = AlertSilenceVO.builder().id(11L).reason("deploy").build();
        when(repository.findPage(2, 25)).thenReturn(PageResult.of(List.of(silence), 51, 2, 25));

        PageResult<AlertSilenceVO> page = service.listPage(2, 25);

        assertThat(page.getTotal()).isEqualTo(51);
        assertThat(page.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo(11L));
        org.mockito.Mockito.verify(repository).findPage(2, 25);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void activeUntilShouldQueryScopedActiveCandidatesBeforeLabelMatchingTest() {
        AlertSilenceService service = new AlertSilenceService(repository, operationAuditService);
        LocalDateTime now = LocalDateTime.of(2026, 8, 22, 10, 0);
        AlertRuleVO rule = AlertRuleVO.builder().id(9L).domain(AlertDomain.CLUSTER).build();
        AlertSilenceVO wrongLabel = AlertSilenceVO.builder().id(1L).domain(AlertDomain.CLUSTER).ruleId(9L)
                .instanceId("local").labels(Map.of("brokerName", "broker-b"))
                .startsAt(now.minusMinutes(5)).endsAt(now.plusMinutes(10)).createdBy("admin").build();
        AlertSilenceVO firstMatch = AlertSilenceVO.builder().id(2L).domain(AlertDomain.CLUSTER).ruleId(9L)
                .instanceId("local").labels(Map.of("brokerName", "broker-a"))
                .startsAt(now.minusMinutes(5)).endsAt(now.plusMinutes(10)).createdBy("admin").build();
        AlertSilenceVO overlappingMatch = AlertSilenceVO.builder().id(3L).domain(AlertDomain.CLUSTER)
                .labels(Map.of("brokerName", "broker-a"))
                .startsAt(now.minusMinutes(1)).endsAt(now.plusMinutes(30)).createdBy("admin").build();
        when(repository.findActiveCandidates(AlertDomain.CLUSTER, 9L, "local", now))
                .thenReturn(List.of(wrongLabel, firstMatch, overlappingMatch));

        LocalDateTime activeUntil = service.activeUntil(rule, "local", Map.of("brokerName", "broker-a"), now);

        assertThat(activeUntil).isEqualTo(now.plusMinutes(30));
        org.mockito.Mockito.verify(repository).findActiveCandidates(AlertDomain.CLUSTER, 9L, "local", now);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAll();
    }
}
