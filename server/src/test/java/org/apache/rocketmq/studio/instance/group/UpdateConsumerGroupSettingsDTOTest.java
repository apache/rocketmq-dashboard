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
package org.apache.rocketmq.studio.instance.group;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateConsumerGroupSettingsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void identityFieldsShouldBeRequired() {
        UpdateConsumerGroupSettingsDTO request = new UpdateConsumerGroupSettingsDTO();
        request.setRetryQueueNums(2);
        request.setRetryMaxTimes(16);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required", "name is required");
    }

    @Test
    void retrySettingsShouldBeRequiredAndPositive() {
        UpdateConsumerGroupSettingsDTO missing = new UpdateConsumerGroupSettingsDTO();
        missing.setInstanceId("instance-a");
        missing.setName("cg-order");

        UpdateConsumerGroupSettingsDTO zero = new UpdateConsumerGroupSettingsDTO();
        zero.setInstanceId("instance-a");
        zero.setName("cg-order");
        zero.setRetryQueueNums(0);
        zero.setRetryMaxTimes(16);

        assertThat(validator.validate(missing))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("retryQueueNums is required", "retryMaxTimes is required");
        assertThat(validator.validate(zero))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("retryQueueNums must be positive");
    }

    @Test
    void validSettingsShouldPassValidation() {
        UpdateConsumerGroupSettingsDTO request = new UpdateConsumerGroupSettingsDTO();
        request.setInstanceId("instance-a");
        request.setName("cg-order");
        request.setRetryQueueNums(2);
        request.setRetryMaxTimes(16);

        assertThat(validator.validate(request)).isEmpty();
    }
}
