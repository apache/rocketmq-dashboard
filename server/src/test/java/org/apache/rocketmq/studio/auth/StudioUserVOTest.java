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
package org.apache.rocketmq.studio.auth;

import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioUserVOTest {

    @Test
    void builderDefaultsDescribeFreshUser() {
        StudioUserVO vo = StudioUserVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getUsername());
        assertFalse(vo.isAdmin());
        assertFalse(vo.isEnabled());
        assertNull(vo.getGmtCreate());
    }

    @Test
    void allArgsCarryUserState() {
        LocalDateTime changed = LocalDateTime.parse("2026-09-01T08:00:00");

        StudioUserVO vo = StudioUserVO.builder()
            .id(7L)
            .username("operator")
            .admin(true)
            .enabled(true)
            .passwordChangedAt(changed)
            .gmtCreate(changed)
            .gmtModified(changed)
            .build();

        assertEquals(7L, vo.getId());
        assertEquals("operator", vo.getUsername());
        assertTrue(vo.isAdmin());
        assertTrue(vo.isEnabled());
        assertEquals(changed, vo.getPasswordChangedAt());
    }

    @Test
    void fromMapsEntityAndNormalizesNullBooleans() {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(9L);
        user.setUsername("reader");
        user.setAdmin(null);
        user.setEnabled(null);

        StudioUserVO vo = StudioUserVO.from(user);

        assertEquals(9L, vo.getId());
        assertEquals("reader", vo.getUsername());
        assertFalse(vo.isAdmin());
        assertFalse(vo.isEnabled());
    }
}
