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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApacheInstanceProviderTest {

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private MessageProvider messageProvider;

    @Mock
    private InstanceRepository instanceRepository;

    @InjectMocks
    private ApacheInstanceProvider provider;

    @Test
    void capabilitiesShouldIncludeApacheOnlyOperationsTest() {
        assertThat(provider.capabilities()).contains(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.CONSUMER_GROUP_MANAGEMENT,
                InstanceCapability.MESSAGE_QUERY,
                InstanceCapability.MESSAGE_TRACE,
                InstanceCapability.ACL_MANAGEMENT,
                InstanceCapability.DLQ_MANAGEMENT);
    }

    @Test
    void vendorShouldBeApacheTest() {
        assertThat(provider.vendor()).isEqualTo(InstanceVendor.APACHE);
    }

    @Test
    void countTopicsShouldDelegateToRepositoryTest() {
        InstanceVO instance = InstanceVO.builder().name("inst-1").build();
        instance.setId(1L);
        when(instanceRepository.findByIdentifier("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.countTopicsByInstance("inst-1")).thenReturn(3L);

        assertThat(provider.countTopics("inst-1")).isEqualTo(3);
    }

    @Test
    void countGroupsShouldDelegateToRepositoryTest() {
        InstanceVO instance = InstanceVO.builder().name("inst-1").build();
        instance.setId(1L);
        when(instanceRepository.findByIdentifier("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.countGroupsByInstance("inst-1")).thenReturn(2L);

        assertThat(provider.countGroups("inst-1")).isEqualTo(2);
    }

    @Test
    void listTopicsShouldPassTheSelectedInstanceToMetadataProvider() {
        when(metadataProvider.listTopics("inst-1", null, "FIFO", "orders")).thenReturn(java.util.List.of());

        assertThat(provider.listTopics("inst-1", "FIFO", "orders")).isEmpty();

        verify(metadataProvider).listTopics("inst-1", null, "FIFO", "orders");
    }

    @Test
    void listConsumerGroupsShouldPassTheSelectedInstanceToMetadataProvider() {
        when(metadataProvider.listConsumerGroups("inst-1", null, "orders")).thenReturn(java.util.List.of());

        assertThat(provider.listConsumerGroups("inst-1", "orders")).isEmpty();

        verify(metadataProvider).listConsumerGroups("inst-1", null, "orders");
    }
}
