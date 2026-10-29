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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the import contract of {@link ImportConsumerGroupsDTO}: the instance and at least one
 * group are required, at most 100 groups are allowed per import, and the validation cascades
 * into each group entry.
 */
class ImportConsumerGroupsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ImportConsumerGroupsDTO request) {
        Set<ConstraintViolation<ImportConsumerGroupsDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private CreateConsumerGroupDTO group(String name) {
        CreateConsumerGroupDTO group = new CreateConsumerGroupDTO();
        group.setName(name);
        return group;
    }

    @Test
    void theInstanceIdAndAtLeastOneGroupAreRequired() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-1");
        request.setGroups(List.of());
        assertThat(violationsOf(request)).containsExactly("groups is required");

        request.setInstanceId(" ");
        assertThat(violationsOf(request)).contains("instanceId is required");
    }

    @Test
    void atMostOneHundredGroupsAreAllowedPerImport() {
        ImportConsumerGroupsDTO atLimit = new ImportConsumerGroupsDTO();
        atLimit.setInstanceId("instance-1");
        atLimit.setGroups(groups(100));
        assertThat(violationsOf(atLimit)).isEmpty();

        ImportConsumerGroupsDTO overLimit = new ImportConsumerGroupsDTO();
        overLimit.setInstanceId("instance-1");
        overLimit.setGroups(groups(101));
        assertThat(violationsOf(overLimit))
                .containsExactly("At most 100 consumer groups are allowed per import");
    }

    @Test
    void validationCascadesIntoEachGroupEntry() {
        ImportConsumerGroupsDTO request = new ImportConsumerGroupsDTO();
        request.setInstanceId("instance-1");
        request.setGroups(new ArrayList<>(List.of(group("group-1"))));
        request.getGroups().add(group("   "));

        assertThat(violationsOf(request)).contains("name is required");
    }

    private List<CreateConsumerGroupDTO> groups(int count) {
        List<CreateConsumerGroupDTO> groups = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            groups.add(group("group-" + i));
        }
        return groups;
    }
}
