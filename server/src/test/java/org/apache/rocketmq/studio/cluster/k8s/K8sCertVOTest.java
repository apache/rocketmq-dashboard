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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class K8sCertVOTest {

    @Test
    void builderDefaultsDescribeEmptyCertificate() {
        K8sCertVO vo = K8sCertVO.builder().build();

        assertNull(vo.getK8sId());
        assertNull(vo.getCluster());
        assertNull(vo.getType());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getDaysRemaining());
        assertNull(vo.getSan());
        assertNull(vo.getCertPem());
        assertNull(vo.getKeyPem());
    }

    @Test
    void allArgsCarryCertificateState() {
        LocalDateTime notBefore = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime notAfter = LocalDateTime.parse("2027-09-01T00:00:00");

        K8sCertVO vo = K8sCertVO.builder()
            .k8sId("rocketmq-tls")
            .cluster("DefaultCluster")
            .type(CertType.TLS)
            .issuer("vault")
            .notBefore(notBefore)
            .notAfter(notAfter)
            .status(CertStatus.valid)
            .daysRemaining(180)
            .san(List.of("broker-a.example.com"))
            .certPem("cert-pem")
            .keyPem("key-pem")
            .build();

        assertEquals("rocketmq-tls", vo.getK8sId());
        assertEquals("DefaultCluster", vo.getCluster());
        assertEquals(CertType.TLS, vo.getType());
        assertEquals(CertStatus.valid, vo.getStatus());
        assertEquals(180, vo.getDaysRemaining());
        assertEquals(List.of("broker-a.example.com"), vo.getSan());
        assertEquals("cert-pem", vo.getCertPem());
        assertEquals("key-pem", vo.getKeyPem());
        assertEquals(notAfter, vo.getNotAfter());
    }
}
