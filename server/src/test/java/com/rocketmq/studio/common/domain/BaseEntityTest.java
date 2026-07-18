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
package com.rocketmq.studio.common.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntityTest {

    /** Concrete subclass for testing the abstract BaseEntity */
    static class TestEntity extends BaseEntity {}

    @Test
    void setId_shouldSetAndGetId() {
        TestEntity entity = new TestEntity();
        entity.setId("abc-123");
        assertEquals("abc-123", entity.getId());
    }

    @Test
    void setCreatedAt_shouldSetAndGetCreatedAt() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    void setUpdatedAt_shouldSetAndGetUpdatedAt() {
        TestEntity entity = new TestEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUpdatedAt(now);
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    void defaultValues_shouldBeNull() {
        TestEntity entity = new TestEntity();
        assertNull(entity.getId());
        assertNull(entity.getCreatedAt());
        assertNull(entity.getUpdatedAt());
    }

    @Test
    void equalsAndHashCode_shouldWorkCorrectly() {
        TestEntity entity1 = new TestEntity();
        entity1.setId("same-id");

        TestEntity entity2 = new TestEntity();
        entity2.setId("same-id");

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
    }
}