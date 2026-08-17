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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqCloudCredential;
import org.apache.rocketmq.studio.persistence.mapper.RmqCloudCredentialMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusCloudCredentialRepositoryTest {

    @Mock
    private RmqCloudCredentialMapper credentialMapper;

    @InjectMocks
    private MybatisPlusCloudCredentialRepository repository;

    @Test
    void saveShouldReportALostConcurrentUpdate() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setId(1L);
        credential.setVendor(InstanceVendor.ALIYUN);
        when(credentialMapper.selectById(1L)).thenReturn(entity(1L, "cred-1", "ALIYUN"));
        when(credentialMapper.updateById(any(RmqCloudCredential.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.save(credential))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cloud credential update was not applied: 1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
    }

    @Test
    void findByIdShouldMapValidPersistedVendor() {
        when(credentialMapper.selectById(2L)).thenReturn(entity(2L, "cred-valid", "ALIYUN"));

        Optional<CloudCredentialVO> result = repository.findById(2L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getVendor()).isEqualTo(InstanceVendor.ALIYUN);
    }

    @Test
    void findByIdShouldRejectInvalidPersistedVendor() {
        when(credentialMapper.selectById(3L)).thenReturn(entity(3L, "cred-invalid", "UNKNOWN"));

        assertThatThrownBy(() -> repository.findById(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid persisted cloud credential vendor")
                .hasMessageContaining("3");
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
}
