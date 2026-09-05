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
package org.apache.rocketmq.studio.ops.audit;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditRecordVOTest {

    @Test
    void freshVoCarriesNullFields() {
        AuditRecordVO vo = new AuditRecordVO();

        assertNull(vo.getId());
        assertNull(vo.getTimestamp());
        assertNull(vo.getOperator());
        assertNull(vo.getOperationType());
        assertNull(vo.getResourceType());
        assertNull(vo.getResult());
        assertNull(vo.getErrorMessage());
    }

    @Test
    void settersRoundTripRepresentativeFields() {
        AuditRecordVO vo = new AuditRecordVO();
        LocalDateTime time = LocalDateTime.parse("2026-09-01T08:00:00");

        vo.setId(9L);
        vo.setTimestamp(time);
        vo.setOperator("alice");
        vo.setOperationType("CREATE_TOPIC");
        vo.setResourceType("TOPIC");
        vo.setTarget("orders");
        vo.setClusterId("cluster-1");
        vo.setDetail("{\"queueNums\":8}");
        vo.setResult("SUCCESS");
        vo.setErrorMessage(null);

        assertEquals(9L, vo.getId());
        assertEquals(time, vo.getTimestamp());
        assertEquals("alice", vo.getOperator());
        assertEquals("CREATE_TOPIC", vo.getOperationType());
        assertEquals("TOPIC", vo.getResourceType());
        assertEquals("orders", vo.getTarget());
        assertEquals("SUCCESS", vo.getResult());
        assertNull(vo.getErrorMessage());
    }
}
