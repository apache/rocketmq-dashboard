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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;
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

        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                new MqAdminProperties());

        assertThat(resolver.resolveEndpoint("instance-a")).isEqualTo("namesrv-a:9876");
    }

    @Test
    void rejectsUnknownOrUnconfiguredInstances() {
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                new MqAdminProperties());
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
        when(adminFactory.execute(eq("namesrv-b:9876"), isNull(), isNull(), any())).thenReturn("done");
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                new MqAdminProperties());

        String result = resolver.execute("instance-b", admin -> "unused");
        assertThat(result).isEqualTo("done");
        verify(adminFactory).execute(eq("namesrv-b:9876"), isNull(), isNull(), any());
    }

    @Test
    void rejectsCloudInstancesBeforeResolvingOrExecutingAdminClient() {
        InstanceVO instance = InstanceVO.builder()
                .vendor(InstanceVendor.ALIYUN)
                .endpoint("cloud-endpoint:9876")
                .build();
        instance.setId("cloud-instance");
        when(instanceRepository.findById("cloud-instance")).thenReturn(Optional.of(instance));
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                new MqAdminProperties());

        assertThatThrownBy(() -> resolver.resolveEndpoint("cloud-instance"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Runtime AdminClient only supports Apache instances: cloud-instance");
        assertThatThrownBy(() -> resolver.execute(instance, admin -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Runtime AdminClient only supports Apache instances: cloud-instance");
        verifyNoInteractions(adminFactory);
    }

    @Test
    void executesWithTheSelectedInstanceCredentialReference() {
        InstanceVO instance = InstanceVO.builder()
                .endpoint("namesrv-b:9876")
                .adminCredentialRef(" production-admin ")
                .build();
        instance.setId("instance-b");
        MqAdminProperties properties = new MqAdminProperties();
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("admin-ak");
        credential.setSecretKey("admin-sk");
        properties.getCredentials().put("production-admin", credential);
        when(instanceRepository.findById("instance-b")).thenReturn(Optional.of(instance));
        when(adminFactory.execute(eq("namesrv-b:9876"), any(), eq("production-admin"), any()))
                .thenReturn("done");
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                properties);

        String result = resolver.execute("instance-b", ignored -> "unused");

        assertThat(result).isEqualTo("done");
        ArgumentCaptor<org.apache.rocketmq.remoting.RPCHook> hookCaptor = ArgumentCaptor.forClass(
                org.apache.rocketmq.remoting.RPCHook.class);
        verify(adminFactory).execute(eq("namesrv-b:9876"), hookCaptor.capture(), eq("production-admin"), any());
        org.apache.rocketmq.acl.common.AclClientRPCHook resolvedHook =
                (org.apache.rocketmq.acl.common.AclClientRPCHook) hookCaptor.getValue();
        assertThat(resolvedHook.getSessionCredentials().getAccessKey()).isEqualTo("admin-ak");
        assertThat(resolvedHook.getSessionCredentials().getSecretKey()).isEqualTo("admin-sk");
    }

    @Test
    void rejectsUnknownOrIncompleteCredentialReferencesBeforeNetworkCalls() {
        MqAdminProperties properties = new MqAdminProperties();
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("admin-ak");
        credential.setSecretKey("admin-sk");
        properties.getCredentials().put("production-admin", credential);
        MqAdminProperties.Credential incomplete = new MqAdminProperties.Credential();
        incomplete.setAccessKey("admin-ak");
        properties.getCredentials().put("incomplete", incomplete);
        InstanceVO instance = InstanceVO.builder().endpoint("namesrv-b:9876")
                .adminCredentialRef("missing").build();
        instance.setId("instance-b");
        when(instanceRepository.findById("instance-b")).thenReturn(Optional.of(instance));
        RuntimeAdminClientResolver resolver = new RuntimeAdminClientResolver(instanceRepository, adminFactory,
                properties);

        assertThatThrownBy(() -> resolver.execute("instance-b", ignored -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Admin credential reference is not configured: missing")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(422));
        instance.setAdminCredentialRef("incomplete");
        assertThatThrownBy(() -> resolver.execute("instance-b", ignored -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Admin credential reference is not configured: incomplete");
        verifyNoInteractions(adminFactory);
    }

    @Test
    void bindsReferencedCredentialsFromExternalizedConfiguration() {
        MqAdminProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                        "studio.cluster.admin.credentials.production-admin.access-key", "admin-ak",
                        "studio.cluster.admin.credentials.production-admin.secret-key", "admin-sk")))
                .bind("studio.cluster.admin", Bindable.of(MqAdminProperties.class)).get();

        MqAdminProperties.Credential credential = properties.getCredentials().get("production-admin");

        assertThat(credential.getAccessKey()).isEqualTo("admin-ak");
        assertThat(credential.getSecretKey()).isEqualTo("admin-sk");
    }
}
