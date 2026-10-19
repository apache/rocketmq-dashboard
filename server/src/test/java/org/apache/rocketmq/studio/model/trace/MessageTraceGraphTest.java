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
 * Pins the message trace graph of {@link MessageTraceGraph}: a fresh graph is empty, and the
 * producer node, subscription list and trace views round-trip together - the whole trace of one
 * message under test.
 */
class MessageTraceGraphTest {

    @Test
    void aFreshGraphIsEmpty() {
        MessageTraceGraph graph = new MessageTraceGraph();
        assertThat(graph.getProducerNode()).isNull();
        assertThat(graph.getSubscriptionNodeList()).isNull();
        assertThat(graph.getMessageTraceViews()).isNull();
    }

    @Test
    void theWholeTraceRoundTrips() {
        TraceNode send = new TraceNode();
        send.setRequestId("req-1");
        send.setCostTime(5);

        ProducerNode producer = new ProducerNode();
        producer.setMsgId("msg-1");
        producer.setTopic("topic-1");
        producer.setGroupName("group-1");
        producer.setTraceNode(send);
        producer.setTransactionNodeList(List.of(send));

        TraceNode consume = new TraceNode();
        consume.setRequestId("req-2");
        SubscriptionNode subscription = new SubscriptionNode();
        subscription.setSubscriptionGroup("sub-group");
        subscription.setConsumeNodeList(List.of(consume));

        MessageTraceGraph graph = new MessageTraceGraph();
        graph.setProducerNode(producer);
        graph.setSubscriptionNodeList(List.of(subscription));
        graph.setMessageTraceViews(List.of(new Object()));

        assertThat(graph.getProducerNode().getMsgId()).isEqualTo("msg-1");
        assertThat(graph.getProducerNode().getTraceNode().getRequestId()).isEqualTo("req-1");
        assertThat(graph.getProducerNode().getTransactionNodeList()).hasSize(1);
        assertThat(graph.getSubscriptionNodeList()).hasSize(1);
        assertThat(graph.getSubscriptionNodeList().get(0).getSubscriptionGroup()).isEqualTo("sub-group");
        assertThat(graph.getSubscriptionNodeList().get(0).getConsumeNodeList().get(0).getRequestId())
                .isEqualTo("req-2");
        assertThat(graph.getMessageTraceViews()).hasSize(1);
    }
}
