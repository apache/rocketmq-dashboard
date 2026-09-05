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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RmqK8sCertificateTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqK8sCertificate entity = new RmqK8sCertificate();

        assertNull(entity.getId());
        assertNull(entity.getK8sId());
        assertNull(entity.getCluster());
        assertNull(entity.getCertType());
        assertNull(entity.getNotBefore());
        assertNull(entity.getNotAfter());
        assertNull(entity.getDaysRemaining());
    }

    @Test
    void settersRoundTripRepresentativeFields() {
        RmqK8sCertificate entity = new RmqK8sCertificate();
        LocalDateTime notBefore = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime notAfter = LocalDateTime.parse("2027-09-01T00:00:00");

        entity.setId(5L);
        entity.setK8sId("rocketmq-tls");
        entity.setCluster("DefaultCluster");
        entity.setCertType("TLS");
        entity.setIssuer("vault");
        entity.setNotBefore(notBefore);
        entity.setNotAfter(notAfter);
        entity.setStatus("valid");
        entity.setDaysRemaining(180);
        entity.setSan("broker-a.example.com");
        entity.setCertPem("cert-pem");
        entity.setKeyPem("key-pem");
        entity.setGmtCreate(notBefore);
        entity.setGmtModified(notBefore);

        assertEquals(5L, entity.getId());
        assertEquals("rocketmq-tls", entity.getK8sId());
        assertEquals("TLS", entity.getCertType());
        assertEquals("valid", entity.getStatus());
        assertEquals(180, entity.getDaysRemaining());
        assertEquals(notAfter, entity.getNotAfter());
    }
}
