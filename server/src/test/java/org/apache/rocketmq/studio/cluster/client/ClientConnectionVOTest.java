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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire shape of {@link ClientConnectionVO}: the connection timestamp is serialized as a
 * JSON string (its {@code @JsonFormat} shape — without it a LocalDateTime serializes as a field
 * array), and every identity field round-trips through the builder.
 */
class ClientConnectionVOTest {

    @Test
    void theConnectedAtTimestampIsSerializedAsAString() throws Exception {
        ClientConnectionVO connection = ClientConnectionVO.builder()
                .connectedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5))
                .build();

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(connection);

        // the @JsonFormat(STRING) shape: a quoted ISO timestamp, not a [2026,1,2,3,4,5] array
        assertThat(json).contains("\"connectedAt\":\"2026-01-02T03:04:05\"");
    }

    @Test
    void everyIdentityFieldRoundTripsThroughTheBuilder() {
        ClientConnectionVO connection = ClientConnectionVO.builder()
                .clientId("client-1")
                .groupOrTopic("topic-1")
                .producerGroup("group-1")
                .address("10.0.0.1:10920")
                .version("5.5.0")
                .clusterName("cluster-1")
                .partial(true)
                .build();

        assertThat(connection.getClientId()).isEqualTo("client-1");
        assertThat(connection.getGroupOrTopic()).isEqualTo("topic-1");
        assertThat(connection.getProducerGroup()).isEqualTo("group-1");
        assertThat(connection.getAddress()).isEqualTo("10.0.0.1:10920");
        assertThat(connection.getVersion()).isEqualTo("5.5.0");
        assertThat(connection.getClusterName()).isEqualTo("cluster-1");
        assertThat(connection.isPartial()).isTrue();
    }

    @Test
    void aFreshConnectionIsNotPartial() {
        assertThat(new ClientConnectionVO().isPartial()).isFalse();
    }
}
