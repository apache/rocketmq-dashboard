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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"1s1m", "1m1m", "1ms1s", "1h2d", "1y1y", "", "1.5m"})
    void durationShouldRejectUnorderedOrRepeatedUnitsTest(String duration) {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setDuration(duration);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("duration");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1y2w3d4h5m6s7ms", "1h30m", "2w3d", "500ms", "0s", "1m1ms"})
    void durationShouldAcceptDescendingUnitsTest(String duration) {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setDuration(duration);

        assertThat(validator.validate(request)).isEmpty();
    }

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
}
