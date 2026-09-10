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

class CreateTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void nameShouldBeRequired() {
        CreateTopicDTO request = new CreateTopicDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("name is required");
    }

    @Test
    void queueCountsShouldNotBeNegative() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        request.setWriteQueues(-1);
        request.setReadQueues(-2);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("writeQueues must be zero or positive",
                        "readQueues must be zero or positive");
    }

    @Test
    void validRequestShouldPassValidation() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        request.setWriteQueues(8);
        request.setReadQueues(8);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toTopicVoShouldOnlyApplyNonNullQueues() {
        CreateTopicDTO sparse = new CreateTopicDTO();
        sparse.setName("orders");

        TopicVO topic = sparse.toTopicVO();
        assertThat(topic.getWriteQueues()).isZero();
        assertThat(topic.getReadQueues()).isZero();

        CreateTopicDTO full = new CreateTopicDTO();
        full.setName("orders");
        full.setWriteQueues(16);
        full.setReadQueues(4);

        TopicVO mapped = full.toTopicVO();
        assertThat(mapped.getWriteQueues()).isEqualTo(16);
        assertThat(mapped.getReadQueues()).isEqualTo(4);
    }
}
