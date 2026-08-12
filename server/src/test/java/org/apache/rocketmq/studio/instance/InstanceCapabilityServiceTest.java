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
package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceCapabilityServiceTest {

    @Mock
    private InstanceRepository instanceRepository;
    @Mock
    private InstanceProviderRegistry providerRegistry;
    @Mock
    private InstanceProvider instanceProvider;

    private InstanceCapabilityService service;

    @BeforeEach
    void setUp() {
        service = new InstanceCapabilityService(instanceRepository, providerRegistry);
    }

    @Test
    void shouldReturnProviderCapabilitiesInStableOrder() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .type(InstanceType.PROXY)
                .build();
        instance.setId("inst-1");
        when(instanceRepository.findById("inst-1")).thenReturn(Optional.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.ALIYUN)).thenReturn(instanceProvider);
        when(instanceProvider.capabilities()).thenReturn(Set.of(
                InstanceCapability.MESSAGE_QUERY,
                InstanceCapability.TOPIC_MANAGEMENT));

        InstanceCapabilitiesVO result = service.getCapabilities("inst-1");

        assertThat(result.instanceId()).isEqualTo("inst-1");
        assertThat(result.vendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(result.accessType()).isEqualTo(InstanceType.PROXY);
        assertThat(result.capabilities()).containsExactly(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.MESSAGE_QUERY);
    }

    @Test
    void shouldDefaultLegacyNullVendorToApache() {
        InstanceVO instance = InstanceVO.builder().type(InstanceType.DIRECT).build();
        instance.setId("legacy");
        when(instanceRepository.findById("legacy")).thenReturn(Optional.of(instance));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(instanceProvider);
        when(instanceProvider.capabilities()).thenReturn(Set.of(InstanceCapability.DLQ_MANAGEMENT));

        assertThat(service.getCapabilities("legacy").vendor()).isEqualTo(InstanceVendor.APACHE);
    }

    @Test
    void shouldRejectUnknownInstance() {
        when(instanceRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCapabilities("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));
    }
}
