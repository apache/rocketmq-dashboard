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
package com.rocketmq.studio.cluster.client;

import com.rocketmq.studio.common.domain.enums.ClientLanguage;
import com.rocketmq.studio.common.domain.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProducerConnectionServiceTest {

    @Mock
    private ClientService clientService;

    @InjectMocks
    private ProducerConnectionService producerConnectionService;

    @Test
    void listConnectionsShouldProjectProducerClientsByTopic() {
        ClientConnectionVO producer = ClientConnectionVO.builder()
                .clientId("producer-1")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .producerGroup("pg-order")
                .address("10.0.0.1:38888")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .build();
        ClientConnectionVO otherProducer = ClientConnectionVO.builder()
                .clientId("producer-2")
                .type(ClientType.Producer)
                .groupOrTopic("payment-topic")
                .producerGroup("pg-payment")
                .address("10.0.0.2:38888")
                .language(ClientLanguage.Go)
                .version("5.0.0")
                .build();
        when(clientService.listConnections(null, ClientType.Producer.name()))
                .thenReturn(List.of(producer, otherProducer));

        List<ProducerConnectionVO> result = producerConnectionService.listConnections("order-topic", "pg-order");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("producer-1");
        assertThat(result.get(0).getClientAddr()).isEqualTo("10.0.0.1:38888");
        assertThat(result.get(0).getLanguage()).isEqualTo("Java");
        assertThat(result.get(0).getVersionDesc()).isEqualTo("5.1.0");
        verify(clientService).listConnections(null, ClientType.Producer.name());
    }

    @Test
    void listConnectionsShouldFallbackToProducerGroupWhenTopicIsMissing() {
        ClientConnectionVO producer = ClientConnectionVO.builder()
                .clientId("producer-1")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .producerGroup("pg-order")
                .address("10.0.0.1:38888")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .build();
        when(clientService.listConnections(null, ClientType.Producer.name())).thenReturn(List.of(producer));

        List<ProducerConnectionVO> result = producerConnectionService.listConnections(null, "pg-order");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("producer-1");
    }

    @Test
    void listConnectionsShouldTrimFilterValues() {
        ClientConnectionVO producer = ClientConnectionVO.builder()
                .clientId("producer-1")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .producerGroup("pg-order")
                .address("10.0.0.1:38888")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .build();
        when(clientService.listConnections(null, ClientType.Producer.name())).thenReturn(List.of(producer));

        List<ProducerConnectionVO> result = producerConnectionService.listConnections(" order-topic ", " pg-order ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("producer-1");
    }

    @Test
    void listConnectionsShouldRequireProducerGroupWhenBothFiltersAreProvided() {
        ClientConnectionVO producer = ClientConnectionVO.builder()
                .clientId("producer-1")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .producerGroup("pg-order")
                .address("10.0.0.1:38888")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .build();
        when(clientService.listConnections(null, ClientType.Producer.name())).thenReturn(List.of(producer));

        List<ProducerConnectionVO> result = producerConnectionService.listConnections("order-topic", "wrong-group");

        assertThat(result).isEmpty();
    }
}
