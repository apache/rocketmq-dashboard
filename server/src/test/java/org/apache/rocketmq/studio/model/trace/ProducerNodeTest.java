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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProducerNodeTest {

    @Test
    void freshNodeCarriesNullFields() {
        ProducerNode node = new ProducerNode();

        assertNull(node.getMsgId());
        assertNull(node.getTags());
        assertNull(node.getKeys());
        assertNull(node.getOffSetMsgId());
        assertNull(node.getTopic());
        assertNull(node.getGroupName());
        assertNull(node.getTraceNode());
        assertNull(node.getTransactionNodeList());
    }

    @Test
    void settersRoundTripEveryField() {
        ProducerNode node = new ProducerNode();
        TraceNode trace = new TraceNode();
        trace.setRequestId("req-1");
        TraceNode transaction = new TraceNode();
        transaction.setRequestId("tx-1");

        node.setMsgId("msg-1");
        node.setTags("created");
        node.setKeys("order-1");
        node.setOffSetMsgId("commit-1");
        node.setTopic("orders");
        node.setGroupName("cg-1");
        node.setTraceNode(trace);
        node.setTransactionNodeList(List.of(transaction));

        assertEquals("msg-1", node.getMsgId());
        assertEquals("created", node.getTags());
        assertEquals("order-1", node.getKeys());
        assertEquals("commit-1", node.getOffSetMsgId());
        assertEquals("orders", node.getTopic());
        assertEquals("cg-1", node.getGroupName());
        assertEquals(trace, node.getTraceNode());
        assertEquals(List.of(transaction), node.getTransactionNodeList());
    }
}
