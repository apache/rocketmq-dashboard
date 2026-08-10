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
package org.apache.rocketmq.studio.provider.credential;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.provider.alibaba.AliyunClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudCredentialServiceTest {

    @Mock
    private CloudCredentialRepository credentialRepository;

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private AliyunClientFactory aliyunClientFactory;

    @Mock
    private OperationAuditService operationAuditService;

    @InjectMocks
    private CloudCredentialService service;

    @Test
    void listShouldMaskAccessKeyAndHideSecretTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId("cred-1");
        stored.setName("aliyun-test");
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("LTAI5tUnitTestKey000000001");
        stored.setSecretKey("secret-value");
        when(credentialRepository.findAll()).thenReturn(List.of(stored));

        List<CloudCredentialVO> result = service.listMasked();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccessKey()).isEqualTo("LTAI****0001");
        assertThat(result.get(0).getSecretKey()).isNull();
    }

    @Test
    void listShouldFullyMaskShortAccessKeyTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("short-ak");
        when(credentialRepository.findAll()).thenReturn(List.of(stored));

        assertThat(service.listMasked().get(0).getAccessKey()).isEqualTo("****");
    }

    @Test
    void createShouldRejectDuplicateVendorAndAccessKeyTest() {
        CloudCredentialVO request = new CloudCredentialVO();
        request.setName("dup");
        request.setVendor(InstanceVendor.ALIYUN);
        request.setAccessKey("LTAI5tDupKey000000000001");
        request.setSecretKey("sk");
        when(credentialRepository.findByVendorAndAccessKey(InstanceVendor.ALIYUN, "LTAI5tDupKey000000000001"))
                .thenReturn(Optional.of(new CloudCredentialVO()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        verify(credentialRepository, never()).save(any());
    }

    @Test
    void createShouldRejectApacheVendorTest() {
        CloudCredentialVO request = new CloudCredentialVO();
        request.setName("bad");
        request.setVendor(InstanceVendor.APACHE);
        request.setAccessKey("LTAI5tBadVendor000000001");
        request.setSecretKey("sk");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ALIYUN or TENCENT");
    }

    @Test
    void createShouldAssignIdAndMaskResultTest() {
        CloudCredentialVO request = new CloudCredentialVO();
        request.setName("ok");
        request.setVendor(InstanceVendor.ALIYUN);
        request.setAccessKey("LTAI5tGoodKey00000000001");
        request.setSecretKey("sk-value");
        when(credentialRepository.findByVendorAndAccessKey(any(), any())).thenReturn(Optional.empty());
        when(credentialRepository.save(any(CloudCredentialVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CloudCredentialVO created = service.create(request);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getAccessKey()).isEqualTo("LTAI****0001");
        assertThat(created.getSecretKey()).isNull();
        verify(operationAuditService).record(eq("CREATE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq(created.getId()), eq(null), argThat(detail -> detail.equals("name=ok, vendor=ALIYUN")
                        && !detail.contains("LTAI5tGoodKey00000000001") && !detail.contains("sk-value")),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteShouldRejectWhenReferencedByInstanceTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId("cred-1");
        when(credentialRepository.findById("cred-1")).thenReturn(Optional.of(stored));
        when(instanceRepository.existsByCredentialId("cred-1")).thenReturn(true);

        assertThatThrownBy(() -> service.delete("cred-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("referenced");
        verify(credentialRepository, never()).deleteById(any());
    }

    @Test
    void updateShouldInvalidateAliyunClientsAfterSavingCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId("cred-1");
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("LTAI5tUpdateKey000000001");
        stored.setSecretKey("old-secret");
        when(credentialRepository.findById("cred-1")).thenReturn(Optional.of(stored));
        when(credentialRepository.save(any(CloudCredentialVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId("cred-1");
        request.setSecretKey("new-secret");

        service.update(request);

        verify(aliyunClientFactory).invalidateCredential("cred-1");
        verify(operationAuditService).record(eq("UPDATE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq("cred-1"), eq(null), eq("name=null, vendor=ALIYUN"), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteShouldInvalidateAliyunClientsAfterRemovingCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId("cred-1");
        stored.setVendor(InstanceVendor.ALIYUN);
        when(credentialRepository.findById("cred-1")).thenReturn(Optional.of(stored));
        when(instanceRepository.existsByCredentialId("cred-1")).thenReturn(false);

        service.delete("cred-1");

        verify(credentialRepository).deleteById("cred-1");
        verify(aliyunClientFactory).invalidateCredential("cred-1");
        verify(operationAuditService).record(eq("DELETE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq("cred-1"), eq(null), eq("name=null, vendor=ALIYUN"), eq("SUCCESS"), eq(null));
    }

    @Test
    void revealShouldReturnUnmaskedCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId("cred-1");
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("LTAI5tRevealKey000000001");
        stored.setSecretKey("plain-secret");
        when(credentialRepository.findById("cred-1")).thenReturn(Optional.of(stored));

        CloudCredentialVO revealed = service.reveal("cred-1");

        assertThat(revealed.getAccessKey()).isEqualTo("LTAI5tRevealKey000000001");
        assertThat(revealed.getSecretKey()).isEqualTo("plain-secret");
    }

    @Test
    void repositoryShouldBase64EncodeSecretTest() {
        String encoded = CredentialUtils.encodeBase64("plain-secret");
        assertThat(encoded).isNotEqualTo("plain-secret");
        assertThat(CredentialUtils.decodeBase64(encoded)).isEqualTo("plain-secret");
    }

    @Test
    void repositoryShouldTolerateLegacyPlainSecretTest() {
        assertThat(CredentialUtils.decodeBase64("not base64 !!!")).isEqualTo("not base64 !!!");
    }
}
