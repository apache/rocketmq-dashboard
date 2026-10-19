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

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the send contract of {@link SendMessageDTO}: the topic is the only required field, and
 * every optional routing and payload field (tag, key, properties, FIFO message group, DELAY
 * delivery timestamp) round-trips through the builder.
 */
class SendMessageDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(SendMessageDTO request) {
        Set<ConstraintViolation<SendMessageDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theTopicIsRequired() {
        SendMessageDTO missing = SendMessageDTO.builder().build();
        assertThat(violationsOf(missing)).containsExactly("topic is required");

        SendMessageDTO blank = SendMessageDTO.builder().topic("   ").build();
        assertThat(violationsOf(blank)).containsExactly("topic is required");

        SendMessageDTO minimal = SendMessageDTO.builder().topic("topic-1").build();
        assertThat(violationsOf(minimal)).isEmpty();
    }

    @Test
    void everyOptionalFieldRoundTrips() {
        SendMessageDTO request = SendMessageDTO.builder()
                .instanceId("instance-1")
                .topic("topic-1")
                .tag("tag-1")
                .key("order-42")
                .body("payload")
                .properties(Map.of("traceId", "t-1"))
                .messageGroup("orders")
                .deliveryTimestamp(1784114406_000L)
                .build();

        assertThat(request.getInstanceId()).isEqualTo("instance-1");
        assertThat(request.getTopic()).isEqualTo("topic-1");
        assertThat(request.getTag()).isEqualTo("tag-1");
        assertThat(request.getKey()).isEqualTo("order-42");
        assertThat(request.getBody()).isEqualTo("payload");
        assertThat(request.getProperties()).containsEntry("traceId", "t-1");
        assertThat(request.getMessageGroup()).isEqualTo("orders");
        assertThat(request.getDeliveryTimestamp()).isEqualTo(1784114406_000L);
    }
}
