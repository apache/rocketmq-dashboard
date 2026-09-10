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

    @Test
    void rejectsNullImportDocumentTest() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER, null));

        assertEquals(400, error.getCode());
        assertEquals("Alert rule import document is required", error.getMessage());
        verifyNoInteractions(alertService, metricCatalogService);
    }

    @Test
    void rejectsUnsupportedImportVersionTest() {
        AlertRuleTransferDTO transfer = transfer(AlertDomain.CLUSTER, request("Broker unavailable"));
        transfer.setVersion(AlertRuleTransferDTO.VERSION + 1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER, transfer));

        assertEquals(400, error.getCode());
        assertEquals("Unsupported alert rule import version", error.getMessage());
        verifyNoInteractions(alertService, metricCatalogService);
    }

    @Test
    void rejectsEmptyAndOversizedRuleListsTest() {
        BusinessException empty = assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER,
                        transfer(AlertDomain.CLUSTER)));
        assertEquals(400, empty.getCode());
        assertEquals("Alert rule import must contain between 1 and 200 rules", empty.getMessage());

        AlertRuleRequestDTO[] many = java.util.stream.IntStream.range(0, 201)
                .mapToObj(index -> request("Rule " + index))
                .toArray(AlertRuleRequestDTO[]::new);
        BusinessException oversized = assertThrows(BusinessException.class,
                () -> transferService.importRules(AlertDomain.CLUSTER,
                        transfer(AlertDomain.CLUSTER, many)));
        assertEquals(400, oversized.getCode());
        assertEquals("Alert rule import must contain between 1 and 200 rules", oversized.getMessage());
        verifyNoInteractions(alertService, metricCatalogService);
    }

    @Test
    void exportCarriesEveryPortableRuleFieldTest() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(7L)
                .domain(AlertDomain.CLUSTER)
                .name("Broker unavailable")
                .metric("broker.up")
                .operator("==")
                .threshold(0)
                .thresholdUnit("%")
                .duration("1m")
                .aggregation("MAX")
                .windowSeconds(60)
                .channels(List.of("dingtalk", "email"))
                .enabled(false)
                .description("Broker went offline")
                .brokerName("broker-a")
                .clusterName("DefaultCluster")
                .severity("P1")
                .instanceId("instance-1")
                .consumerGroup("cg-orders")
                .topic("orders")
                .consecutiveSamples(3)
                .reminderInterval("10m")
                .notificationTemplate("${title}")
                .build();
        when(alertService.listRules(AlertDomain.CLUSTER)).thenReturn(List.of(rule));

        AlertRuleRequestDTO request = transferService.exportRules(AlertDomain.CLUSTER)
                .getRules().get(0);

        assertEquals("Broker unavailable", request.getName());
        assertEquals("broker.up", request.getMetric());
        assertEquals("==", request.getOperator());
        assertEquals(0, request.getThreshold());
        assertEquals("%", request.getThresholdUnit());
        assertEquals("1m", request.getDuration());
        assertEquals("MAX", request.getAggregation());
        assertEquals(60, request.getWindowSeconds());
        assertEquals(List.of("dingtalk", "email"), request.getChannels());
        assertEquals(false, request.isEnabled());
        assertEquals("Broker went offline", request.getDescription());
        assertEquals("broker-a", request.getBrokerName());
        assertEquals("DefaultCluster", request.getClusterName());
        assertEquals("P1", request.getSeverity());
        assertEquals("instance-1", request.getInstanceId());
        assertEquals("cg-orders", request.getConsumerGroup());
        assertEquals("orders", request.getTopic());
        assertEquals(3, request.getConsecutiveSamples());
        assertEquals("10m", request.getReminderInterval());
        assertEquals("${title}", request.getNotificationTemplate());
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
