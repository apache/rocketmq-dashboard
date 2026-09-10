/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleTransferServiceTest {

    @Mock
    private AlertService alertService;

    @Mock
    private NativeAlertMetricCatalogService metricCatalogService;

    private AlertRuleTransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new AlertRuleTransferService(alertService, metricCatalogService,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void exportsPortableRulesWithoutIdsOrRuntimeStateTest() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(42L)
                .domain(AlertDomain.CLUSTER)
                .name("Broker disk usage")
                .metric("broker.disk.usage")
                .operator(">")
                .threshold(85)
                .duration("5m")
                .channels(List.of("dingtalk"))
                .enabled(true)
                .lastTriggered("2026-08-23T10:35:38Z")
                .notificationTemplate("${ruleName}: ${value}")
                .build();
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(rule));

        AlertRuleTransferDTO transfer = transferService.exportRules(AlertDomain.CLUSTER);

        assertEquals(AlertRuleTransferDTO.VERSION, transfer.getVersion());
        assertEquals(AlertDomain.CLUSTER, transfer.getDomain());
        assertEquals("Broker disk usage", transfer.getRules().get(0).getName());
        assertEquals("${ruleName}: ${value}", transfer.getRules().get(0).getNotificationTemplate());
        assertNull(transfer.getRules().get(0).getId());
    }

    @Test
    void rejectsImportFromAnotherDomainBeforeChangingRulesTest() {
        AlertRuleTransferDTO transfer = transfer(AlertDomain.BUSINESS, request("Business lag"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER, transfer));

        assertEquals(400, error.getCode());
        verifyNoInteractions(alertService, metricCatalogService);
    }

    @Test
    void validatesEveryRuleBeforeCreatingAnyRuleTest() {
        AlertRuleRequestDTO first = request("First");
        AlertRuleRequestDTO invalid = request("Invalid");
        doAnswer(invocation -> {
            AlertRuleVO candidate = invocation.getArgument(0);
            if ("Invalid".equals(candidate.getName())) {
                throw new BusinessException(400, "invalid metric");
            }
            return null;
        }).when(metricCatalogService).validate(any(AlertRuleVO.class));

        assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER,
                        transfer(AlertDomain.CLUSTER, first, invalid)));

        verify(metricCatalogService, times(2)).validate(any(AlertRuleVO.class));
        verify(alertService, never()).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    @Test
    void importsRulesAsNewRulesInTheRequestedDomainTest() {
        AlertRuleRequestDTO request = request("Broker unavailable");
        request.setId(99L);
        request.setNotificationTemplate("${transition} ${metric}");
        when(alertService.createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        List<AlertRuleVO> imported = transferService.importRules(AlertDomain.CLUSTER,
                transfer(AlertDomain.CLUSTER, request));

        ArgumentCaptor<AlertRuleVO> captor = ArgumentCaptor.forClass(AlertRuleVO.class);
        verify(alertService).createRule(eq(AlertDomain.CLUSTER), captor.capture());
        assertNull(captor.getValue().getId());
        assertEquals(AlertDomain.CLUSTER, captor.getValue().getDomain());
        assertEquals("${transition} ${metric}", captor.getValue().getNotificationTemplate());
        assertEquals("Broker unavailable", imported.get(0).getName());
    }

    @Test
    void previewClassifiesNewDuplicateAndInvalidRowsWithoutWritingTest() {
        AlertRuleVO existing = existingRule(7L, "Existing disk rule", 85);
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(existing));
        AlertRuleRequestDTO duplicate = request("Imported duplicate");
        AlertRuleRequestDTO fresh = request("Fresh rule");
        fresh.setThreshold(90);
        AlertRuleRequestDTO invalid = request(" ");

        AlertRuleImportPreviewVO preview = transferService.previewRules(AlertDomain.CLUSTER,
                transfer(AlertDomain.CLUSTER, duplicate, fresh, invalid));

        assertThat(preview.totalCount()).isEqualTo(3);
        assertThat(preview.newCount()).isEqualTo(1);
        assertThat(preview.duplicateCount()).isEqualTo(1);
        assertThat(preview.invalidCount()).isEqualTo(1);
        assertThat(preview.items()).extracting(AlertRuleImportPreviewItemVO::status)
                .containsExactly(AlertRuleImportPreviewItemVO.Status.DUPLICATE,
                        AlertRuleImportPreviewItemVO.Status.NEW,
                        AlertRuleImportPreviewItemVO.Status.INVALID);
        assertThat(preview.items().get(0).existingRuleId()).isEqualTo(7L);
        assertThat(preview.items().get(0).existingRuleName()).isEqualTo("Existing disk rule");
        assertThat(preview.items().get(2).error()).contains("name", "required");
        verify(alertService, never()).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
        verify(alertService, never()).updateRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    @Test
    void previewRejectsDuplicateSemanticsInsideTheTransferTest() {
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of());

        AlertRuleImportPreviewVO preview = transferService.previewRules(AlertDomain.CLUSTER,
                transfer(AlertDomain.CLUSTER, request("First"), request("Second")));

        assertThat(preview.items()).extracting(AlertRuleImportPreviewItemVO::status)
                .containsExactly(AlertRuleImportPreviewItemVO.Status.NEW,
                        AlertRuleImportPreviewItemVO.Status.INVALID);
        assertThat(preview.items().get(1).error()).contains("row 1");
    }

    @Test
    void failStrategyRejectsExistingDuplicatesBeforeWritingTest() {
        when(alertService.listRules(AlertDomain.CLUSTER))
                .thenReturn(List.of(existingRule(7L, "Existing disk rule", 85)));

        assertThatThrownBy(() -> transferService.applyRules(AlertDomain.CLUSTER,
                apply(AlertRuleImportConflictStrategy.FAIL,
                        transfer(AlertDomain.CLUSTER, request("Imported duplicate")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duplicate");

        verify(alertService, never()).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
        verify(alertService, never()).updateRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    @Test
    void skipStrategyCreatesNewRulesAndLeavesDuplicatesUnchangedTest() {
        when(alertService.listRules(AlertDomain.CLUSTER))
                .thenReturn(List.of(existingRule(7L, "Existing disk rule", 85)));
        AlertRuleRequestDTO fresh = request("Fresh rule");
        fresh.setThreshold(90);
        when(alertService.createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AlertRuleImportResultVO result = transferService.applyRules(AlertDomain.CLUSTER,
                apply(AlertRuleImportConflictStrategy.SKIP,
                        transfer(AlertDomain.CLUSTER, request("Imported duplicate"), fresh)));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.replacedCount()).isZero();
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.changedRules()).extracting(AlertRuleVO::getName).containsExactly("Fresh rule");
        verify(alertService).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
        verify(alertService, never()).updateRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    @Test
    void replaceStrategyUpdatesDuplicatesAndCreatesNewRulesTest() {
        when(alertService.listRules(AlertDomain.CLUSTER))
                .thenReturn(List.of(existingRule(7L, "Existing disk rule", 85)));
        AlertRuleRequestDTO duplicate = request("Replacement name");
        AlertRuleRequestDTO fresh = request("Fresh rule");
        fresh.setThreshold(90);
        when(alertService.updateRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(alertService.createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));

        AlertRuleImportResultVO result = transferService.applyRules(AlertDomain.CLUSTER,
                apply(AlertRuleImportConflictStrategy.REPLACE,
                        transfer(AlertDomain.CLUSTER, duplicate, fresh)));

        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.replacedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        ArgumentCaptor<AlertRuleVO> updateCaptor = ArgumentCaptor.forClass(AlertRuleVO.class);
        verify(alertService).updateRule(eq(AlertDomain.CLUSTER), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getId()).isEqualTo(7L);
        assertThat(updateCaptor.getValue().getName()).isEqualTo("Replacement name");
    }

    @Test
    void invalidRowsBlockApplyForEveryConflictStrategyTest() {
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of());

        assertThatThrownBy(() -> transferService.applyRules(AlertDomain.CLUSTER,
                apply(AlertRuleImportConflictStrategy.SKIP,
                        transfer(AlertDomain.CLUSTER, request(" ")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid");

        verify(alertService, never()).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
        verify(alertService, never()).updateRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));
    }

    private static AlertRuleTransferDTO transfer(AlertDomain domain, AlertRuleRequestDTO... rules) {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(domain);
        transfer.setRules(List.of(rules));
        return transfer;
    }

    private static AlertRuleImportApplyDTO apply(AlertRuleImportConflictStrategy strategy,
                                                  AlertRuleTransferDTO transfer) {
        AlertRuleImportApplyDTO request = new AlertRuleImportApplyDTO();
        request.setStrategy(strategy);
        request.setTransfer(transfer);
        return request;
    }

    private static AlertRuleVO existingRule(Long id, String name, double threshold) {
        return AlertRuleVO.builder()
                .id(id)
                .domain(AlertDomain.CLUSTER)
                .name(name)
                .metric("broker.disk.usage")
                .operator(">")
                .threshold(threshold)
                .duration("5m")
                .channels(List.of("dingtalk"))
                .enabled(true)
                .aggregation("LAST")
                .consecutiveSamples(1)
                .build();
    }

    private static AlertRuleRequestDTO request(String name) {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName(name);
        request.setMetric("broker.disk.usage");
        request.setOperator(">");
        request.setThreshold(85);
        request.setDuration("5m");
        request.setChannels(List.of("dingtalk"));
        request.setEnabled(true);
        return request;
    }
}
