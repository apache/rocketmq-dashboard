/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
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
    private InstanceResolver instanceRepository;

    private CapabilityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CapabilityResolver(providerRegistry,
                instanceRepository);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "DIRECT|BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING",
        "PROXY_LOCAL|PROXY_DISCOVERY BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING",
        "PROXY_CLUSTER|PROXY_DISCOVERY BROKER_ADMIN CLUSTER_TOPOLOGY NAMESERVER_ADMIN REMOTING"
    }, delimiter = '|')
    void resolvesCapabilitiesForEveryApacheInstanceTypeTest(InstanceType type, String accessCapabilities) {
        when(instanceRepository.findByName("instance-a"))
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
    void cloudTargetUsesCloudProtocolAndProviderCapabilities(InstanceVendor vendor) {
        when(instanceRepository.findByName("instance-a"))
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
