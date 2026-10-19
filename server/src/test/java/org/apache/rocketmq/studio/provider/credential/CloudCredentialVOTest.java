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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CloudCredentialVO}, one cloud-vendor credential as the credentials page shows
 * it: name, vendor, access key and remark. The access key stays visible on purpose (it is
 * the identifier support asks for), while the secret key never reaches {@code toString()} —
 * the VO travels through service and request logs, and unlike the credential DTOs it is not
 * covered by {@code SensitiveRequestToStringTest}, so the redaction has no net today.
 */
class CloudCredentialVOTest {

    @Test
    void aCredentialCarriesItsIdentityAndVendor() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setName("aliyun-prod");
        credential.setVendor(InstanceVendor.ALIYUN);
        credential.setAccessKey("LTAI5tExampleAccessKey");
        credential.setSecretKey("prod-secret-value");
        credential.setRemark("order traffic");

        assertThat(credential.getName()).isEqualTo("aliyun-prod");
        assertThat(credential.getVendor()).isEqualTo(InstanceVendor.ALIYUN);
        assertThat(credential.getAccessKey()).isEqualTo("LTAI5tExampleAccessKey");
        assertThat(credential.getSecretKey()).isEqualTo("prod-secret-value");
        assertThat(credential.getRemark()).isEqualTo("order traffic");
    }

    @Test
    void toStringKeepsTheAccessKeyButNeverTheSecretKey() {
        CloudCredentialVO credential = new CloudCredentialVO();
        credential.setName("aliyun-prod");
        credential.setAccessKey("LTAI5tExampleAccessKey");
        credential.setSecretKey("prod-secret-value");

        String rendered = credential.toString();

        assertThat(rendered).contains("aliyun-prod");
        assertThat(rendered).contains("LTAI5tExampleAccessKey");
        assertThat(rendered).doesNotContain("prod-secret-value");
    }
}
