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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateConfigDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void idShouldBeRequired() {
        UpdateConfigDTO request = UpdateConfigDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("id is required");
    }

    @Test
    void validConfigurationShouldPassValidation() {
        UpdateConfigDTO request = UpdateConfigDTO.builder()
                .id("cluster-a")
                .flushDiskType("SYNC_FLUSH")
                .autoCreateTopicEnable(true)
                .autoCreateSubscriptionGroup(false)
                .maxMessageSize(4_194_304)
                .fileReservedTime(72)
                .writeQueueNums(8)
                .readQueueNums(8)
                .brokerPermission(6)
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void messageSizeShouldStayWithinTheBrokerBounds() {
        UpdateConfigDTO tooSmall = UpdateConfigDTO.builder().id("cluster-a").maxMessageSize(1024).build();
        UpdateConfigDTO tooLarge = UpdateConfigDTO.builder().id("cluster-a").maxMessageSize(1_000_000_000).build();

        assertThat(validator.validate(tooSmall))
                .extracting(violation -> violation.getMessage())
                .contains("maxMessageSize must be between 1048576 and 134217728");
        assertThat(validator.validate(tooLarge))
                .extracting(violation -> violation.getMessage())
                .contains("maxMessageSize must be between 1048576 and 134217728");
    }

    @Test
    void retentionAndQueueCountsShouldRespectTheirRanges() {
        UpdateConfigDTO request = UpdateConfigDTO.builder()
                .id("cluster-a")
                .fileReservedTime(1000)
                .writeQueueNums(0)
                .readQueueNums(257)
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "fileReservedTime must be between 1 and 720",
                        "writeQueueNums must be between 1 and 256",
                        "readQueueNums must be between 1 and 256");
    }

    @Test
    void brokerPermissionShouldStayWithinZeroAndSeven() {
        UpdateConfigDTO request = UpdateConfigDTO.builder().id("cluster-a").brokerPermission(-1).build();
        UpdateConfigDTO upper = UpdateConfigDTO.builder().id("cluster-a").brokerPermission(8).build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("brokerPermission must be between 0 and 7");
        assertThat(validator.validate(upper))
                .extracting(violation -> violation.getMessage())
                .contains("brokerPermission must be between 0 and 7");
    }
}
