/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertNotificationTemplateTest {

    @Test
    void replacesOnlyDocumentedValuesAndLeavesUnknownPlaceholdersUntouchedTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Disk threshold").metric("broker.disk.usage_ratio")
                .threshold(85).thresholdUnit("%").build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning).title("Disk threshold")
                .description("FIRING broker.disk.usage_ratio on local").transition("FIRING")
                .instanceId("local").currentValue(0.865).time(LocalDateTime.of(2026, 8, 23, 12, 0))
                .labels(Map.of("brokerName", "broker-a", "brokerAddr", "127.0.0.1:10911")).build();

        String rendered = AlertNotificationTemplate.render(
                "${ruleName}|${transition}|${value}${thresholdUnit}/${threshold}|${labels}|${missing}", alert, rule);

        assertThat(rendered).isEqualTo("Disk threshold|FIRING|86.5%/85.0|"
                + "brokerAddr=127.0.0.1:10911, brokerName=broker-a|${missing}");
    }

    @Test
    void rendersPercentageValuesForPaddedStoredMetricsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Disk threshold").metric(" broker.disk.usage_ratio ")
                .threshold(85).thresholdUnit("%").build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning).title("Disk threshold")
                .description("FIRING").transition("FIRING").instanceId("local").currentValue(0.865)
                .time(LocalDateTime.of(2026, 8, 23, 12, 0)).labels(Map.of()).build();

        String rendered = AlertNotificationTemplate.render("${value}${thresholdUnit}", alert, rule);

        assertThat(rendered).isEqualTo("86.5%");
    }

    @Test
    void usesTheExistingNotificationFormatWhenNoTemplateWasConfiguredTest() {
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.info).title("Test")
                .description("connection works").build();

        assertThat(AlertNotificationTemplate.render(null, alert, null))
                .isEqualTo("[info] Test - connection works\nLabels: ");
    }

    @Test
    void doesNotExpandPlaceholderSyntaxIntroducedByAlertValuesTest() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .title("${description}")
                .description("internal detail")
                .build();

        assertThat(AlertNotificationTemplate.render("${title}", alert, null))
                .isEqualTo("${description}");
    }

    @Test
    void missingAlertFieldsRenderAsEmptyValues() {
        SystemAlertVO alert = SystemAlertVO.builder().build();

        String rendered = AlertNotificationTemplate.render(
                "${ruleName}|${title}|${description}|${value}|${time}|${labels}|${level}", alert, null);

        assertThat(rendered).isEqualTo("||||||");
    }

    @Test
    void rawCurrentValueIsUsedForNonRatioMetrics() {
        AlertRuleVO rule = AlertRuleVO.builder().metric("consumer.lag.total")
                .thresholdUnit("%").threshold(85).build();
        SystemAlertVO alert = SystemAlertVO.builder().currentValue(0.865).build();

        assertThat(AlertNotificationTemplate.render("${value}", alert, rule))
                .isEqualTo("0.865");
    }

    @Test
    void whitespaceAroundTemplateIsTrimmed() {
        SystemAlertVO alert = SystemAlertVO.builder().title("T").build();

        assertThat(AlertNotificationTemplate.render("   ${title}   ", alert, null))
                .isEqualTo("T");
    }
}
