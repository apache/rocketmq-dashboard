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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the online consumer instance row of {@link ConsumerInstanceVO}: the client identity, its
 * protocol and address, the subscribed topics, the last heartbeat and the per-topic lag map the
 * group detail renders.
 */
class ConsumerInstanceVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        ConsumerInstanceVO instance = ConsumerInstanceVO.builder()
                .clientId("consumer-1")
                .protocol(Protocol.gRPC)
                .address("10.0.0.1:10920")
                .subscribedTopics(List.of("orders", "payments"))
                .lastHeartbeat(LocalDateTime.of(2026, 1, 2, 3, 4))
                .topicLag(Map.of("orders", 100L))
                .build();

        assertThat(instance.getClientId()).isEqualTo("consumer-1");
        assertThat(instance.getProtocol()).isEqualTo(Protocol.gRPC);
        assertThat(instance.getAddress()).isEqualTo("10.0.0.1:10920");
        assertThat(instance.getSubscribedTopics()).containsExactly("orders", "payments");
        assertThat(instance.getLastHeartbeat()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(instance.getTopicLag()).containsEntry("orders", 100L);
    }

    @Test
    void aFreshInstanceCarriesNoSubscriptions() {
        ConsumerInstanceVO instance = new ConsumerInstanceVO();
        assertThat(instance.getSubscribedTopics()).isNull();
        assertThat(instance.getTopicLag()).isNull();
        assertThat(instance.getLastHeartbeat()).isNull();
    }
}
