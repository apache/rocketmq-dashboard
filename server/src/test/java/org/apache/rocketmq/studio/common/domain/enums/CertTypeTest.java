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
package org.apache.rocketmq.studio.common.domain.enums;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the certificate type vocabulary that flows into the K8s cert create/update
 * payloads and the frontend type selector.
 */
class CertTypeTest {

    @Test
    void exposesAllSupportedCertificateTypes() {
        assertEquals(3, CertType.values().length);
        assertTrue(Arrays.asList(CertType.values()).contains(CertType.TLS));
        assertTrue(Arrays.asList(CertType.values()).contains(CertType.mTLS));
        assertTrue(Arrays.asList(CertType.values()).contains(CertType.ServiceAccount));
    }

    @Test
    void enumNamesMatchTheAcceptedTypeStrings() {
        assertEquals("TLS", CertType.TLS.name());
        assertEquals("mTLS", CertType.mTLS.name());
        assertEquals("ServiceAccount", CertType.ServiceAccount.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (CertType type : CertType.values()) {
            assertEquals(type, CertType.valueOf(type.name()));
        }
    }
}
