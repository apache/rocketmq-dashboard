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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialEncryptionMigrationTest {

    private static final String TEST_KEY = testKey();

    @Mock
    private RmqCloudCredentialMapper credentialMapper;

    private CredentialCipher credentialCipher;

    private CredentialEncryptionMigration migration;

    @BeforeEach
    void setUp() {
        credentialCipher = new CredentialCipher(TEST_KEY);
        migration = new CredentialEncryptionMigration(credentialMapper, credentialCipher);
    }

    @Test
    void runShouldResealLegacyRowsAndSkipSealedOrEmptyOnes() {
        RmqCloudCredential legacy = row(1L, CredentialUtils.encodeBase64("legacy-secret"));
        RmqCloudCredential sealed = row(2L, credentialCipher.encrypt("already-sealed"));
        RmqCloudCredential empty = row(3L, null);
        when(credentialMapper.selectList(null)).thenReturn(List.of(legacy, sealed, empty));
        when(credentialMapper.updateById(any(RmqCloudCredential.class))).thenReturn(1);

        migration.run(null);

        ArgumentCaptor<RmqCloudCredential> captor = ArgumentCaptor.forClass(RmqCloudCredential.class);
        verify(credentialMapper).updateById(captor.capture());
        RmqCloudCredential updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(CredentialCipher.isEncrypted(updated.getSecretKey())).isTrue();
        assertThat(credentialCipher.decrypt(updated.getSecretKey())).isEqualTo("legacy-secret");
    }

    @Test
    void runShouldNotRewriteAnythingWhenEveryRowIsAlreadySealed() {
        when(credentialMapper.selectList(null))
                .thenReturn(List.of(row(1L, credentialCipher.encrypt("sealed"))));

        migration.run(null);

        verify(credentialMapper, never()).updateById(any(RmqCloudCredential.class));
    }

    @Test
    void runShouldNotPropagateAFailureSoStartupStillSucceeds() {
        when(credentialMapper.selectList(null)).thenThrow(new IllegalStateException("table missing"));

        assertThatCode(() -> migration.run(null)).doesNotThrowAnyException();

        verify(credentialMapper, never()).updateById(any(RmqCloudCredential.class));
    }

    private RmqCloudCredential row(Long id, String secretKey) {
        RmqCloudCredential entity = new RmqCloudCredential();
        entity.setId(id);
        entity.setName("cred-" + id);
        entity.setVendor("ALIYUN");
        entity.setAccessKey("access-key");
        entity.setSecretKey(secretKey);
        return entity;
    }

    private static String testKey() {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) (index + 11);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
