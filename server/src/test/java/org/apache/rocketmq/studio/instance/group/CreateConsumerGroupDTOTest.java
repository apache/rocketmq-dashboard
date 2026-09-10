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
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateConsumerGroupDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingNameIsRejectedTest() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("name is required");
    }

    @Test
    void negativeRetryAndDelayValuesAreRejectedTest() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("cg-orders");
        request.setRetryMaxTimes(-1);
        request.setDelaySeconds(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "retryMaxTimes must be zero or positive",
                        "delaySeconds must be zero or positive");
    }

    @Test
    void completeRequestPassesValidationAndMapsTest() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("cg-orders");
        request.setNamespace("prod");
        request.setSubscriptionMode(SubscriptionMode.Push);
        request.setConsumeType(ConsumeType.CLUSTERING);
        request.setRetryMaxTimes(16);
        request.setDelaySeconds(0);

        assertThat(validator.validate(request)).isEmpty();

        ConsumerGroupVO group = request.toConsumerGroupVO();
        assertThat(group.getName()).isEqualTo("cg-orders");
        assertThat(group.getSubscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(group.getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(group.getRetryMaxTimes()).isEqualTo(16);
        assertThat(group.getDelaySeconds()).isEqualTo(0);
    }
}
