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
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create contract of {@link CreateTopicDTO}: the topic name is required, the queue
 * counts are zero-or-positive, and {@code toTopicVO()} only overrides the view's queue counts
 * when the request actually carries them.
 */
class CreateTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateTopicDTO request) {
        Set<ConstraintViolation<CreateTopicDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aMinimalCreateIsValid() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theNameIsRequired() {
        assertThat(violationsOf(new CreateTopicDTO())).containsExactly("name is required");

        CreateTopicDTO blank = new CreateTopicDTO();
        blank.setName("   ");
        assertThat(violationsOf(blank)).containsExactly("name is required");
    }

    @Test
    void queueCountsAreZeroOrPositive() {
        CreateTopicDTO negativeWrite = new CreateTopicDTO();
        negativeWrite.setName("orders");
        negativeWrite.setWriteQueues(-1);
        assertThat(violationsOf(negativeWrite)).containsExactly("writeQueues must be zero or positive");

        CreateTopicDTO negativeRead = new CreateTopicDTO();
        negativeRead.setName("orders");
        negativeRead.setReadQueues(-1);
        assertThat(violationsOf(negativeRead)).containsExactly("readQueues must be zero or positive");

        CreateTopicDTO zero = new CreateTopicDTO();
        zero.setName("orders");
        zero.setWriteQueues(0);
        zero.setReadQueues(0);
        assertThat(violationsOf(zero)).isEmpty();
    }

    @Test
    void toTopicVOMapsEveryFieldAndOmitsAbsentQueueCounts() {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName("orders");
        request.setNamespace("ns-1");
        request.setClusterId("cluster-1");
        request.setType(TopicType.FIFO);
        request.setWriteQueues(8);
        request.setReadQueues(16);
        request.setPerm(TopicPerm.RW);
        request.setRemark("created by test");

        TopicVO topic = request.toTopicVO();

        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getNamespace()).isEqualTo("ns-1");
        assertThat(topic.getClusterId()).isEqualTo("cluster-1");
        assertThat(topic.getType()).isEqualTo(TopicType.FIFO);
        assertThat(topic.getWriteQueues()).isEqualTo(8);
        assertThat(topic.getReadQueues()).isEqualTo(16);
        assertThat(topic.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(topic.getRemark()).isEqualTo("created by test");

        // an absent queue count leaves the view's default instead of zeroing it
        CreateTopicDTO minimal = new CreateTopicDTO();
        minimal.setName("orders");
        assertThat(minimal.toTopicVO().getWriteQueues()).isZero();
    }
}
