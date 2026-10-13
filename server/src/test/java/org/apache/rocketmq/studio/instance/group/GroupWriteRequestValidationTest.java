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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the request contract api-spec 6.6-6.8 documents: the owning instance is mandatory, a reset
 * needs a topic and a positive epoch-millis timestamp, and an import is a bounded JSON list.
 */
class GroupWriteRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deleteShouldRequireTheOwningInstanceTest() {
        DeleteConsumerGroupDTO request = new DeleteConsumerGroupDTO();
        request.setName("cg-orders");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("instanceId is required");
    }

    @Test
    void resetOffsetShouldRequireTheInstanceTheTopicAndAPositiveTimestampTest() {
        ResetConsumerOffsetDTO request = new ResetConsumerOffsetDTO();
        request.setName("cg-orders");
        request.setTimestamp(0L);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required", "topic is required",
                        "timestamp must be positive");
    }

    @Test
    void resetOffsetShouldAcceptAnEpochMillisTimestampTest() {
        ResetConsumerOffsetDTO request = new ResetConsumerOffsetDTO();
        request.setInstanceId("instance-a");
        request.setName("cg-orders");
        request.setTopic("orders");
        request.setTimestamp(1_780_000_000_000L);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void importShouldRequireTheInstanceAndANonEmptyBoundedListTest() {
        ImportConsumerGroupsDTO empty = new ImportConsumerGroupsDTO();
        assertThat(validator.validate(empty))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required", "groups is required");

        List<CreateConsumerGroupDTO> tooMany = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            CreateConsumerGroupDTO group = new CreateConsumerGroupDTO();
            group.setName("cg-" + index);
            tooMany.add(group);
        }
        ImportConsumerGroupsDTO oversized = new ImportConsumerGroupsDTO();
        oversized.setInstanceId("instance-a");
        oversized.setGroups(tooMany);

        assertThat(validator.validate(oversized))
                .extracting(violation -> violation.getMessage())
                .containsExactly("At most 100 consumer groups are allowed per import");
    }
}
