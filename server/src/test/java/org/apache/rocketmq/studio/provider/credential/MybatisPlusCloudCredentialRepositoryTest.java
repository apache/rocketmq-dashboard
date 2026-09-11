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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialCipher;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqCloudCredential;
import org.apache.rocketmq.studio.persistence.mapper.RmqCloudCredentialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusCloudCredentialRepositoryTest {

    /** Deterministic 32-byte AES key so the test can assert on the stored ciphertext. */
    private static final String TEST_KEY = testKey();

    @Mock
    private RmqCloudCredentialMapper credentialMapper;

    private CredentialCipher credentialCipher;

    private MybatisPlusCloudCredentialRepository repository;

    @BeforeEach
    void setUp() {
        credentialCipher = new CredentialCipher(TEST_KEY);
        repository = new MybatisPlusCloudCredentialRepository(credentialMapper, credentialCipher);
    }

    @Test
    void saveShouldReportALostConcurrentUpdate() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(credentialMapper.updateById(any(RmqCloudCredential.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.save(credential))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud credential update was not applied: 1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
    }

    @Test
    void saveShouldNotReinsertACredentialDeletedConcurrently() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(credentialMapper.updateById(any(RmqCloudCredential.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.save(credential))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud credential update was not applied: 1");
        verify(credentialMapper, never()).insert(any(RmqCloudCredential.class));
    }

    @Test
    void saveShouldSealTheSecretKeyBeforePersisting() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setName("aliyun-prod");
        credential.setVendor(InstanceVendor.ALIYUN);
        credential.setAccessKey("access-key");
        credential.setSecretKey("super-secret-value");

        repository.save(credential);

        RmqCloudCredential persisted = capturedInsert();
        String stored = persisted.getSecretKey();
        assertThat(stored).isNotEqualTo("super-secret-value");
        assertThat(CredentialCipher.isEncrypted(stored)).isTrue();
        assertThat(credentialCipher.decrypt(stored)).isEqualTo("super-secret-value");
    }

    @Test
    void replaceShouldResealTheSecretKeyWithAFreshIv() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setName("aliyun-prod");
        credential.setVendor(InstanceVendor.ALIYUN);
        credential.setAccessKey("access-key");
        credential.setSecretKey("rotated-secret");
        when(credentialMapper.updateById(any(RmqCloudCredential.class))).thenReturn(1);

        assertThat(repository.replace(credential)).isTrue();

        ArgumentCaptor<RmqCloudCredential> captor = ArgumentCaptor.forClass(RmqCloudCredential.class);
        verify(credentialMapper).updateById(captor.capture());
        String stored = captor.getValue().getSecretKey();
        assertThat(CredentialCipher.isEncrypted(stored)).isTrue();
        assertThat(credentialCipher.decrypt(stored)).isEqualTo("rotated-secret");
    }

    @Test
    void findByIdShouldOpenASealedSecret() {
        RmqCloudCredential entity = entity(4L, "cred-sealed", "ALIYUN");
        entity.setSecretKey(credentialCipher.encrypt("sealed-secret"));
        when(credentialMapper.selectById(4L)).thenReturn(entity);

        Optional<CloudCredentialVO> result = repository.findById(4L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getSecretKey()).isEqualTo("sealed-secret");
    }

    @Test
    void findByIdShouldMapValidPersistedVendor() {
        when(credentialMapper.selectById(2L)).thenReturn(entity(2L, "cred-valid", "ALIYUN"));

        Optional<CloudCredentialVO> result = repository.findById(2L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        // A row written before AES-GCM is still readable through the legacy base64 path.
        assertThat(result.orElseThrow().getSecretKey())
                .isEqualTo(CredentialUtils.decodeBase64("c2VjcmV0"));
    }

    @Test
    void findByIdShouldRejectInvalidPersistedVendor() {
        when(credentialMapper.selectById(3L)).thenReturn(entity(3L, "cred-invalid", "UNKNOWN"));

        assertThatThrownBy(() -> repository.findById(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid persisted cloud credential vendor")
                .hasMessageContaining("3");
    }

    @Test
    void findPageShouldTrimTheSearchTerm() {
        when(credentialMapper.selectPage(any(IPage.class), any(Wrapper.class)))
                .thenReturn(new Page<RmqCloudCredential>(1, 20));

        repository.findPage(null, "  credential  ", 1, 20);

        ArgumentCaptor<Wrapper<RmqCloudCredential>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(credentialMapper).selectPage(any(IPage.class), queryCaptor.capture());
        QueryWrapper<RmqCloudCredential> query = (QueryWrapper<RmqCloudCredential>) queryCaptor.getValue();
        query.getCustomSqlSegment();
        assertThat(query.getParamNameValuePairs()).containsValue("%credential%");
    }

    private RmqCloudCredential capturedInsert() {
        ArgumentCaptor<RmqCloudCredential> captor = ArgumentCaptor.forClass(RmqCloudCredential.class);
        verify(credentialMapper).insert(captor.capture());
        return captor.getValue();
    }

    private RmqCloudCredential entity(Long id, String name, String vendor) {
        RmqCloudCredential entity = new RmqCloudCredential();
        entity.setId(id);
        entity.setName(name);
        entity.setVendor(vendor);
        entity.setAccessKey("access-key");
        entity.setSecretKey("c2VjcmV0");
        return entity;
    }

    private static String testKey() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 1);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
