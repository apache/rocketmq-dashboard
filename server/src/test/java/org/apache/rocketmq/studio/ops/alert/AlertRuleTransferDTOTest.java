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
import java.util.List;

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
    void rejectsMissingVersionDomainAndRules() {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();

        assertThat(validator.validate(transfer))
                .extracting(violation -> violation.getMessage())
                .contains("version is required", "domain is required", "rules must not be empty");
    }

    @Test
    void rejectsEmptyRulesList() {
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(List.of());

        assertThat(validator.validate(transfer))
                .extracting(violation -> violation.getMessage())
                .contains("rules must not be empty");
    }

    @Test
    void acceptsCompleteTransferDocument() {
        AlertRuleRequestDTO rule = new AlertRuleRequestDTO();
        rule.setName("Broker unavailable");
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(List.of(rule));

        assertThat(validator.validate(transfer)).isEmpty();
    }

    @Test
    void propagatesValidationOfNestedRules() {
        AlertRuleRequestDTO invalid = new AlertRuleRequestDTO();
        AlertRuleTransferDTO transfer = new AlertRuleTransferDTO();
        transfer.setVersion(AlertRuleTransferDTO.VERSION);
        transfer.setDomain(AlertDomain.CLUSTER);
        transfer.setRules(List.of(invalid));

        assertThat(validator.validate(transfer))
                .extracting(violation -> violation.getMessage())
                .contains("name is required");
    }
}
