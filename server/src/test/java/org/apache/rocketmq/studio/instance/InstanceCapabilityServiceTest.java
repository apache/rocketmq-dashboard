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

import java.util.LinkedHashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstanceCapabilityServiceTest {

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private InstanceProviderRegistry providerRegistry;

    private InstanceCapabilityService service;

    @BeforeEach
    void setUp() {
        service = new InstanceCapabilityService(instanceRepository, providerRegistry);
    }

    @Test
    void rejectsUnknownInstances() {
        when(instanceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCapabilities(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Instance not found");
    }

    @Test
    void resolvesVendorCapabilitiesSortedByOrdinal() {
        InstanceVO instance = InstanceVO.builder()
                .name("inst-a")
                .vendor(InstanceVendor.TENCENT)
                .type(InstanceType.CLOUD)
                .build();
        when(instanceRepository.findById(7L)).thenReturn(Optional.of(instance));

        InstanceProvider provider = org.mockito.Mockito.mock(InstanceProvider.class);
        LinkedHashSet<InstanceCapability> unordered = new LinkedHashSet<>();
        unordered.add(InstanceCapability.CONSUMER_GROUP_MANAGEMENT);
        unordered.add(InstanceCapability.TOPIC_MANAGEMENT);
        when(provider.capabilities()).thenReturn(unordered);
        when(providerRegistry.forVendor(InstanceVendor.TENCENT)).thenReturn(provider);

        InstanceCapabilitiesVO result = service.getCapabilities(7L);

        assertThat(result.instanceId()).isEqualTo("inst-a");
        assertThat(result.vendor()).isEqualTo(InstanceVendor.TENCENT);
        assertThat(result.accessType()).isEqualTo(InstanceType.CLOUD);
        assertThat(result.capabilities()).containsExactly(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.CONSUMER_GROUP_MANAGEMENT);
    }
}
