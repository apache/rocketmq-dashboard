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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeAdminClientResolverTest {

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private MqAdminExtFactory adminFactory;

    @Test
    void resolvesTrimmedEndpointFromSelectedInstance() {
        InstanceVO instance = InstanceVO.builder().endpoint(" namesrv-a:9876 ").build();
        instance.setId("instance-a");
        when(instanceRepository.findById("instance-a")).thenReturn(Optional.of(instance));

        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory);

        assertThat(resolver.resolveEndpoint("instance-a")).isEqualTo("namesrv-a:9876");
    }

    @Test
    void rejectsUnknownOrUnconfiguredInstances() {
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory);
        when(instanceRepository.findById("missing")).thenReturn(Optional.empty());
        InstanceVO noEndpoint = InstanceVO.builder().endpoint(" ").build();
        when(instanceRepository.findById("no-endpoint")).thenReturn(Optional.of(noEndpoint));

        assertThatThrownBy(() -> resolver.resolveEndpoint("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance not found: missing");
        assertThatThrownBy(() -> resolver.resolveEndpoint("no-endpoint"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance has no endpoint: no-endpoint");
    }

    @Test
    void executesAgainstTheSelectedInstanceEndpoint() {
        InstanceVO instance = InstanceVO.builder().endpoint("namesrv-b:9876").build();
        when(instanceRepository.findById("instance-b")).thenReturn(Optional.of(instance));
        when(adminFactory.execute(eq("namesrv-b:9876"), isNull(), any())).thenReturn("done");
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory);

        String result = resolver.execute("instance-b", admin -> "unused");
        assertThat(result).isEqualTo("done");
        verify(adminFactory).execute(eq("namesrv-b:9876"), isNull(), any());
    }

    @Test
    void rejectsCloudInstancesBeforeResolvingOrExecutingAdminClient() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .endpoint("cloud-endpoint:9876")
                .build();
        instance.setId("cloud-instance");
        when(instanceRepository.findById("cloud-instance")).thenReturn(Optional.of(instance));
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory);

        assertThatThrownBy(() -> resolver.resolveEndpoint("cloud-instance"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Runtime AdminClient only supports Apache instances: cloud-instance");
        assertThatThrownBy(() -> resolver.execute(instance, admin -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Runtime AdminClient only supports Apache instances: cloud-instance");
        verifyNoInteractions(adminFactory);
    }
}
