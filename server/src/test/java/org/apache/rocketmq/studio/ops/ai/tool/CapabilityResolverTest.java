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
package org.apache.rocketmq.studio.ops.ai.tool;


import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityResolverTest {

    @Mock
    private InstanceProviderRegistry providerRegistry;
    @Mock
    private InstanceProvider provider;
    @Mock
    private InstanceResolver instanceResolver;

    private CapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CapabilityResolver(providerRegistry,
                instanceResolver);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "DIRECT|BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING",
        "PROXY_LOCAL|PROXY_DISCOVERY BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING",
        "PROXY_CLUSTER|PROXY_DISCOVERY BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING"
    }, delimiter = '|')
    void resolvesCapabilitiesForEveryApacheInstanceTypeTest(InstanceType type, String accessCapabilities) {
        when(instanceResolver.findByName("instance-a"))
                .thenReturn(Optional.of(instance(type, InstanceVendor.APACHE)));
        when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(provider);
        when(provider.capabilities()).thenReturn(Set.of(
                InstanceCapability.TOPIC_MANAGEMENT, InstanceCapability.MESSAGE_QUERY));

        Set<String> result = resolver.resolve("instance-a");

        assertThat(result).containsExactlyInAnyOrder(
                (accessCapabilities + " TOPIC_MANAGEMENT MESSAGE_QUERY").split(" "));
    }

    @ParameterizedTest
    @EnumSource(value = InstanceVendor.class, names = {"ALIYUN", "TENCENT"})
    void cloudTargetUsesCloudProtocolAndProviderCapabilitiesTest(InstanceVendor vendor) {
        when(instanceResolver.findByName("instance-a"))
                .thenReturn(Optional.of(instance(InstanceType.CLOUD, vendor)));
        when(providerRegistry.forVendor(vendor)).thenReturn(provider);
        when(provider.capabilities()).thenReturn(Set.of(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.MESSAGE_QUERY));

        Set<String> result = resolver.resolve("instance-a");

        assertThat(result).containsExactlyInAnyOrder("CLOUD_API", "TOPIC_MANAGEMENT", "MESSAGE_QUERY");
    }

    private static InstanceVO instance(InstanceType type, InstanceVendor vendor) {
        return InstanceVO.builder()
                .name("instance-a")
                .type(type)
                .vendor(vendor)
                .build();
    }

}
