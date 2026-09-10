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

class RmqStudioSessionTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqStudioSession entity = new RmqStudioSession();

        assertNull(entity.getId());
        assertNull(entity.getUserId());
        assertNull(entity.getTokenHash());
        assertNull(entity.getExpiresAt());
        assertNull(entity.getRevokedAt());
        assertNull(entity.getLastSeenAt());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqStudioSession entity = new RmqStudioSession();
        LocalDateTime expires = LocalDateTime.parse("2026-09-08T08:00:00");

        entity.setId(1L);
        entity.setUserId(7L);
        entity.setTokenHash("hash-1");
        entity.setExpiresAt(expires);
        entity.setRevokedAt(null);
        entity.setLastSeenAt(expires);
        entity.setGmtCreate(expires);
        entity.setGmtModified(expires);

        assertEquals(1L, entity.getId());
        assertEquals(7L, entity.getUserId());
        assertEquals("hash-1", entity.getTokenHash());
        assertEquals(expires, entity.getExpiresAt());
        assertEquals(expires, entity.getLastSeenAt());
    }
}
