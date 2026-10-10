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
package org.apache.rocketmq.studio.persistence.entity;

import org.apache.rocketmq.studio.instance.acl.PlainAccessConfigVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The credential-bearing types this package mirrors from the database. A Lombok {@code @Data}
 * class prints every field in its generated {@code toString()}, including the secret columns, so a
 * single log line, exception message or diagnostic dump that renders one of them writes the
 * credential to disk. {@code RmqSettings}, {@code RmqStudioUser} and {@code RmqK8sCertificate}
 * already exclude theirs; these four kept printing them.
 */
class CredentialToStringRedactionTest {

    private static final String ACCESS_KEY = "sensitive-access-key";
    private static final String SECRET_KEY = "sensitive-secret-key";

    @Test
    void cloudCredentialEntityToStringShouldExcludeTheCredentialTest() {
        RmqCloudCredential credential = new RmqCloudCredential();
        credential.setName("aliyun-prod");
        credential.setAccessKey(ACCESS_KEY);
        credential.setSecretKey(SECRET_KEY);

        assertThat(credential.toString())
                .contains("aliyun-prod")
                .doesNotContain(ACCESS_KEY)
                .doesNotContain(SECRET_KEY);
    }

    @Test
    void aclUserEntityToStringShouldExcludeTheCredentialTest() {
        RmqAclUser user = new RmqAclUser();
        user.setUsername("order-service");
        user.setAccessKey(ACCESS_KEY);
        user.setSecretKey(SECRET_KEY);

        assertThat(user.toString())
                .contains("order-service")
                .doesNotContain(ACCESS_KEY)
                .doesNotContain(SECRET_KEY);
    }

    @Test
    void dataSourceEntityToStringShouldExcludeTheSerializedConfigurationTest() {
        RmqDataSource dataSource = new RmqDataSource();
        dataSource.setDsKey("prometheus-prod");
        dataSource.setJson("{\"username\":\"" + ACCESS_KEY + "\",\"password\":\"" + SECRET_KEY + "\"}");

        assertThat(dataSource.toString())
                .contains("prometheus-prod")
                .doesNotContain(ACCESS_KEY)
                .doesNotContain(SECRET_KEY);
    }

    @Test
    void plainAccessConfigToStringShouldExcludeTheCredentialTest() {
        PlainAccessConfigVO plainAccess = PlainAccessConfigVO.builder()
                .accessKey(ACCESS_KEY)
                .secretKey(SECRET_KEY)
                .whiteRemoteAddress("10.0.0.0/8")
                .build();

        assertThat(plainAccess.toString())
                .contains("10.0.0.0/8")
                .doesNotContain(ACCESS_KEY)
                .doesNotContain(SECRET_KEY);
    }
}
