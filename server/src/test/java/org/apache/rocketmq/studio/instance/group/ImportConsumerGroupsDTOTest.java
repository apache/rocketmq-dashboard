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

class ImportConsumerGroupsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void instanceIdShouldBeRequired() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setGroups(List.of(group("cg-order")));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required");
    }

    @Test
    void groupsShouldBePresentAndBounded() {
        ImportConsumerGroupsDTO empty = new ImportConsumerGroupsDTO();
        empty.setInstanceId("instance-a");
        empty.setGroups(List.of());

        List<CreateConsumerGroupDTO> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(group("cg-" + i));
        }
        ImportConsumerGroupsDTO oversized = new ImportConsumerGroupsDTO();
        oversized.setInstanceId("instance-a");
        oversized.setGroups(many);

        assertThat(validator.validate(empty))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("groups is required");
        assertThat(validator.validate(oversized))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("At most 100 consumer groups are allowed per import");
    }

    @Test
    void nestedGroupValidationShouldPropagate() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-a");
        CreateConsumerGroupDTO nameless = new CreateConsumerGroupDTO();
        request.setGroups(List.of(nameless));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("name is required");
    }

    @Test
    void validImportShouldPassValidation() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-a");
        request.setGroups(List.of(group("cg-order"), group("cg-payment")));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static CreateConsumerGroupDTO group(String name) {
        CreateConsumerGroupDTO group = new CreateConsumerGroupDTO();
        group.setName(name);
        return group;
    }
}
