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

import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientProvider clientProvider;

    @Mock
    private NameserverRegistryService registryService;

    @InjectMocks
    private ClientService clientService;

    @Test
    void listConnectionsShouldTrimFiltersBeforeQueryingProvider() {
        ClientConnectionVO connection = ClientConnectionVO.builder()
                .clientId("client-1")
                .clusterName("production-cluster")
                .build();
        when(clientProvider.findConnections("instance-1", "production-cluster", "Producer")).thenReturn(List.of(connection));

        List<ClientConnectionVO> result = clientService.listConnections(" instance-1 ", " production-cluster ", " Producer ");

        assertThat(result).containsExactly(connection);
        verify(clientProvider).findConnections("instance-1", "production-cluster", "Producer");
    }

    @Test
    void listConnectionsShouldTreatBlankOptionalFiltersAsUnspecified() {
        when(clientProvider.findConnections("instance-1", null, null)).thenReturn(List.of());

        List<ClientConnectionVO> result = clientService.listConnections("instance-1", " ", "\t");

        assertThat(result).isEmpty();
        verify(clientProvider).findConnections("instance-1", null, null);
    }

    @Test
    void listConnectionsShouldRejectBlankInstanceIdBeforeQueryingProvider() {
        assertThatThrownBy(() -> clientService.listConnections(" ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("instanceId is required");

        verifyNoInteractions(clientProvider);
    }

    @Test
    void listConnectionsAtShouldTrimAndDelegateToProvider() {
        when(registryService.list()).thenReturn(List.of(NameserverRegistryVO.builder()
                .namesrvAddr("10.0.1.31:9876")
                .build()));
        when(clientProvider.findConnectionsAt("10.0.1.31:9876", "DefaultCluster", null))
                .thenReturn(List.of());

        List<ClientConnectionVO> result =
                clientService.listConnectionsAt(" 10.0.1.31:9876 ", " DefaultCluster ", " ");

        assertThat(result).isEmpty();
        verify(clientProvider).findConnectionsAt("10.0.1.31:9876", "DefaultCluster", null);
    }

    @Test
    void listConnectionsAtShouldNormalizeRegisteredAddressBeforeQueryingProvider() {
        when(registryService.list()).thenReturn(List.of(NameserverRegistryVO.builder()
                .namesrvAddr("ns1:9876,ns2:9876")
                .build()));

        clientService.listConnectionsAt(" NS1:9876 ; ns2:9876 ", null, null);

        verify(clientProvider).findConnectionsAt("ns1:9876,ns2:9876", null, null);
    }

    @Test
    void listConnectionsAtShouldRejectUnregisteredAddressBeforeQueryingProvider() {
        when(registryService.list()).thenReturn(List.of(NameserverRegistryVO.builder()
                .namesrvAddr("registered-ns:9876")
                .build()));

        assertThatThrownBy(() -> clientService.listConnectionsAt("other-ns:9876", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer address is not registered: other-ns:9876")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(404));

        verifyNoInteractions(clientProvider);
    }

    @Test
    void listConnectionsAtShouldRejectBlankNamesrvAddrBeforeQueryingProvider() {
        assertThatThrownBy(() -> clientService.listConnectionsAt(" ", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("namesrvAddr is required");

        verifyNoInteractions(clientProvider);
    }
}
