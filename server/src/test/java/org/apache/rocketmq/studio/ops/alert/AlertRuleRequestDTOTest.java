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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void channelsShouldRejectNullBlankAndUnsupportedElementsTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setChannels(Arrays.asList("email", null, " "));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("channel must not be blank", "channel must not be blank",
                        "channel is unsupported");
    }

    @Test
    void durationShouldAcceptCompositePrometheusDurationTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setDuration("1h30m");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toAlertRuleVOShouldTrimAndDeduplicateChannelsInInputOrderTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setChannels(List.of(" email ", "sms", "email", " sms "));

        assertThat(request.toAlertRuleVO().getChannels()).containsExactly("email", "sms");
    }

    @Test
    void toAlertRuleVOShouldNormalizeTheNativeMetricKeyTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setMetric("  consumer.lag.total  ");

        assertThat(request.toAlertRuleVO().getMetric()).isEqualTo("consumer.lag.total");
    }

    @Test
    void toAlertRuleVOShouldApplyDefaultsForNullOptionalFields() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");

        AlertRuleVO vo = request.toAlertRuleVO();

        assertThat(vo.getAggregation()).isEqualTo("LAST");
        assertThat(vo.getWindowSeconds()).isZero();
        assertThat(vo.getConsecutiveSamples()).isEqualTo(1);
        assertThat(vo.getReminderInterval()).isEqualTo("30m");
        assertThat(vo.getMetric()).isNull();
        assertThat(vo.getChannels()).isNull();
    }

    @Test
    void toAlertRuleVOShouldCopyExplicitRuntimeFields() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setOperator(">=");
        request.setThreshold(85);
        request.setThresholdUnit("%");
        request.setDuration("5m");
        request.setAggregation("AVG");
        request.setWindowSeconds(60);
        request.setEnabled(false);
        request.setDescription("broker disk high");
        request.setBrokerName("broker-a");
        request.setClusterName("cluster-a");
        request.setSeverity("warning");
        request.setInstanceId("local");
        request.setConsumerGroup("group-a");
        request.setTopic("orders");
        request.setConsecutiveSamples(3);
        request.setReminderInterval("1h");
        request.setNotificationTemplate("${ruleName} ${value}");

        AlertRuleVO vo = request.toAlertRuleVO();

        assertThat(vo.getName()).isEqualTo("High Lag");
        assertThat(vo.getOperator()).isEqualTo(">=");
        assertThat(vo.getThreshold()).isEqualTo(85);
        assertThat(vo.getThresholdUnit()).isEqualTo("%");
        assertThat(vo.getDuration()).isEqualTo("5m");
        assertThat(vo.getAggregation()).isEqualTo("AVG");
        assertThat(vo.getWindowSeconds()).isEqualTo(60);
        assertThat(vo.isEnabled()).isFalse();
        assertThat(vo.getDescription()).isEqualTo("broker disk high");
        assertThat(vo.getBrokerName()).isEqualTo("broker-a");
        assertThat(vo.getClusterName()).isEqualTo("cluster-a");
        assertThat(vo.getSeverity()).isEqualTo("warning");
        assertThat(vo.getInstanceId()).isEqualTo("local");
        assertThat(vo.getConsumerGroup()).isEqualTo("group-a");
        assertThat(vo.getTopic()).isEqualTo("orders");
        assertThat(vo.getConsecutiveSamples()).isEqualTo(3);
        assertThat(vo.getReminderInterval()).isEqualTo("1h");
        assertThat(vo.getNotificationTemplate()).isEqualTo("${ruleName} ${value}");
    }
}
