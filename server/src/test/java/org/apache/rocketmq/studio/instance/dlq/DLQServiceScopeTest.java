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
package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DLQServiceScopeTest {

    @Mock
    private DLQProvider dlqProvider;

    @Mock
    private InstanceProviderRegistry providerRegistry;

    private DLQService service;

    @BeforeEach
    void setUp() {
        service = new DLQService(dlqProvider, providerRegistry);
    }

    @Test
    void rejectsDlqOperationsForCloudInstances() {
        InstanceProvider cloud = org.mockito.Mockito.mock(InstanceProvider.class);
        when(cloud.vendor()).thenReturn(InstanceVendor.TENCENT);
        when(providerRegistry.byInstanceId("cloud-a")).thenReturn(Optional.of(cloud));

        assertThatThrownBy(() -> service.listDLQGroups("cloud-a", null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void allowsDlqOperationsWhenInstanceIsApacheOrUnknown() {
        when(providerRegistry.byInstanceId("apache-a")).thenReturn(Optional.empty());
        when(dlqProvider.listDLQGroups("apache-a", null, 1, 20)).thenReturn(
                org.apache.rocketmq.studio.common.domain.PageResult.of(List.of(), 0L, 1, 20));

        service.listDLQGroups("apache-a", null, 1, 20);

        verify(dlqProvider).listDLQGroups("apache-a", null, 1, 20);
    }

    @Test
    void pagesAndSearchAreValidatedBeforeProviderCalls() {
        when(providerRegistry.byInstanceId("apache-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listDLQGroups("apache-a", null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> service.listDLQGroups("apache-a", null, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pageSize");
    }
}
