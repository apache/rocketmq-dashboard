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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImportConsumerGroupsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRequestReportsRequiredFieldsTest() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("instanceId is required", "groups is required");
    }

    @Test
    void moreThanOneHundredGroupsAreRejectedTest() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-1");
        request.setGroups(IntStream.rangeClosed(1, 101)
                .mapToObj(index -> group("cg-" + index))
                .collect(Collectors.toCollection(ArrayList::new)));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("At most 100 consumer groups are allowed per import");
    }

    @Test
    void nestedInvalidGroupIsRejectedTest() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-1");
        CreateConsumerGroupDTO invalid = new CreateConsumerGroupDTO();
        invalid.setName(" ");
        request.setGroups(List.of(invalid));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("name is required");
    }

    @Test
    void completeRequestPassesValidationTest() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-1");
        request.setGroups(List.of(group("cg-orders")));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static CreateConsumerGroupDTO group(String name) {
        CreateConsumerGroupDTO group = new CreateConsumerGroupDTO();
        group.setName(name);
        return group;
    }
}
