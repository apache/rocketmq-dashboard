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
    void scalesRatioMetricsIntoPercentWhenTheUnitIsPercentTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Heap").metric("broker.jvm.heap.usage_ratio")
                .threshold(80).thresholdUnit("%").build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning).title("Heap")
                .currentValue(0.754).build();

        assertThat(AlertNotificationTemplate.render("${value}%/", alert, rule)).isEqualTo("75.4%/");
    }

    @Test
    void keepsTheRawValueForNonRatioMetricsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Lag").metric("broker.consumer.lag")
                .threshold(1000).thresholdUnit("msgs").build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning).title("Lag")
                .currentValue(1500.0).build();

        assertThat(AlertNotificationTemplate.render("${value}", alert, rule)).isEqualTo("1500.0");
    }

    @Test
    void rendersInstanceLevelTimeAndEmptyRuleFieldsTest() {
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.error).title("Down")
                .description("broker unreachable").instanceId("prod-cluster")
                .time(LocalDateTime.of(2026, 8, 23, 12, 0, 30)).build();

        String rendered = AlertNotificationTemplate.render(
                "${level}|${instanceId}|${time}|[${ruleName}|${metric}|${threshold}]", alert, null);

        assertThat(rendered).isEqualTo("error|prod-cluster|2026-08-23T12:00:30|[||]");
    }

    @Test
    void trimsWhitespaceFromAConfiguredTemplateTest() {
        AlertRuleVO rule = AlertRuleVO.builder().name("Disk").threshold(85).build();
        SystemAlertVO alert = SystemAlertVO.builder().level(AlertLevel.warning).title("Disk")
                .description("usage high").build();

        assertThat(AlertNotificationTemplate.render("  ${title}: ${description}  ", alert, rule))
                .isEqualTo("Disk: usage high");
    }
}
