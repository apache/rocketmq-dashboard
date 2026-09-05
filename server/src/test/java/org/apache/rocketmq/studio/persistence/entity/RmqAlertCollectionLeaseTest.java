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

class RmqAlertCollectionLeaseTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAlertCollectionLease entity = new RmqAlertCollectionLease();

        assertNull(entity.getId());
        assertNull(entity.getLeaseName());
        assertNull(entity.getHolderId());
        assertNull(entity.getExpiresAt());
        assertNull(entity.getGmtModified());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAlertCollectionLease entity = new RmqAlertCollectionLease();
        LocalDateTime expires = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setLeaseName("alert-collector");
        entity.setHolderId("replica-1");
        entity.setExpiresAt(expires);
        entity.setGmtModified(expires);

        assertEquals(1L, entity.getId());
        assertEquals("alert-collector", entity.getLeaseName());
        assertEquals("replica-1", entity.getHolderId());
        assertEquals(expires, entity.getExpiresAt());
        assertEquals(expires, entity.getGmtModified());
    }
}
