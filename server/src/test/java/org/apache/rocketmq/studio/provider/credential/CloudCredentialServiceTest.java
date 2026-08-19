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
import org.apache.rocketmq.studio.provider.tencent.TencentClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

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
    private TencentClientFactory tencentClientFactory;

    @Mock
    private OperationAuditService operationAuditService;

    @InjectMocks
    private CloudCredentialService service;

    @Test
    void listShouldMaskAccessKeyAndHideSecretTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
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
    void createShouldTranslateConcurrentDuplicateKeyToConflict() {
        CloudCredentialVO request = new CloudCredentialVO();
        request.setName("race");
        request.setVendor(InstanceVendor.ALIYUN);
        request.setAccessKey("LTAI5tRaceKey00000000001");
        request.setSecretKey("sk");
        when(credentialRepository.findByVendorAndAccessKey(InstanceVendor.ALIYUN, request.getAccessKey()))
                .thenReturn(Optional.empty());
        when(credentialRepository.save(any())).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
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
                .thenAnswer(invocation -> {
                    CloudCredentialVO saved = invocation.getArgument(0);
                    if (saved.getId() == null) {
                        saved.setId(1L);
                    }
                    return saved;
                });

        CloudCredentialVO created = service.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getAccessKey()).isEqualTo("LTAI****0001");
        assertThat(created.getSecretKey()).isNull();
        verify(operationAuditService).record(eq("CREATE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq("1"), eq(null), argThat(detail -> detail.equals("name=ok, vendor=ALIYUN")
                        && !detail.contains("LTAI5tGoodKey00000000001") && !detail.contains("sk-value")),
                eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteShouldRejectWhenReferencedByInstanceTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(instanceRepository.existsByCredentialId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("referenced");
        verify(credentialRepository, never()).deleteById(any());
    }

    @Test
    void updateShouldInvalidateAliyunClientsAfterSavingCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("LTAI5tUpdateKey000000001");
        stored.setSecretKey("old-secret");
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(credentialRepository.replace(any(CloudCredentialVO.class))).thenReturn(true);

        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(1L);
        request.setSecretKey("new-secret");

        service.update(request);

        verify(aliyunClientFactory).invalidateCredential(1L);
        verify(operationAuditService).record(eq("UPDATE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq("1"), eq(null), eq("name=null, vendor=ALIYUN"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateShouldNotRecreateConcurrentlyDeletedCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        stored.setVendor(InstanceVendor.TENCENT);
        stored.setAccessKey("AKIDexample");
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(credentialRepository.replace(stored)).thenReturn(false);
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(1L);
        request.setName("renamed");

        assertThatThrownBy(() -> service.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud credential not found: 1");

        verify(tencentClientFactory, never()).invalidateCredential(any());
    }

    @Test
    void deleteShouldInvalidateAliyunClientsAfterRemovingCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        stored.setVendor(InstanceVendor.ALIYUN);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(instanceRepository.existsByCredentialId(1L)).thenReturn(false);
        when(credentialRepository.deleteById(1L)).thenReturn(true);

        service.delete(1L);

        verify(credentialRepository).deleteById(1L);
        verify(aliyunClientFactory).invalidateCredential(1L);
        verify(operationAuditService).record(eq("DELETE_CLOUD_CREDENTIAL"), eq("CLOUD_CREDENTIAL"),
                eq("1"), eq(null), eq("name=null, vendor=ALIYUN"), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteShouldRejectConcurrentRemovalBeforeInvalidatingClientsTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        stored.setVendor(InstanceVendor.ALIYUN);
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(instanceRepository.existsByCredentialId(1L)).thenReturn(false);
        when(credentialRepository.deleteById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud credential not found: 1");

        verify(aliyunClientFactory, never()).invalidateCredential(any());
    }

    @Test
    void revealShouldReturnUnmaskedCredentialTest() {
        CloudCredentialVO stored = new CloudCredentialVO();
        stored.setId(1L);
        stored.setVendor(InstanceVendor.ALIYUN);
        stored.setAccessKey("LTAI5tRevealKey000000001");
        stored.setSecretKey("plain-secret");
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(stored));

        CloudCredentialVO revealed = service.reveal(1L);

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
