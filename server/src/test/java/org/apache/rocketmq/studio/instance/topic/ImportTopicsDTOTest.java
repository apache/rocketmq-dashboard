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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportTopicsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void instanceIdShouldBeRequired() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setTopics(java.util.List.of(topic("orders")));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required");
    }

    @Test
    void topicsShouldBePresentAndBounded() {
        ImportTopicsDTO empty = new ImportTopicsDTO();
        empty.setInstanceId("instance-a");
        empty.setTopics(java.util.List.of());

        java.util.List<CreateTopicDTO> many = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add(topic("orders-" + i));
        }
        ImportTopicsDTO oversized = new ImportTopicsDTO();
        oversized.setInstanceId("instance-a");
        oversized.setTopics(many);

        assertThat(validator.validate(empty))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("topics is required");
        assertThat(validator.validate(oversized))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("At most 100 topics are allowed per import");
    }

    @Test
    void nestedTopicValidationShouldPropagate() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-a");
        request.setTopics(java.util.List.of(new CreateTopicDTO()));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("name is required");
    }

    @Test
    void validImportShouldPassValidation() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-a");
        request.setTopics(java.util.List.of(topic("orders"), topic("payments")));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static CreateTopicDTO topic(String name) {
        CreateTopicDTO topic = new CreateTopicDTO();
        topic.setName(name);
        return topic;
    }
}
