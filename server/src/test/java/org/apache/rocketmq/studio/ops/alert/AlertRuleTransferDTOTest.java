/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleTransferDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsNullRuleEntriesBeforeImportTest() {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(Collections.singletonList(null));

        assertThat(validator.validate(transfer))
                .extracting(violation -> violation.getMessage())
                .contains("rule must not be null");
    }

    @Test
    void rejectsMissingEnvelopeFieldsTest() {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();

        assertThat(validator.validate(transfer))
                .extracting(violation -> violation.getMessage())
                .contains("version is required", "domain is required", "rules must not be empty");
    }

    @Test
    void acceptsACompleteTransferWithoutViolationsTest() {
        AlertRuleRequestDTO rule = new AlertRuleRequestDTO();
        rule.setName("Broker unavailable");
        rule.setMetric("broker.up");
        rule.setOperator("==");
        rule.setThreshold(0);
        rule.setDuration("1m");
        rule.setChannels(java.util.List.of("email"));
        rule.setEnabled(true);

        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(java.util.List.of(rule));

        assertThat(validator.validate(transfer)).isEmpty();
    }
}
