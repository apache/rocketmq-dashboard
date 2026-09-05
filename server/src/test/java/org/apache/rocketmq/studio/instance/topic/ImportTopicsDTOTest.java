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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImportTopicsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRequestReportsRequiredFieldsTest() {
        ImportTopicsDTO request = new ImportTopicsDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("instanceId is required", "topics is required");
    }

    @Test
    void moreThanOneHundredTopicsAreRejectedTest() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-1");
        request.setTopics(IntStream.rangeClosed(1, 101)
                .mapToObj(index -> topic("topic-" + index))
                .collect(Collectors.toCollection(ArrayList::new)));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("At most 100 topics are allowed per import");
    }

    @Test
    void nestedInvalidTopicIsRejectedTest() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-1");
        CreateTopicDTO invalid = new CreateTopicDTO();
        invalid.setName(" ");
        request.setTopics(List.of(invalid));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("name is required");
    }

    @Test
    void completeRequestPassesValidationTest() {
        ImportTopicsDTO request = new ImportTopicsDTO();
        request.setInstanceId("instance-1");
        request.setTopics(List.of(topic("orders")));

        assertThat(validator.validate(request)).isEmpty();
    }

    private static CreateTopicDTO topic(String name) {
        CreateTopicDTO topic = new CreateTopicDTO();
        topic.setName(name);
        return topic;
    }
}
