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

class RmqTraceQueryTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqTraceQuery entity = new RmqTraceQuery();

        assertNull(entity.getId());
        assertNull(entity.getMsgId());
        assertNull(entity.getTopic());
        assertNull(entity.getNodeCount());
        assertNull(entity.getConsumerCount());
        assertNull(entity.getQueriedBy());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqTraceQuery entity = new RmqTraceQuery();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setMsgId("msg-1");
        entity.setTopic("orders");
        entity.setTraceTopic("RMQ_SYS_TRACE_TOPIC");
        entity.setNodeCount(5);
        entity.setConsumerCount(2);
        entity.setClusterId("cluster-1");
        entity.setQueriedBy("alice");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(1L, entity.getId());
        assertEquals("msg-1", entity.getMsgId());
        assertEquals("orders", entity.getTopic());
        assertEquals("RMQ_SYS_TRACE_TOPIC", entity.getTraceTopic());
        assertEquals(5, entity.getNodeCount());
        assertEquals(2, entity.getConsumerCount());
        assertEquals("alice", entity.getQueriedBy());
        assertEquals(created, entity.getGmtCreate());
    }
}
