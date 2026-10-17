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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the producer side of a message trace: {@link ProducerNode} carries the message identity
 * (msgId, offSetMsgId, tags, keys), its topic and group, the send trace node and the transaction
 * branch nodes.
 */
class ProducerNodeTest {

    @Test
    void everyFieldRoundTrips() {
        TraceNode send = new TraceNode();
        send.setRequestId("req-1");
        TraceNode branch = new TraceNode();
        branch.setRequestId("req-2");

        ProducerNode producer = new ProducerNode();
        producer.setMsgId("msg-1");
        producer.setTags("tag-a");
        producer.setKeys("key-1");
        producer.setOffSetMsgId("offset-1");
        producer.setTopic("topic-1");
        producer.setGroupName("group-1");
        producer.setTraceNode(send);
        producer.setTransactionNodeList(List.of(branch));

        assertThat(producer.getMsgId()).isEqualTo("msg-1");
        assertThat(producer.getTags()).isEqualTo("tag-a");
        assertThat(producer.getKeys()).isEqualTo("key-1");
        assertThat(producer.getOffSetMsgId()).isEqualTo("offset-1");
        assertThat(producer.getTopic()).isEqualTo("topic-1");
        assertThat(producer.getGroupName()).isEqualTo("group-1");
        assertThat(producer.getTraceNode()).isSameAs(send);
        assertThat(producer.getTransactionNodeList()).containsExactly(branch);
    }

    @Test
    void aFreshProducerCarriesNoTrace() {
        ProducerNode producer = new ProducerNode();
        assertThat(producer.getTraceNode()).isNull();
        assertThat(producer.getTransactionNodeList()).isNull();
    }
}
