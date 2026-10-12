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

import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.persistence.entity.RmqCloudCredential;
import org.apache.rocketmq.studio.persistence.mapper.RmqCloudCredentialMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"studio.auth.login-required=false"})
class CloudCredentialPartialUpdateIntegrationTest {

    @Autowired
    private CloudCredentialRepository repository;

    @Autowired
    private RmqCloudCredentialMapper mapper;

    @Test
    void nameOnlyUpdateAfterRotationPreservesSecretTest() {
        RmqCloudCredential row = new RmqCloudCredential();
        row.setName("production");
        row.setVendor("ALIYUN");
        row.setAccessKey("integration-" + UUID.randomUUID());
        row.setSecretKey(CredentialUtils.encodeBase64("old-secret"));
        row.setGmtCreate(LocalDateTime.now());
        row.setGmtModified(LocalDateTime.now());
        mapper.insert(row);
        try {
            assertThat(repository.updateFields(row.getId(), null, "new-secret", null)).isTrue();
            assertThat(repository.updateFields(row.getId(), "renamed", null, "")).isTrue();

            RmqCloudCredential persisted = mapper.selectById(row.getId());
            assertThat(persisted.getName()).isEqualTo("renamed");
            assertThat(persisted.getSecretKey()).isEqualTo(CredentialUtils.encodeBase64("new-secret"));
            assertThat(persisted.getRemark()).isEmpty();
            assertThat(repository.findById(row.getId()).orElseThrow().getSecretKey())
                    .isEqualTo("new-secret");
        } finally {
            mapper.deleteById(row.getId());
        }
    }
}
