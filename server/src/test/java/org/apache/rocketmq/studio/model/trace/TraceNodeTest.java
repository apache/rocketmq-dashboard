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
package org.apache.rocketmq.studio.model.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceNodeTest {

    @Test
    void freshNodeCarriesPrimitiveDefaults() {
        TraceNode node = new TraceNode();

        assertNull(node.getRequestId());
        assertNull(node.getStoreHost());
        assertNull(node.getClientHost());
        assertEquals(0, node.getCostTime());
        assertEquals(0L, node.getBeginTimestamp());
        assertEquals(0L, node.getEndTimestamp());
        assertEquals(0, node.getRetryTimes());
        assertNull(node.getStatus());
        assertNull(node.getTransactionState());
        assertNull(node.getTransactionId());
        assertFalse(node.isFromTransactionCheck());
        assertNull(node.getMsgType());
    }

    @Test
    void settersRoundTripEveryField() {
        TraceNode node = new TraceNode();
        node.setRequestId("req-1");
        node.setStoreHost("broker-a");
        node.setClientHost("10.0.0.1");
        node.setCostTime(23);
        node.setBeginTimestamp(1000L);
        node.setEndTimestamp(1023L);
        node.setRetryTimes(2);
        node.setStatus("CONSUMED");
        node.setTransactionState("COMMIT_MESSAGE");
        node.setTransactionId("tx-1");
        node.setFromTransactionCheck(true);
        node.setMsgType("Normal");

        assertEquals("req-1", node.getRequestId());
        assertEquals("broker-a", node.getStoreHost());
        assertEquals("10.0.0.1", node.getClientHost());
        assertEquals(23, node.getCostTime());
        assertEquals(1000L, node.getBeginTimestamp());
        assertEquals(1023L, node.getEndTimestamp());
        assertEquals(2, node.getRetryTimes());
        assertEquals("CONSUMED", node.getStatus());
        assertEquals("COMMIT_MESSAGE", node.getTransactionState());
        assertEquals("tx-1", node.getTransactionId());
        assertTrue(node.isFromTransactionCheck());
        assertEquals("Normal", node.getMsgType());
    }
}
