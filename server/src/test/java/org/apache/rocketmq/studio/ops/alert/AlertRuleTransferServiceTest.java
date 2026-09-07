/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private AlertRuleTransferService transferService;

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

    private static AlertRuleTransferDTO transfer(AlertDomain domain, AlertRuleRequestDTO... rules) {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(domain);
        transfer.setRules(List.of(rules));
        return transfer;
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
