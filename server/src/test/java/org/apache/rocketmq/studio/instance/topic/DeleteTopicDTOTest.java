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

class DeleteTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRequireTheOwningInstanceTest() {
        // The console sends both fields and api-spec 5.5 documents instanceId as required, because
        // deleting topic names is ambiguous across the instances that share a cluster.
        DeleteTopicDTO request = new DeleteTopicDTO();
        request.setName("orders");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("instanceId is required");
    }

    @Test
    void shouldRequireTheTopicNameTest() {
        DeleteTopicDTO request = new DeleteTopicDTO();
        request.setInstanceId("instance-a");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("name is required");
    }

    @Test
    void shouldAcceptBothFieldsTest() {
        DeleteTopicDTO request = new DeleteTopicDTO();
        request.setInstanceId("instance-a");
        request.setName("orders");

        assertThat(validator.validate(request)).isEmpty();
    }
}
