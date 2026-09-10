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
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingNameIsRejectedTest() {
        CreateTopicDTO request = new CreateTopicDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("name is required");
    }

    @Test
    void negativeQueuesAreRejectedTest() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        request.setWriteQueues(-1);
        request.setReadQueues(-1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "writeQueues must be zero or positive",
                        "readQueues must be zero or positive");
    }

    @Test
    void completeRequestPassesValidationAndMapsTest() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        request.setNamespace("prod");
        request.setType(TopicType.NORMAL);
        request.setWriteQueues(8);
        request.setReadQueues(8);
        request.setPerm(TopicPerm.RW);
        request.setRemark("created");

        assertThat(validator.validate(request)).isEmpty();

        TopicVO vo = request.toTopicVO();
        assertThat(vo.getName()).isEqualTo("orders");
        assertThat(vo.getType()).isEqualTo(TopicType.NORMAL);
        assertThat(vo.getWriteQueues()).isEqualTo(8);
        assertThat(vo.getReadQueues()).isEqualTo(8);
        assertThat(vo.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(vo.getRemark()).isEqualTo("created");
    }
}
