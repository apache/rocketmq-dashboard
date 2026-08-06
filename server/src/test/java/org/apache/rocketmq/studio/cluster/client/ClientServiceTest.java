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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientProvider clientProvider;

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
    void listConnectionsShouldTreatBlankFiltersAsUnspecified() {
        when(clientProvider.findConnections(null, null, null)).thenReturn(List.of());

        List<ClientConnectionVO> result = clientService.listConnections(" ", " ", "\t");

        assertThat(result).isEmpty();
        verify(clientProvider).findConnections(null, null, null);
    }
}
