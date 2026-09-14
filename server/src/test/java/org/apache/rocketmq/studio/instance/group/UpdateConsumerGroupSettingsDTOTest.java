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
    void shouldAcceptZeroRetrySettingsTest() {
        // 0 is a valid broker-side value (retryQueueNums=0 disables retry queues,
        // retryMaxTimes=0 sends failures straight to the DLQ) and CreateConsumerGroupDTO
        // accepts retryMaxTimes=0 — the update path must not reject what create allows.
        UpdateConsumerGroupSettingsDTO request = new UpdateConsumerGroupSettingsDTO();
        request.setInstanceId("instance-a");
        request.setName("cg-orders");
        request.setRetryQueueNums(0);
        request.setRetryMaxTimes(0);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldRejectNegativeRetrySettingsTest() {
        UpdateConsumerGroupSettingsDTO request = new UpdateConsumerGroupSettingsDTO();
        request.setInstanceId("instance-a");
        request.setName("cg-orders");
        request.setRetryQueueNums(-1);
        request.setRetryMaxTimes(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("retryQueueNums must be zero or positive",
                        "retryMaxTimes must be zero or positive");
    }
}
