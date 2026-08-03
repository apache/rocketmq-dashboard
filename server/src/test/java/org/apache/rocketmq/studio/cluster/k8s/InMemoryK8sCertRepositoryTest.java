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

import org.apache.rocketmq.studio.common.domain.enums.CertStatus;
import org.apache.rocketmq.studio.common.domain.enums.CertType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryK8sCertRepositoryTest {

    @Test
    void repositoryShouldStartEmpty() {
        InMemoryK8sCertRepository repository = new InMemoryK8sCertRepository();

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void saveFindAndDeleteShouldManageExplicitRecordsOnly() {
        InMemoryK8sCertRepository repository = new InMemoryK8sCertRepository();
        K8sCertVO cert = K8sCertVO.builder()
                .name("rocketmq-tls")
                .namespace("rocketmq")
                .cluster("prod")
                .type(CertType.TLS)
                .issuer("issuer")
                .notBefore(LocalDateTime.now().minusDays(1))
                .notAfter(LocalDateTime.now().plusDays(30))
                .status(CertStatus.valid)
                .daysRemaining(30)
                .san(List.of("broker.example.com"))
                .build();
        cert.setId("cert-explicit");

        repository.save(cert);

        assertThat(repository.findAll()).containsExactly(cert);
        assertThat(repository.findById("cert-explicit")).contains(cert);

        repository.deleteById("cert-explicit");

        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.findById("cert-explicit")).isEmpty();
    }
}
