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

class RmqNameserverTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqNameserver entity = new RmqNameserver();

        assertNull(entity.getId());
        assertNull(entity.getName());
        assertNull(entity.getNamesrvAddr());
        assertNull(entity.getK8sNamespace());
        assertNull(entity.getStatus());
        assertNull(entity.getGmtCreate());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqNameserver entity = new RmqNameserver();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(2L);
        entity.setName("ns-1");
        entity.setNamesrvAddr("10.132.218.11:9876");
        entity.setK8sNamespace("rocketmq");
        entity.setK8sId("nameserver-0");
        entity.setStatus("RUNNING");
        entity.setDescription("primary nameserver");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(2L, entity.getId());
        assertEquals("ns-1", entity.getName());
        assertEquals("10.132.218.11:9876", entity.getNamesrvAddr());
        assertEquals("rocketmq", entity.getK8sNamespace());
        assertEquals("RUNNING", entity.getStatus());
        assertEquals(created, entity.getGmtCreate());
    }
}
