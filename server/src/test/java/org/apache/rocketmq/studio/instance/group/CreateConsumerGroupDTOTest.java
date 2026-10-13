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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create-group request of {@link CreateConsumerGroupDTO}: the name is required and
 * the retry/delay knobs must not go negative — and its {@code toConsumerGroupVO()} mapping
 * copies every field across, tolerating absent retry/delay values so the group keeps its own
 * defaults instead of blowing up or silently redefaulting explicit ones.
 */
class CreateConsumerGroupDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateConsumerGroupDTO request) {
        Set<ConstraintViolation<CreateConsumerGroupDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aCompleteRequestPassesValidation() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("order-consumer-group");
        request.setSubscriptionMode(SubscriptionMode.Push);
        request.setConsumeType(ConsumeType.CLUSTERING);
        request.setRetryMaxTimes(16);
        request.setDelaySeconds(0);

        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theNameIsRequiredAndTheKnobsMustNotGoNegative() {
        CreateConsumerGroupDTO invalid = new CreateConsumerGroupDTO();
        invalid.setName("   ");
        invalid.setRetryMaxTimes(-1);
        invalid.setDelaySeconds(-5);

        assertThat(violationsOf(invalid)).containsExactlyInAnyOrder(
                "name is required",
                "retryMaxTimes must be zero or positive",
                "delaySeconds must be zero or positive");

        CreateConsumerGroupDTO zeroKnobs = new CreateConsumerGroupDTO();
        zeroKnobs.setName("order-consumer-group");
        zeroKnobs.setRetryMaxTimes(0);
        zeroKnobs.setDelaySeconds(0);
        assertThat(violationsOf(zeroKnobs)).isEmpty();
    }

    @Test
    void theMappingCarriesEveryFieldAcross() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("order-consumer-group");
        request.setNamespace("ns-1");
        request.setClusterId("cluster-1");
        request.setSubscriptionMode(SubscriptionMode.Push);
        request.setConsumeType(ConsumeType.CLUSTERING);
        request.setSubscriptionDataType("JSON");
        request.setDeliveryOrderType("PARALLEL");
        request.setRetryMaxTimes(16);
        request.setDelaySeconds(30);

        ConsumerGroupVO group = request.toConsumerGroupVO();

        assertThat(group.getName()).isEqualTo("order-consumer-group");
        assertThat(group.getNamespace()).isEqualTo("ns-1");
        assertThat(group.getClusterId()).isEqualTo("cluster-1");
        assertThat(group.getSubscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(group.getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(group.getSubscriptionDataType()).isEqualTo("JSON");
        assertThat(group.getDeliveryOrderType()).isEqualTo("PARALLEL");
        assertThat(group.getRetryMaxTimes()).isEqualTo(16);
        assertThat(group.getDelaySeconds()).isEqualTo(30);
    }

    @Test
    void absentRetryAndDelayKeepTheGroupDefaults() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("order-consumer-group");

        ConsumerGroupVO group = request.toConsumerGroupVO();

        assertThat(group.getRetryMaxTimes()).isZero();
        assertThat(group.getDelaySeconds()).isZero();
    }
}
