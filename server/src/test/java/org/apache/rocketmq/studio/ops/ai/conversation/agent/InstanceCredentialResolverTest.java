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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.InstanceCredentialResolver.InstanceCredential;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The credential lookup shared by {@code McpAuthenticator} (which verifies a signature) and
 * {@code RmqctlWorkspace} (which injects the key pair into an agent's child process). Both sides must
 * agree, or every tool call the hosted agent makes would fail authentication.
 */
class InstanceCredentialResolverTest {

    private static final String INSTANCE_ID = "instance-a";
    private static final String ADMIN_ACCESS_KEY = " admin-ak ";
    private static final String ADMIN_SECRET_KEY = " admin-secret ";
    private static final String CLOUD_ACCESS_KEY = "cloud-ak";
    private static final String CLOUD_SECRET_KEY = "cloud-secret";

    private RuntimeAdminClientResolver adminClientResolver;
    private CloudCredentialRepository cloudCredentialRepository;
    private InstanceResolver instanceResolver;
    private InstanceCredentialResolver resolver;

    @BeforeEach
    void setUp() {
        adminClientResolver = mock(RuntimeAdminClientResolver.class);
        cloudCredentialRepository = mock(CloudCredentialRepository.class);
        instanceResolver = mock(InstanceResolver.class);
        resolver = new InstanceCredentialResolver(
                adminClientResolver, cloudCredentialRepository, instanceResolver);
    }

    private static InstanceVO instance(InstanceVendor vendor) {
        return InstanceVO.builder().name(INSTANCE_ID).vendor(vendor)
                .credentialId(7L).adminCredentialRef("admin").build();
    }

    private static MqAdminProperties.Credential adminCredential() {
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey(ADMIN_ACCESS_KEY);
        credential.setSecretKey(ADMIN_SECRET_KEY);
        return credential;
    }

    private CloudCredentialVO stubCloudCredential(String accessKey, String secretKey) {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setVendor(InstanceVendor.ALIYUN);
        credential.setAccessKey(accessKey);
        credential.setSecretKey(secretKey);
        when(cloudCredentialRepository.findById(7L)).thenReturn(Optional.of(credential));
        return credential;
    }

    @Test
    void resolvesApacheCredentialsFromTheAdminConfigurationTest() {
        InstanceVO instance = instance(InstanceVendor.APACHE);
        when(adminClientResolver.resolveCredential(instance)).thenReturn(adminCredential());

        InstanceCredential credential = resolver.resolve(instance);

        assertThat(credential.accessKey()).isEqualTo("admin-ak");
        assertThat(credential.secretKey()).isEqualTo("admin-secret");
        verify(adminClientResolver).resolveCredential(instance);
        verifyNoInteractions(cloudCredentialRepository, instanceResolver);
    }

    @Test
    void treatsAnInstanceWithoutVendorAsApacheTest() {
        InstanceVO instance = instance(null);
        when(adminClientResolver.resolveCredential(instance)).thenReturn(adminCredential());

        assertThat(resolver.resolve(instance).accessKey()).isEqualTo("admin-ak");
        verifyNoInteractions(cloudCredentialRepository);
    }

    @ParameterizedTest
    @EnumSource(value = InstanceVendor.class, names = "APACHE", mode = EnumSource.Mode.EXCLUDE)
    void resolvesCloudVendorCredentialsByReferenceTest(InstanceVendor vendor) {
        InstanceVO instance = instance(vendor);
        stubCloudCredential(CLOUD_ACCESS_KEY, CLOUD_SECRET_KEY);

        InstanceCredential credential = resolver.resolve(instance);

        assertThat(credential.accessKey()).isEqualTo(CLOUD_ACCESS_KEY);
        assertThat(credential.secretKey()).isEqualTo(CLOUD_SECRET_KEY);
        verify(cloudCredentialRepository).findById(7L);
        // A cloud tenant's key and the operator's admin key are different identities: an APACHE-style
        // fallback would authenticate one instance's traffic as another's.
        verifyNoInteractions(adminClientResolver);
    }

    @Test
    void rejectsACloudInstanceWithoutCredentialReferenceTest() {
        InstanceVO instance = instance(InstanceVendor.ALIYUN);
        instance.setCredentialId(null);

        assertThatThrownBy(() -> resolver.resolve(instance))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(422))
                .hasMessageContaining("no cloud credential reference");
        verifyNoInteractions(cloudCredentialRepository, adminClientResolver);
    }

    @Test
    void rejectsAMissingCloudCredentialRowTest() {
        when(cloudCredentialRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(instance(InstanceVendor.TENCENT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not available");
        verifyNoInteractions(adminClientResolver);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAnIncompleteCloudCredentialTest(String accessKey) {
        stubCloudCredential(accessKey, CLOUD_SECRET_KEY);

        assertThatThrownBy(() -> resolver.resolve(instance(InstanceVendor.ALIYUN)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(422))
                .hasMessageContaining("incomplete");

        stubCloudCredential(CLOUD_ACCESS_KEY, accessKey);
        assertThatThrownBy(() -> resolver.resolve(instance(InstanceVendor.ALIYUN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void propagatesTheAdminResolverConfigurationFailureTest() {
        InstanceVO instance = instance(InstanceVendor.APACHE);
        BusinessException configurationFailure =
                new BusinessException(422, "Admin credential reference is not configured: admin");
        when(adminClientResolver.resolveCredential(instance)).thenThrow(configurationFailure);

        assertThatThrownBy(() -> resolver.resolve(instance)).isSameAs(configurationFailure);
    }

    @Test
    void propagatesARepositoryOutageWithoutBlamingTheCallerTest() {
        // McpAuthenticator answers 500 for this and 401 for a bad credential; collapsing the two
        // would tell an attacker that the credential store is down, and would tell an operator that
        // their key is wrong when the database is.
        DataAccessResourceFailureException outage =
                new DataAccessResourceFailureException("Credential database unavailable");
        when(cloudCredentialRepository.findById(7L)).thenThrow(outage);

        assertThatThrownBy(() -> resolver.resolve(instance(InstanceVendor.ALIYUN))).isSameAs(outage);

        when(adminClientResolver.resolveCredential(any())).thenThrow(outage);
        assertThatThrownBy(() -> resolver.resolve(instance(InstanceVendor.APACHE))).isSameAs(outage);
    }

    @Test
    void rejectsANullInstanceTest() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(422));
        verifyNoInteractions(adminClientResolver, cloudCredentialRepository, instanceResolver);
    }

    @Test
    void resolveByNameLooksTheInstanceUpTheWayTheMcpAuthenticatorDoesTest() {
        InstanceVO instance = instance(InstanceVendor.APACHE);
        when(instanceResolver.findByName(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(adminClientResolver.resolveCredential(instance)).thenReturn(adminCredential());

        InstanceCredential credential = resolver.resolveByName(INSTANCE_ID);

        assertThat(credential.accessKey()).isEqualTo("admin-ak");
        // findByName, not findByIdentifier: McpAuthenticator resolves the x-rmq-instance-id header
        // with findByName, so injecting a credential resolved any other way could hand the agent a
        // key pair the server would then refuse to verify.
        verify(instanceResolver).findByName(INSTANCE_ID);
        verifyNoMoreInteractions(instanceResolver);
    }

    @Test
    void resolveByNameTrimsTheIdentifierTest() {
        InstanceVO instance = instance(InstanceVendor.APACHE);
        when(instanceResolver.findByName(INSTANCE_ID)).thenReturn(Optional.of(instance));
        when(adminClientResolver.resolveCredential(instance)).thenReturn(adminCredential());

        resolver.resolveByName("  " + INSTANCE_ID + " ");

        verify(instanceResolver).findByName(INSTANCE_ID);
    }

    @Test
    void resolveByNameRejectsAnUnknownInstanceTest() {
        when(instanceResolver.findByName(INSTANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveByName(INSTANCE_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(404))
                .hasMessageContaining(INSTANCE_ID);
        verifyNoInteractions(adminClientResolver, cloudCredentialRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void resolveByNameRejectsABlankIdentifierTest(String instanceId) {
        assertThatThrownBy(() -> resolver.resolveByName(instanceId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        verifyNoInteractions(instanceResolver, adminClientResolver, cloudCredentialRepository);
    }

    @Test
    void credentialMasksBothValuesWhenRenderedTest() {
        InstanceCredential credential = new InstanceCredential("ak-under-test", "sk-must-not-leak");

        String rendered = credential.toString();

        assertThat(rendered)
                .doesNotContain("sk-must-not-leak")
                .doesNotContain("ak-under-test")
                .contains("***");
        // The accessors still return the real values, otherwise nothing could sign with them.
        assertThat(credential.accessKey()).isEqualTo("ak-under-test");
        assertThat(credential.secretKey()).isEqualTo("sk-must-not-leak");
        assertThat(new InstanceCredential("ak", "sk").toString()).doesNotContain("ak");
    }
}
