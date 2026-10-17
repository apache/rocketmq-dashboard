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

package org.apache.rocketmq.studio.instance.message;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the direct-consume request of {@link DirectConsumeMessageDTO}: consuming a message
 * straight from the dashboard needs the full address of the message and of the consuming
 * client — instance, topic, message id, consumer group and client id are all required, with
 * each miss producing its own message instead of a generic 400.
 */
class DirectConsumeMessageDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(DirectConsumeMessageDTO request) {
        Set<ConstraintViolation<DirectConsumeMessageDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private DirectConsumeMessageDTO completeRequest() {
        DirectConsumeMessageDTO request = new DirectConsumeMessageDTO();
        request.setInstanceId("instance-1");
        request.setTopic("order-topic");
        request.setMsgId("AC11000100001A3B0000000000000000");
        request.setConsumerGroup("order-consumer-group");
        request.setClientId("consumer@192.168.1.20");
        return request;
    }

    @Test
    void aCompleteRequestPassesValidation() {
        assertThat(violationsOf(completeRequest())).isEmpty();
    }

    @Test
    void everyFieldCarriesItsOwnRequiredMessage() {
        DirectConsumeMessageDTO request = new DirectConsumeMessageDTO();
        assertThat(violationsOf(request)).containsExactlyInAnyOrder(
                "instanceId is required",
                "topic is required",
                "msgId is required",
                "consumerGroup is required",
                "clientId is required");

        DirectConsumeMessageDTO missingTopic = completeRequest();
        missingTopic.setTopic("   ");
        assertThat(violationsOf(missingTopic)).containsExactly("topic is required");

        DirectConsumeMessageDTO missingClient = completeRequest();
        missingClient.setClientId("");
        assertThat(violationsOf(missingClient)).containsExactly("clientId is required");
    }
}
