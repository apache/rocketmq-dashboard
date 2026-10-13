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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TraceNode}, one hop of a message trace: where it ran (store/client host), when
 * (begin/end timestamps), what it cost and how many retries it took — plus the flags that keep
 * the timeline honest: {@code fromTransactionCheck} separates server-side transaction-check
 * probes from real client hops, and the transaction state travels next to the plain status.
 * The sibling trace-model classes have tests; this node did not.
 */
class TraceNodeTest {

    @Test
    void aHopCarriesItsFullTimeline() {
        TraceNode node = new TraceNode();
        node.setRequestId("7f0000012345");
        node.setStoreHost("10.0.0.1:10911");
        node.setClientHost("192.168.1.10:53412");
        node.setCostTime(12);
        node.setBeginTimestamp(1700000000000L);
        node.setEndTimestamp(1700000000012L);
        node.setRetryTimes(1);
        node.setStatus("success");
        node.setTransactionState("COMMIT");
        node.setTransactionId("TX-4711");
        node.setFromTransactionCheck(false);
        node.setMsgType("NORMAL_MSG");

        assertThat(node.getRequestId()).isEqualTo("7f0000012345");
        assertThat(node.getStoreHost()).isEqualTo("10.0.0.1:10911");
        assertThat(node.getClientHost()).isEqualTo("192.168.1.10:53412");
        assertThat(node.getCostTime()).isEqualTo(12);
        assertThat(node.getBeginTimestamp()).isEqualTo(1700000000000L);
        assertThat(node.getEndTimestamp()).isEqualTo(1700000000012L);
        assertThat(node.getRetryTimes()).isEqualTo(1);
        assertThat(node.getStatus()).isEqualTo("success");
        assertThat(node.getTransactionState()).isEqualTo("COMMIT");
        assertThat(node.getTransactionId()).isEqualTo("TX-4711");
        assertThat(node.isFromTransactionCheck()).isFalse();
        assertThat(node.getMsgType()).isEqualTo("NORMAL_MSG");
    }

    @Test
    void aTransactionCheckProbeIsMarkedAsSuch() {
        TraceNode probe = new TraceNode();
        probe.setFromTransactionCheck(true);
        probe.setTransactionState("UNKNOW");

        assertThat(probe.isFromTransactionCheck()).isTrue();
        assertThat(probe.getTransactionState()).isEqualTo("UNKNOW");
    }

    @Test
    void aFreshNodeReadsAsEmptyNotDefaulted() {
        TraceNode node = new TraceNode();

        assertThat(node.getRequestId()).isNull();
        assertThat(node.getStoreHost()).isNull();
        assertThat(node.getClientHost()).isNull();
        assertThat(node.getCostTime()).isZero();
        assertThat(node.getBeginTimestamp()).isZero();
        assertThat(node.getEndTimestamp()).isZero();
        assertThat(node.getRetryTimes()).isZero();
        assertThat(node.getStatus()).isNull();
        assertThat(node.getTransactionState()).isNull();
        assertThat(node.getTransactionId()).isNull();
        assertThat(node.isFromTransactionCheck()).isFalse();
        assertThat(node.getMsgType()).isNull();
    }
}
