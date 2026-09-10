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
package org.apache.rocketmq.studio.common.domain;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    @Test
    void freshEntityCarriesNullFields() {
        TestEntity entity = new TestEntity();

        assertNull(entity.getId());
        assertNull(entity.getGmtCreate());
        assertNull(entity.getGmtModified());
    }

    @Test
    void settersRoundTripEveryField() {
        TestEntity entity = new TestEntity();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime modified = LocalDateTime.parse("2026-09-02T09:00:00");

        entity.setId(7L);
        entity.setGmtCreate(created);
        entity.setGmtModified(modified);

        assertEquals(7L, entity.getId());
        assertEquals(created, entity.getGmtCreate());
        assertEquals(modified, entity.getGmtModified());
    }
}
