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
    void nameShouldBeRequiredTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("name is required");
    }

    @Test
    void operatorAggregationAndSeverityShouldRejectInvalidValuesTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setOperator("between");
        request.setAggregation("MEDIAN");
        request.setSeverity("fatal");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("operator is invalid", "aggregation is invalid",
                        "severity is invalid");
    }

    @Test
    void durationReminderAndTemplateShouldBeValidatedTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setDuration("5 minutes");
        request.setReminderInterval("every hour");
        request.setNotificationTemplate("x".repeat(4001));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("duration is invalid", "reminderInterval is invalid",
                        "notificationTemplate must not exceed 4000 characters");
    }

    @Test
    void windowAndConsecutiveSamplesShouldRejectNonPositiveValuesTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setWindowSeconds(-5);
        request.setConsecutiveSamples(0);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("windowSeconds must not be negative",
                        "consecutiveSamples must be at least 1");
    }

    @Test
    void toAlertRuleVOShouldFillOperationalDefaultsTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");

        AlertRuleVO vo = request.toAlertRuleVO();

        assertThat(vo.getAggregation()).isEqualTo("LAST");
        assertThat(vo.getWindowSeconds()).isZero();
        assertThat(vo.getConsecutiveSamples()).isEqualTo(1);
        assertThat(vo.getReminderInterval()).isEqualTo("30m");
        assertThat(vo.getChannels()).isNull();
    }
}
