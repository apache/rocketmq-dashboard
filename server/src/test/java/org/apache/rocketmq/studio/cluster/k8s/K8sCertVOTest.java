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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.enums.CertStatus;
import org.apache.rocketmq.studio.common.domain.enums.CertType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the certificate view of {@link K8sCertVO}: the identity, validity window and the
 * remaining-days countdown round-trip - and the private key is write-only on the wire and
 * never exposed by toString, so a persisted key can never leak through an API response or a
 * log line.
 */
class K8sCertVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        K8sCertVO cert = K8sCertVO.builder()
                .k8sId("tls-cert")
                .cluster("cluster-1")
                .type(CertType.TLS)
                .issuer("CN=rocketmq")
                .notBefore(LocalDateTime.of(2026, 1, 1, 0, 0))
                .notAfter(LocalDateTime.of(2027, 1, 1, 0, 0))
                .status(CertStatus.valid)
                .daysRemaining(365)
                .san(List.of("rocketmq.example.com"))
                .certPem("-----BEGIN CERTIFICATE-----")
                .keyPem("-----BEGIN PRIVATE KEY-----")
                .build();

        assertThat(cert.getK8sId()).isEqualTo("tls-cert");
        assertThat(cert.getCluster()).isEqualTo("cluster-1");
        assertThat(cert.getType()).isEqualTo(CertType.TLS);
        assertThat(cert.getIssuer()).isEqualTo("CN=rocketmq");
        assertThat(cert.getStatus()).isEqualTo(CertStatus.valid);
        assertThat(cert.getDaysRemaining()).isEqualTo(365);
        assertThat(cert.getSan()).containsExactly("rocketmq.example.com");
        assertThat(cert.getCertPem()).isEqualTo("-----BEGIN CERTIFICATE-----");
        assertThat(cert.getKeyPem()).isEqualTo("-----BEGIN PRIVATE KEY-----");
    }

    @Test
    void thePrivateKeyNeverLeaksOnTheWireOrInToString() {
        K8sCertVO cert = K8sCertVO.builder()
                .k8sId("tls-cert")
                .keyPem("plain-private-key-123")
                .build();

        try {
            String json = new ObjectMapper().writeValueAsString(cert);
            assertThat(json).doesNotContain("plain-private-key-123");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThat(cert.toString()).doesNotContain("plain-private-key-123");
    }

    @Test
    void aFreshCertificateCountsNoRemainingDays() {
        assertThat(new K8sCertVO().getDaysRemaining()).isZero();
    }
}
