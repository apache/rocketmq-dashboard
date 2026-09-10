/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "studio.auth.login-required=false")
class AlertRuleTransferTransactionTest {

    @Autowired
    private AlertRuleTransferService transferService;

    @Autowired
    private AlertRepository alertRepository;

    @MockitoSpyBean
    private AlertService alertService;

    @Test
    void failedApplyRollsBackEarlierWritesTest() {
        String namePrefix = "import-rollback-" + UUID.randomUUID();
        AlertRuleImportApplyDTO request = new AlertRuleImportApplyDTO();
        request.setStrategy(AlertRuleImportConflictStrategy.FAIL);
        request.setTransfer(transfer(rule(namePrefix + "-first", 10),
                rule(namePrefix + "-second", 20)));
        AtomicInteger createCalls = new AtomicInteger();
        doAnswer(invocation -> {
            if (createCalls.incrementAndGet() == 2) {
                throw new IllegalStateException("forced second write failure");
            }
            return invocation.callRealMethod();
        }).when(alertService).createRule(eq(AlertDomain.CLUSTER), any(AlertRuleVO.class));

        try {
            assertThatThrownBy(() -> transferService.applyRules(AlertDomain.CLUSTER, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("forced second write failure");

            assertThat(alertRepository.findAllRules())
                    .noneMatch(rule -> rule.getName().startsWith(namePrefix));
        } finally {
            alertRepository.findAllRules().stream()
                    .filter(rule -> rule.getName().startsWith(namePrefix))
                    .map(AlertRuleVO::getId)
                    .forEach(alertRepository::deleteRule);
        }
    }

    private static AlertRuleTransferDTO transfer(AlertRuleRequestDTO... rules) {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(List.of(rules));
        return transfer;
    }

    private static AlertRuleRequestDTO rule(String name, double threshold) {
        AlertRuleRequestDTO rule = new AlertRuleRequestDTO();
        rule.setName(name);
        rule.setMetric("custom.metric");
        rule.setOperator(">");
        rule.setThreshold(threshold);
        rule.setDuration("5m");
        rule.setChannels(List.of("email"));
        rule.setEnabled(true);
        return rule;
    }
}
