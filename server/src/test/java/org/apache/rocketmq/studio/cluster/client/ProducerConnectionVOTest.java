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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the identity of {@link ProducerConnectionVO}, one row of the producer connections
 * table: the client id and address, the topic and producer group it feeds, and the language
 * and version the client reports. The sibling summary and result VOs have dedicated tests;
 * the row itself does not — this pins that all construction paths agree and the no-args path
 * leaves every column empty rather than defaulted.
 */
class ProducerConnectionVOTest {

    @Test
    void aConnectionCarriesItsFullIdentity() {
        ProducerConnectionVO connection = ProducerConnectionVO.builder()
                .clientId("producer@192.168.1.10")
                .clientAddr("/192.168.1.10:53412")
                .topic("order-topic")
                .producerGroup("order-producer-group")
                .language("JAVA")
                .versionDesc("v5_3_2")
                .build();

        assertThat(connection.getClientId()).isEqualTo("producer@192.168.1.10");
        assertThat(connection.getClientAddr()).isEqualTo("/192.168.1.10:53412");
        assertThat(connection.getTopic()).isEqualTo("order-topic");
        assertThat(connection.getProducerGroup()).isEqualTo("order-producer-group");
        assertThat(connection.getLanguage()).isEqualTo("JAVA");
        assertThat(connection.getVersionDesc()).isEqualTo("v5_3_2");
    }

    @Test
    void allConstructionPathsAgree() {
        ProducerConnectionVO allArgs = new ProducerConnectionVO(
                "producer@192.168.1.10", "/192.168.1.10:53412", "order-topic",
                "order-producer-group", "JAVA", "v5_3_2");
        ProducerConnectionVO built = ProducerConnectionVO.builder()
                .clientId("producer@192.168.1.10")
                .clientAddr("/192.168.1.10:53412")
                .topic("order-topic")
                .producerGroup("order-producer-group")
                .language("JAVA")
                .versionDesc("v5_3_2")
                .build();

        assertThat(allArgs).isEqualTo(built);

        ProducerConnectionVO empty = new ProducerConnectionVO();
        assertThat(empty.getClientId()).isNull();
        assertThat(empty.getClientAddr()).isNull();
        assertThat(empty.getTopic()).isNull();
        assertThat(empty.getProducerGroup()).isNull();
        assertThat(empty.getLanguage()).isNull();
        assertThat(empty.getVersionDesc()).isNull();
    }
}
