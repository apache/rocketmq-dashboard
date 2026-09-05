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

class RmqAclUserTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAclUser entity = new RmqAclUser();

        assertNull(entity.getId());
        assertNull(entity.getUsername());
        assertNull(entity.getAccessKey());
        assertNull(entity.getSecretKey());
        assertNull(entity.getAdmin());
        assertNull(entity.getGmtCreate());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAclUser entity = new RmqAclUser();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(4L);
        entity.setUsername("operator");
        entity.setAccessKey("AK-1");
        entity.setSecretKey("sk-1");
        entity.setAdmin(true);
        entity.setClusters("cluster-a,cluster-b");
        entity.setWhiteRemoteAddress("10.0.0.0/8");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(4L, entity.getId());
        assertEquals("operator", entity.getUsername());
        assertEquals("AK-1", entity.getAccessKey());
        assertEquals("sk-1", entity.getSecretKey());
        assertEquals(Boolean.TRUE, entity.getAdmin());
        assertEquals("cluster-a,cluster-b", entity.getClusters());
        assertEquals("10.0.0.0/8", entity.getWhiteRemoteAddress());
        assertEquals(created, entity.getGmtCreate());
    }
}
