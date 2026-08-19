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
package org.apache.rocketmq.studio.cluster.k8s;

import org.apache.rocketmq.studio.persistence.entity.RmqK8sCertificate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class K8sCertificateSecretRedactionTest {

    private static final String PRIVATE_KEY = "sensitive-private-key-material";

    @Test
    void createRequestToStringShouldExcludePrivateKeyTest() {
        CreateCertDTO request = CreateCertDTO.builder()
                .k8sId("production-k8s")
                .keyPem(PRIVATE_KEY)
                .build();

        assertThat(request.toString())
                .contains("production-k8s")
                .doesNotContain(PRIVATE_KEY);
    }

    @Test
    void responseValueToStringShouldExcludePrivateKeyTest() {
        K8sCertVO certificate = K8sCertVO.builder()
                .k8sId("production-k8s")
                .keyPem(PRIVATE_KEY)
                .build();

        assertThat(certificate.toString())
                .contains("production-k8s")
                .doesNotContain(PRIVATE_KEY);
    }

    @Test
    void persistenceEntityToStringShouldExcludePrivateKeyTest() {
        RmqK8sCertificate entity = new RmqK8sCertificate();
        entity.setK8sId("production-k8s");
        entity.setKeyPem(PRIVATE_KEY);

        assertThat(entity.toString())
                .contains("production-k8s")
                .doesNotContain(PRIVATE_KEY);
    }
}
