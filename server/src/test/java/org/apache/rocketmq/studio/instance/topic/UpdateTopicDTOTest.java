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
 * Pins the update contract of {@link UpdateTopicDTO}: every supplied field maps into
 * {@code toTopicVO()}, missing queue counts keep the view defaults, the topic name is required,
 * and queue counts must be zero or positive.
 */
class UpdateTopicDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateTopicDTO request) {
        Set<ConstraintViolation<UpdateTopicDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private UpdateTopicDTO validRequest() {
        UpdateTopicDTO request = new UpdateTopicDTO();
        request.setName("topic-1");
        return request;
    }

    @Test
    void toTopicVOMapsEverySuppliedField() {
        UpdateTopicDTO request = validRequest();
        request.setNamespace("ns-1");
        request.setClusterId("cluster-1");
        request.setType(TopicType.NORMAL);
        request.setWriteQueues(8);
        request.setReadQueues(16);
        request.setPerm(TopicPerm.RW);
        request.setRemark("created by test");

        TopicVO topic = request.toTopicVO();

        assertThat(topic.getName()).isEqualTo("topic-1");
        assertThat(topic.getNamespace()).isEqualTo("ns-1");
        assertThat(topic.getClusterId()).isEqualTo("cluster-1");
        assertThat(topic.getType()).isEqualTo(TopicType.NORMAL);
        assertThat(topic.getWriteQueues()).isEqualTo(8);
        assertThat(topic.getReadQueues()).isEqualTo(16);
        assertThat(topic.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(topic.getRemark()).isEqualTo("created by test");
    }

    @Test
    void missingQueueCountsKeepTheViewDefaults() {
        UpdateTopicDTO request = validRequest();
        TopicVO topic = request.toTopicVO();
        assertThat(topic.getWriteQueues()).isZero();
        assertThat(topic.getReadQueues()).isZero();
    }

    @Test
    void aBlankNameIsRejected() {
        UpdateTopicDTO blank = validRequest();
        blank.setName("   ");
        assertThat(violationsOf(blank)).containsExactly("name is required");

        UpdateTopicDTO missing = validRequest();
        missing.setName(null);
        assertThat(violationsOf(missing)).containsExactly("name is required");
    }

    @Test
    void negativeQueueCountsAreRejected() {
        UpdateTopicDTO negativeWrite = validRequest();
        negativeWrite.setWriteQueues(-1);
        assertThat(violationsOf(negativeWrite)).containsExactly("writeQueues must be zero or positive");

        UpdateTopicDTO negativeRead = validRequest();
        negativeRead.setReadQueues(-1);
        assertThat(violationsOf(negativeRead)).containsExactly("readQueues must be zero or positive");
    }

    @Test
    void zeroQueueCountsAreValid() {
        UpdateTopicDTO request = validRequest();
        request.setWriteQueues(0);
        request.setReadQueues(0);
        assertThat(violationsOf(request)).isEmpty();
    }
}
