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
 * Locks the certificate lifecycle vocabulary surfaced by the K8s cert list views.
 */
class CertStatusTest {

    @Test
    void exposesAllCertificateLifecycleStatuses() {
        assertEquals(3, CertStatus.values().length);
        assertTrue(Arrays.asList(CertStatus.values()).contains(CertStatus.valid));
        assertTrue(Arrays.asList(CertStatus.values()).contains(CertStatus.expiring));
        assertTrue(Arrays.asList(CertStatus.values()).contains(CertStatus.expired));
    }

    @Test
    void enumNamesStayLowerCamelForJsonSerialization() {
        assertEquals("valid", CertStatus.valid.name());
        assertEquals("expiring", CertStatus.expiring.name());
        assertEquals("expired", CertStatus.expired.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (CertStatus status : CertStatus.values()) {
            assertEquals(status, CertStatus.valueOf(status.name()));
        }
    }
}
