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
package org.apache.rocketmq.studio.cluster.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bean-validation bounds of {@link UpdateConfigDTO}: the cluster id is required, and each
 * numeric broker setting has the exact floor and ceiling the broker API accepts.
 */
class UpdateConfigDTOTest {

    private static final String MAX_MESSAGE_SIZE_MESSAGE =
            "maxMessageSize must be between 1048576 and 134217728";

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateConfigDTO request) {
        Set<ConstraintViolation<UpdateConfigDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }

    private UpdateConfigDTO validRequest() {
        return UpdateConfigDTO.builder().id("cluster-1").build();
    }

    @Test
    void aMissingOrBlankIdIsRejected() {
        UpdateConfigDTO missing = validRequest();
        missing.setId(null);
        assertThat(violationsOf(missing)).containsExactly("id is required");

        UpdateConfigDTO blank = validRequest();
        blank.setId("   ");
        assertThat(violationsOf(blank)).containsExactly("id is required");
    }

    @Test
    void aValidRequestPassesUntouched() {
        assertThat(violationsOf(validRequest())).isEmpty();
    }

    @Test
    void maxMessageSizeIsBoundedBetweenOneMiBAnd128MiB() {
        UpdateConfigDTO tooSmall = validRequest();
        tooSmall.setMaxMessageSize(1_048_575);
        assertThat(violationsOf(tooSmall)).contains(MAX_MESSAGE_SIZE_MESSAGE);

        UpdateConfigDTO atFloor = validRequest();
        atFloor.setMaxMessageSize(1_048_576);
        assertThat(violationsOf(atFloor)).isEmpty();

        UpdateConfigDTO atCeiling = validRequest();
        atCeiling.setMaxMessageSize(134_217_728);
        assertThat(violationsOf(atCeiling)).isEmpty();

        UpdateConfigDTO tooLarge = validRequest();
        tooLarge.setMaxMessageSize(134_217_729);
        assertThat(violationsOf(tooLarge)).contains(MAX_MESSAGE_SIZE_MESSAGE);
    }

    @Test
    void fileReservedTimeIsBoundedBetweenOneAnd720Hours() {
        UpdateConfigDTO tooSmall = validRequest();
        tooSmall.setFileReservedTime(0);
        assertThat(violationsOf(tooSmall)).contains("fileReservedTime must be between 1 and 720");

        UpdateConfigDTO atFloor = validRequest();
        atFloor.setFileReservedTime(1);
        assertThat(violationsOf(atFloor)).isEmpty();

        UpdateConfigDTO atCeiling = validRequest();
        atCeiling.setFileReservedTime(720);
        assertThat(violationsOf(atCeiling)).isEmpty();

        UpdateConfigDTO tooLarge = validRequest();
        tooLarge.setFileReservedTime(721);
        assertThat(violationsOf(tooLarge)).contains("fileReservedTime must be between 1 and 720");
    }

    @Test
    void queueNumbersAreBoundedBetweenOneAnd256() {
        UpdateConfigDTO tooFewWriteQueues = validRequest();
        tooFewWriteQueues.setWriteQueueNums(0);
        assertThat(violationsOf(tooFewWriteQueues)).contains("writeQueueNums must be between 1 and 256");

        UpdateConfigDTO tooManyReadQueues = validRequest();
        tooManyReadQueues.setReadQueueNums(257);
        assertThat(violationsOf(tooManyReadQueues)).contains("readQueueNums must be between 1 and 256");

        UpdateConfigDTO atBounds = validRequest();
        atBounds.setWriteQueueNums(256);
        atBounds.setReadQueueNums(1);
        assertThat(violationsOf(atBounds)).isEmpty();
    }

    @Test
    void brokerPermissionIsBoundedBetweenZeroAndSeven() {
        UpdateConfigDTO tooLow = validRequest();
        tooLow.setBrokerPermission(-1);
        assertThat(violationsOf(tooLow)).contains("brokerPermission must be between 0 and 7");

        UpdateConfigDTO tooHigh = validRequest();
        tooHigh.setBrokerPermission(8);
        assertThat(violationsOf(tooHigh)).contains("brokerPermission must be between 0 and 7");

        UpdateConfigDTO atBounds = validRequest();
        atBounds.setBrokerPermission(0);
        assertThat(violationsOf(atBounds)).isEmpty();

        UpdateConfigDTO readOnly = validRequest();
        readOnly.setBrokerPermission(7);
        assertThat(violationsOf(readOnly)).isEmpty();
    }
}
