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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RmqStudioUserTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqStudioUser entity = new RmqStudioUser();

        assertNull(entity.getId());
        assertNull(entity.getUsername());
        assertNull(entity.getPasswordHash());
        assertNull(entity.getAdmin());
        assertNull(entity.getEnabled());
        assertNull(entity.getPasswordChangedAt());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqStudioUser entity = new RmqStudioUser();
        LocalDateTime changed = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(7L);
        entity.setUsername("operator");
        entity.setPasswordHash("hash-1");
        entity.setAdmin(false);
        entity.setEnabled(true);
        entity.setPasswordChangedAt(changed);
        entity.setGmtCreate(changed);
        entity.setGmtModified(changed);

        assertEquals(7L, entity.getId());
        assertEquals("operator", entity.getUsername());
        assertEquals("hash-1", entity.getPasswordHash());
        assertEquals(Boolean.FALSE, entity.getAdmin());
        assertEquals(Boolean.TRUE, entity.getEnabled());
        assertEquals(changed, entity.getPasswordChangedAt());
    }

    @Test
    void passwordHashIsExcludedFromToString() {
        RmqStudioUser entity = new RmqStudioUser();
        entity.setUsername("operator");
        entity.setPasswordHash("must-not-leak");

        assertFalse(entity.toString().contains("must-not-leak"));
    }
}
