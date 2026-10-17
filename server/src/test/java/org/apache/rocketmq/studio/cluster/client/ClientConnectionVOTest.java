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
package org.apache.rocketmq.studio.cluster.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the client connection row of {@link ClientConnectionVO}: the identity with type,
 * protocol and language, and the connectedAt timestamp serialised as a JSON string (its
 * @JsonFormat shape) - without the annotation a LocalDateTime serialises as a field array no
 * client can parse.
 */
class ClientConnectionVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        ClientConnectionVO connection = ClientConnectionVO.builder()
                .clientId("consumer-1")
                .type(ClientType.Consumer)
                .groupOrTopic("order-group")
                .producerGroup(null)
                .protocol(Protocol.gRPC)
                .address("10.0.0.1:10920")
                .language(ClientLanguage.Java)
                .version("5.5.0")
                .connectedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .build();

        assertThat(connection.getClientId()).isEqualTo("consumer-1");
        assertThat(connection.getType()).isEqualTo(ClientType.Consumer);
        assertThat(connection.getGroupOrTopic()).isEqualTo("order-group");
        assertThat(connection.getProtocol()).isEqualTo(Protocol.gRPC);
        assertThat(connection.getAddress()).isEqualTo("10.0.0.1:10920");
        assertThat(connection.getLanguage()).isEqualTo(ClientLanguage.Java);
        assertThat(connection.getVersion()).isEqualTo("5.5.0");
    }

    @Test
    void theConnectedAtTimestampIsSerialisedAsAString() throws Exception {
        ClientConnectionVO connection = ClientConnectionVO.builder()
                .clientId("consumer-1")
                .connectedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String json = mapper.writeValueAsString(connection);

        // the @JsonFormat STRING shape, not the [2026,1,2,3,4,5] field array
        assertThat(json).contains("\"connectedAt\":\"2026-01-02T03:04:05\"");
    }
}
