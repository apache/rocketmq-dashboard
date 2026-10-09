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
package org.apache.rocketmq.studio.instance.topic;

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
 * Pins the import contract of {@link ImportTopicsDTO}: the instance and at least one topic are
 * required, at most 100 topics are allowed per import, and the validation cascades into each
 * topic entry.
 */
class ImportTopicsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ImportTopicsDTO request) {
        Set<ConstraintViolation<ImportTopicsDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private CreateTopicDTO topic(String name) {
        CreateTopicDTO topic = new CreateTopicDTO();
        topic.setName(name);
        return topic;
    }

    @Test
    void theInstanceIdAndAtLeastOneTopicAreRequired() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-1");
        request.setTopics(List.of());
        assertThat(violationsOf(request)).containsExactly("topics is required");

        request.setInstanceId(" ");
        assertThat(violationsOf(request)).contains("instanceId is required");
    }

    @Test
    void atMostOneHundredTopicsAreAllowedPerImport() {
        ImportTopicsDTO atLimit = new ImportTopicsDTO();
        atLimit.setInstanceId("instance-1");
        atLimit.setTopics(topics(100));
        assertThat(violationsOf(atLimit)).isEmpty();

        ImportTopicsDTO overLimit = new ImportTopicsDTO();
        overLimit.setInstanceId("instance-1");
        overLimit.setTopics(topics(101));
        assertThat(violationsOf(overLimit)).containsExactly("At most 100 topics are allowed per import");
    }

    @Test
    void validationCascadesIntoEachTopicEntry() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-1");
        request.setTopics(new ArrayList<>(List.of(topic("topic-1"))));
        request.getTopics().add(topic("   "));

        assertThat(violationsOf(request)).contains("name is required");
    }

    private List<CreateTopicDTO> topics(int count) {
        List<CreateTopicDTO> topics = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            topics.add(topic("topic-" + i));
        }
        return topics;
    }
}
