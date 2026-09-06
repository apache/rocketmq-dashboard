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

class UpdateTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void nameShouldBeRequired() {
        UpdateTopicDTO request = new UpdateTopicDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("name is required");
    }

    @Test
    void negativeQueueCountsShouldBeRejected() {
        UpdateTopicDTO request = new UpdateTopicDTO();
        request.setName("orders");
        request.setWriteQueues(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("writeQueues must be zero or positive");
    }

    @Test
    void toTopicVoShouldMapOptionalFields() {
        UpdateTopicDTO request = new UpdateTopicDTO();
        request.setName("orders");
        request.setNamespace("trade");
        request.setWriteQueues(16);

        TopicVO topic = request.toTopicVO();

        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getNamespace()).isEqualTo("trade");
        assertThat(topic.getWriteQueues()).isEqualTo(16);
    }
}
