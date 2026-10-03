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
        when(clientProvider.scanProducerConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(ProducerConnectionScanResult.complete(List.of(producer)));

        ProducerConnectionResultVO result = producerConnectionService.listConnections(
                "instance-1", "order-topic", "pg-order");

        assertThat(result.getConnectionSet()).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-1");
            assertThat(connection.getClientAddr()).isEqualTo("10.0.0.1:38888");
            assertThat(connection.getTopic()).isEqualTo("order-topic");
            assertThat(connection.getProducerGroup()).isEqualTo("pg-order");
            assertThat(connection.getLanguage()).isEqualTo("Java");
            assertThat(connection.getVersionDesc()).isEqualTo("5.1.0");
        });
        assertThat(result.isComplete()).isTrue();
        verify(clientProvider).scanProducerConnections("instance-1", "order-topic", "pg-order");
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
        when(clientProvider.scanProducerConnections("instance-1", "order-topic", null))
                .thenReturn(ProducerConnectionScanResult.complete(List.of()));

        ProducerConnectionResultVO result =
                producerConnectionService.listConnections("instance-1", "order-topic", " ");

        assertThat(result.getConnectionSet()).isEmpty();
        verify(clientProvider).scanProducerConnections("instance-1", "order-topic", null);
    }

    @Test
    void listConnectionsShouldTrimRequiredValues() {
        when(clientProvider.scanProducerConnections("instance-1", "order-topic", "pg-order"))
                .thenReturn(ProducerConnectionScanResult.complete(List.of()));

        ProducerConnectionResultVO result = producerConnectionService.listConnections(
                " instance-1 ", " order-topic ", " pg-order ");
        assertThat(result.getConnectionSet()).isEmpty();
        verify(clientProvider).scanProducerConnections("instance-1", "order-topic", "pg-order");
    }

    @Test
    void listConnectionsShouldPreservePartialScanMetadataTest() {
        when(clientProvider.scanProducerConnections("instance-1", "order-topic", null))
                .thenReturn(new ProducerConnectionScanResult(
                        List.of(), List.of("broker-a:10911"), List.of("pg-orders")));

        ProducerConnectionResultVO result =
                producerConnectionService.listConnections("instance-1", "order-topic", null);

        assertThat(result.isComplete()).isFalse();
        assertThat(result.getFailedBrokers()).containsExactly("broker-a:10911");
        assertThat(result.getFailedProducerGroups()).containsExactly("pg-orders");
        assertThat(result.getSummary().getWarnings())
                .contains(ProducerConnectionSummaryVO.INCOMPLETE_SCAN);
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
}
