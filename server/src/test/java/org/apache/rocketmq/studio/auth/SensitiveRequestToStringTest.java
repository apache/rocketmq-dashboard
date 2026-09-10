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
package org.apache.rocketmq.studio.auth;

import org.apache.rocketmq.studio.instance.acl.UpsertPlainAccessConfigDTO;
import org.apache.rocketmq.studio.model.request.MetricsDataSourceQueryRequest;
import org.apache.rocketmq.studio.ops.ai.LlmConfigDTO;
import org.apache.rocketmq.studio.provider.credential.CreateCloudCredentialDTO;
import org.apache.rocketmq.studio.provider.credential.UpdateCloudCredentialDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveRequestToStringTest {

    @Test
    void requestToStringShouldNotExposeSecrets() {
        LlmConfigDTO llm = new LlmConfigDTO();
        llm.setProvider("openai");
        llm.setApiKey("llm-secret-value");

        CreateCloudCredentialDTO createCredential = new CreateCloudCredentialDTO();
        createCredential.setName("production");
        createCredential.setAccessKey("cloud-access-key");
        createCredential.setSecretKey("cloud-secret-key");

        UpdateCloudCredentialDTO updateCredential = new UpdateCloudCredentialDTO();
        updateCredential.setId(1L);
        updateCredential.setSecretKey("rotated-cloud-secret");

        UpsertPlainAccessConfigDTO plainAccess = new UpsertPlainAccessConfigDTO();
        plainAccess.setAccessKey("rocketmq-access-key");
        plainAccess.setSecretKey("rocketmq-secret-key");

        MetricsDataSourceQueryRequest metrics = new MetricsDataSourceQueryRequest();
        metrics.setInstanceId("instance-1");
        metrics.setPassword("metrics-password");
        metrics.setBearerToken("metrics-bearer-token");

        assertThat(llm.toString())
                .contains("provider=openai")
                .doesNotContain("llm-secret-value");
        assertThat(createCredential.toString())
                .contains("name=production")
                .doesNotContain("cloud-access-key", "cloud-secret-key");
        assertThat(updateCredential.toString())
                .contains("id=1")
                .doesNotContain("rotated-cloud-secret");
        assertThat(plainAccess.toString())
                .doesNotContain("rocketmq-access-key", "rocketmq-secret-key");
        assertThat(metrics.toString())
                .contains("instanceId=instance-1")
                .doesNotContain("metrics-password", "metrics-bearer-token");
    }

    @Test
    void studioUserDtoRedactsPlainAndResetPasswords() {
        CreateStudioUserDTO create = new CreateStudioUserDTO();
        create.setUsername("alice");
        create.setPassword("plain-create-password");

        ResetPasswordDTO reset = new ResetPasswordDTO();
        reset.setNewPassword("plain-reset-password");

        assertThat(create.toString())
                .contains("username=alice")
                .doesNotContain("plain-create-password")
                .doesNotContain("password");
        assertThat(reset.toString())
                .doesNotContain("plain-reset-password")
                .doesNotContain("password");
    }
}
