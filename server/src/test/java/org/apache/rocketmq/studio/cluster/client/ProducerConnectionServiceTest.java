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

import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProducerConnectionServiceTest {

    @Mock
    private ClientProvider clientProvider;

    @InjectMocks
    private ProducerConnectionService producerConnectionService;

    @Test
    void listConnectionsShouldQueryAndProjectExactProducerGroup() {
        ClientConnectionVO producer = ClientConnectionVO.builder()
                .clientId("producer-1")
                .type(ClientType.Producer)
                .groupOrTopic("order-topic")
                .producerGroup("pg-order")
                .address("10.0.0.1:38888")
                .language(ClientLanguage.Java)
                .version("5.1.0")
                .build();
        when(clientProvider.findProducerConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(List.of(producer));

        List<ProducerConnectionVO> result = producerConnectionService.listConnections(
                "instance-1", "order-topic", "pg-order");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientId()).isEqualTo("producer-1");
        assertThat(result.get(0).getClientAddr()).isEqualTo("10.0.0.1:38888");
        assertThat(result.get(0).getTopic()).isEqualTo("order-topic");
        assertThat(result.get(0).getProducerGroup()).isEqualTo("pg-order");
        assertThat(result.get(0).getLanguage()).isEqualTo("Java");
        assertThat(result.get(0).getVersionDesc()).isEqualTo("5.1.0");
        verify(clientProvider).findProducerConnections("instance-1", "order-topic", "pg-order");
    }

    @Test
    void listConnectionsShouldRejectMissingTopic() {
        assertThatThrownBy(() -> producerConnectionService.listConnections("instance-1", " ", "pg-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verifyNoInteractions(clientProvider);
    }

    @Test
    void listConnectionsShouldAllowMissingProducerGroupForAllGroupScan() {
        when(clientProvider.findProducerConnections("instance-1", "order-topic", null))
                .thenReturn(List.of());

        List<ProducerConnectionVO> result =
                producerConnectionService.listConnections("instance-1", "order-topic", " ");

        assertThat(result).isEmpty();
        verify(clientProvider).findProducerConnections("instance-1", "order-topic", null);
    }

    @Test
    void listConnectionsShouldTrimRequiredValues() {
        when(clientProvider.findProducerConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(List.of());

        List<ProducerConnectionVO> result = producerConnectionService.listConnections(
                " instance-1 ", " order-topic ", " pg-order ");
        assertThat(result).isEmpty();
        verify(clientProvider).findProducerConnections("instance-1", "order-topic", "pg-order");
    }

    @Test
    void listProducerGroupsShouldDelegateSelectorDiscoveryWithNormalizedFilters() {
        when(clientProvider.findProducerGroups("instance-1", "order-topic", "pg", 100))
                .thenReturn(List.of("pg-order", "pg-payment"));

        assertThat(producerConnectionService.listProducerGroups(" instance-1 ", " order-topic ", " pg ", 1000))
                .containsExactly("pg-order", "pg-payment");
        verify(clientProvider).findProducerGroups("instance-1", "order-topic", "pg", 100);
    }

    @Test
    void listProducerGroupsShouldApplyDefaultSelectorLimit() {
        when(clientProvider.findProducerGroups("instance-1", null, null, 20))
                .thenReturn(List.of("pg-order"));

        assertThat(producerConnectionService.listProducerGroups("instance-1", " ", " ", null))
                .containsExactly("pg-order");
        verify(clientProvider).findProducerGroups("instance-1", null, null, 20);
    }

    @Test
    void listConnectionsShouldRejectMissingInstanceId() {
        assertThatThrownBy(() -> producerConnectionService.listConnections(null, "order-topic", "pg-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("instanceId is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verifyNoInteractions(clientProvider);
    }

    @Test
    void listConnectionsShouldTreatNullProviderResultAsEmpty() {
        when(clientProvider.findProducerConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(null);

        List<ProducerConnectionVO> result = producerConnectionService.listConnections(
                "instance-1", "order-topic", "pg-order");

        assertThat(result).isEmpty();
        verify(clientProvider).findProducerConnections("instance-1", "order-topic", "pg-order");
    }

    @Test
    void listProducerGroupsShouldTreatNullProviderResultAsEmpty() {
        when(clientProvider.findProducerGroups("instance-1", null, null, 20)).thenReturn(null);

        List<String> result = producerConnectionService.listProducerGroups("instance-1", null, null, null);

        assertThat(result).isEmpty();
        verify(clientProvider).findProducerGroups("instance-1", null, null, 20);
    }
}
