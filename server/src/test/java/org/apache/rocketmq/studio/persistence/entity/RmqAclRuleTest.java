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

class RmqAclRuleTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAclRule entity = new RmqAclRule();

        assertNull(entity.getId());
        assertNull(entity.getPrincipal());
        assertNull(entity.getResource());
        assertNull(entity.getActions());
        assertNull(entity.getDecision());
        assertNull(entity.getScope());
        assertNull(entity.getAclVersion());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAclRule entity = new RmqAclRule();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setPrincipal("AK-1");
        entity.setResource("order-*");
        entity.setResourceType("TOPIC");
        entity.setResourcePattern("order-*");
        entity.setActions("PUB,SUB");
        entity.setDecision("Allow");
        entity.setScope("DefaultCluster");
        entity.setAclVersion("2.0");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(1L, entity.getId());
        assertEquals("AK-1", entity.getPrincipal());
        assertEquals("order-*", entity.getResource());
        assertEquals("PUB,SUB", entity.getActions());
        assertEquals("Allow", entity.getDecision());
        assertEquals("DefaultCluster", entity.getScope());
        assertEquals("2.0", entity.getAclVersion());
        assertEquals(created, entity.getGmtCreate());
    }
}
