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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class InstanceResolverTest {
    private final InstanceRepository repository = mock(InstanceRepository.class);
    private final RocketMQDefaultClusterResolver configured = mock(RocketMQDefaultClusterResolver.class);
    private final InstanceResolver resolver = new InstanceResolver(repository, configured);

    @Test
    void registeredNameWinsWithoutContactingConfiguredNameServer() {
        InstanceVO registered = InstanceVO.builder().name("DefaultCluster").endpoint("registered:9876").build();
        when(repository.findByName("DefaultCluster")).thenReturn(Optional.of(registered));
        when(repository.findByIdentifier("DefaultCluster")).thenReturn(Optional.of(registered));
        assertThat(resolver.findByName("DefaultCluster")).contains(registered);
        assertThat(resolver.findByIdentifier("DefaultCluster")).contains(registered);
        assertThat(resolver.resolveClusterName("DefaultCluster")).isNull();
        verifyNoInteractions(configured);
    }

    @Test
    void configuredTargetHasNoDatabaseIdentityAndUsesEmptyMetadataScope() {
        InstanceVO target = InstanceVO.builder().name("DefaultCluster").endpoint("configured:9876").build();
        when(configured.find("DefaultCluster")).thenReturn(Optional.of(target));
        assertThat(resolver.findByName("DefaultCluster")).contains(target);
        assertThat(target.getId()).isNull();
        assertThat(resolver.resolveClusterName("DefaultCluster")).isEqualTo("DefaultCluster");
    }

    @Test
    void numericPhysicalNameDoesNotTurnIntoAnUnrelatedDatabaseId() {
        InstanceVO registered = InstanceVO.builder().name("prod").build();
        InstanceVO physical = InstanceVO.builder().name("7").build();
        when(repository.findByIdentifier("7")).thenReturn(Optional.of(registered));
        when(configured.find("7")).thenReturn(Optional.of(physical));
        assertThat(resolver.findByIdentifier("7")).contains(physical);
        assertThat(resolver.resolveClusterName("7")).isEqualTo("7");
        when(configured.find("7")).thenReturn(Optional.empty());
        assertThat(resolver.findByIdentifier("7")).contains(registered);
    }

    @Test
    void nameLookupDoesNotAcceptDatabaseIdAliases() {
        InstanceVO registered = InstanceVO.builder().name("prod").build();
        registered.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(registered));
        when(repository.findByIdentifier("1")).thenReturn(Optional.of(registered));

        assertThat(resolver.findByName("1")).isEmpty();
        verify(repository).findByName("1");
        verifyNoMoreInteractions(repository);
        verify(configured).find("1");
    }

    @Test
    void numericRegisteredNameWinsOverConfiguredTarget() {
        InstanceVO registered = InstanceVO.builder().name("1").build();
        registered.setId(99L);
        when(repository.findByName("1")).thenReturn(Optional.of(registered));

        assertThat(resolver.findByName("1")).contains(registered);
        verify(repository).findByName("1");
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(configured);
    }

    @Test
    void missingConfiguredClusterCannotSilentlyAcquireARegisteredMetadataScope() {
        assertThatThrownBy(() -> resolver.resolveClusterName("gone"))
                .isInstanceOf(BusinessException.class).hasMessage("Instance not found: gone");
    }

}
