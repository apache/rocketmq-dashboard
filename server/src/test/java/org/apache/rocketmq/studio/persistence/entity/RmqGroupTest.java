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

class RmqGroupTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqGroup entity = new RmqGroup();

        assertNull(entity.getId());
        assertNull(entity.getInstanceId());
        assertNull(entity.getName());
        assertNull(entity.getConsumeType());
        assertNull(entity.getMessageModel());
        assertNull(entity.getMaxRetry());
        assertNull(entity.getGmtCreate());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqGroup entity = new RmqGroup();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setClusterId("cluster-1");
        entity.setInstanceId("inst-1");
        entity.setName("cg-orders");
        entity.setConsumeType("CLUSTERING");
        entity.setMessageModel("CLUSTERING");
        entity.setMaxRetry(3);
        entity.setStatus("RUNNING");
        entity.setCreatedBy("alice");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(1L, entity.getId());
        assertEquals("cluster-1", entity.getClusterId());
        assertEquals("inst-1", entity.getInstanceId());
        assertEquals("cg-orders", entity.getName());
        assertEquals("CLUSTERING", entity.getConsumeType());
        assertEquals("CLUSTERING", entity.getMessageModel());
        assertEquals(3, entity.getMaxRetry());
        assertEquals("RUNNING", entity.getStatus());
        assertEquals("alice", entity.getCreatedBy());
        assertEquals(created, entity.getGmtCreate());
    }
}
