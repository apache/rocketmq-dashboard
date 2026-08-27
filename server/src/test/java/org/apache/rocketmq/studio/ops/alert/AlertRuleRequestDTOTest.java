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
    void toAlertRuleVOShouldTrimAndDeduplicateChannelsInInputOrderTest() {
        AlertRuleRequestDTO request = new AlertRuleRequestDTO();
        request.setName("High Lag");
        request.setChannels(List.of(" email ", "sms", "email", " sms "));

        assertThat(request.toAlertRuleVO().getChannels()).containsExactly("email", "sms");
    }
}
