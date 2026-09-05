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
    void expandsEveryRemainingDocumentedTokenTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Orders rate").metric("orders.total")
                .thresholdUnit("%").build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning)
                .title("Orders rate")
                .description("orders above threshold")
                .transition("FIRING")
                .instanceId("local")
                .currentValue(0.5)
                .time(LocalDateTime.of(2026, 8, 23, 9, 30))
                .build();

        String rendered = AlertNotificationTemplate.render(
                "${title}|${description}|${metric}|${instanceId}|${level}|${time}|${value}", alert, rule);

        assertThat(rendered).isEqualTo(
                "Orders rate|orders above threshold|orders.total|local|warning|2026-08-23T09:30|0.5");
    }

    @Test
    void emptiesMissingTimesAndValuesTest() {
        SystemAlertVO alert = SystemAlertVO.builder().title("x").build();

        assertThat(AlertNotificationTemplate.render("${time}|${value}", alert, null)).isEqualTo("|");
    }
}
