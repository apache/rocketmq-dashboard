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
package org.apache.rocketmq.studio.proxyadmin;

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyAdminControllerTest {

    @Mock
    private ProxyAdminClient proxyAdminClient;

    @Mock
    private InstanceRepository instanceRepository;

    private ProxyAdminController controller;

    private static final String PROXY_INSTANCE_ID = "proxy-instance-1";
    private static final String ADMIN_ENDPOINT = "127.0.0.1:8083";

    @BeforeEach
    void setUp() {
        controller = new ProxyAdminController(proxyAdminClient, instanceRepository);
    }

    private InstanceVO proxyInstance() {
        InstanceVO instance = InstanceVO.builder()
                .name("proxy")
                .type(InstanceType.PROXY)
                .endpoint(ADMIN_ENDPOINT)
                .build();
        instance.setId(PROXY_INSTANCE_ID);
        return instance;
    }

    @Test
    void routeTopologyResolvesProxyEndpointAndDelegates() {
        when(instanceRepository.findById(PROXY_INSTANCE_ID)).thenReturn(Optional.of(proxyInstance()));
        ProxyAdminDiagnosticsVO.RouteTopology topology = ProxyAdminDiagnosticsVO.RouteTopology.builder()
                .topic("topic-a").links(List.of()).load(List.of()).build();
        when(proxyAdminClient.describeRouteTopology(ADMIN_ENDPOINT, "topic-a")).thenReturn(topology);

        ProxyAdminDiagnosticsVO.RouteTopology result = controller.routeTopology(PROXY_INSTANCE_ID, "topic-a").getData();

        assertThat(result).isSameAs(topology);
        verify(proxyAdminClient).describeRouteTopology(ADMIN_ENDPOINT, "topic-a");
    }

    @Test
    void popReceiptHandlesDelegateWithPagination() {
        when(instanceRepository.findById(PROXY_INSTANCE_ID)).thenReturn(Optional.of(proxyInstance()));
        ProxyAdminDiagnosticsVO.PopReceiptHandles handles = ProxyAdminDiagnosticsVO.PopReceiptHandles.builder()
                .total(3).pageNum(1).pageSize(20).handles(List.of()).build();
        when(proxyAdminClient.describePopReceiptHandles(ADMIN_ENDPOINT, "group-a", null, 1, 20))
                .thenReturn(handles);

        ProxyAdminDiagnosticsVO.PopReceiptHandles result = controller
                .popReceiptHandles(PROXY_INSTANCE_ID, "group-a", null, 1, 20).getData();

        assertThat(result.getTotal()).isEqualTo(3);
        verify(proxyAdminClient).describePopReceiptHandles(ADMIN_ENDPOINT, "group-a", null, 1, 20);
    }

    @Test
    void batchConsumeDiagnosticsDelegate() {
        when(instanceRepository.findById(PROXY_INSTANCE_ID)).thenReturn(Optional.of(proxyInstance()));
        ProxyAdminDiagnosticsVO.BatchConsumeDiagnostics diagnostics =
                ProxyAdminDiagnosticsVO.BatchConsumeDiagnostics.builder().total(1).diagnostics(List.of()).build();
        when(proxyAdminClient.describeBatchConsumeDiagnostics(ADMIN_ENDPOINT, "group-a", null, null, 1, 20))
                .thenReturn(diagnostics);

        ProxyAdminDiagnosticsVO.BatchConsumeDiagnostics result = controller
                .batchConsumeDiagnostics(PROXY_INSTANCE_ID, "group-a", null, null, 1, 20).getData();

        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    void routeEventsSplitTopicsAndDelegate() {
        when(instanceRepository.findById(PROXY_INSTANCE_ID)).thenReturn(Optional.of(proxyInstance()));
        when(proxyAdminClient.collectRouteEvents(eq(ADMIN_ENDPOINT), anyList(), anyLong(), anyInt()))
                .thenReturn(List.of());

        controller.routeEvents(PROXY_INSTANCE_ID, "topic-a, topic-b", 3, 50);

        verify(proxyAdminClient).collectRouteEvents(eq(ADMIN_ENDPOINT),
                eq(List.of("topic-a", "topic-b")), eq(3L), eq(50));
    }

    @Test
    void rejectsNonProxyInstance() {
        InstanceVO direct = InstanceVO.builder().type(InstanceType.DIRECT)
                .endpoint("127.0.0.1:9876").build();
        direct.setId("d1");
        when(instanceRepository.findById("d1")).thenReturn(Optional.of(direct));

        assertThatThrownBy(() -> controller.routeTopology("d1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PROXY");
        verify(proxyAdminClient, never()).describeRouteTopology(anyString(), any());
    }

    @Test
    void rejectsUnknownInstance() {
        when(instanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.popReceiptHandles("missing", "group-a", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsBlankInstanceId() {
        assertThatThrownBy(() -> controller.routeTopology(" ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("instanceId");
    }

    @Test
    void rejectsProxyInstanceWithoutEndpoint() {
        InstanceVO noEndpoint = InstanceVO.builder().type(InstanceType.PROXY).endpoint("").build();
        noEndpoint.setId("p2");
        when(instanceRepository.findById("p2")).thenReturn(Optional.of(noEndpoint));

        assertThatThrownBy(() -> controller.routeTopology("p2", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint");
    }
}
