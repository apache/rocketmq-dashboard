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

class RmqOperationAuditTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqOperationAudit entity = new RmqOperationAudit();

        assertNull(entity.getId());
        assertNull(entity.getOperation());
        assertNull(entity.getResourceType());
        assertNull(entity.getResourceName());
        assertNull(entity.getResult());
        assertNull(entity.getOperator());
        assertNull(entity.getGmtCreate());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqOperationAudit entity = new RmqOperationAudit();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(9L);
        entity.setOperation("CREATE_TOPIC");
        entity.setResourceType("TOPIC");
        entity.setResourceName("orders");
        entity.setClusterId("cluster-1");
        entity.setDetail("{\"queueNums\":8}");
        entity.setResult("SUCCESS");
        entity.setErrorMessage(null);
        entity.setOperator("alice");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(9L, entity.getId());
        assertEquals("CREATE_TOPIC", entity.getOperation());
        assertEquals("TOPIC", entity.getResourceType());
        assertEquals("orders", entity.getResourceName());
        assertEquals("SUCCESS", entity.getResult());
        assertEquals("alice", entity.getOperator());
        assertEquals(created, entity.getGmtCreate());
    }
}
