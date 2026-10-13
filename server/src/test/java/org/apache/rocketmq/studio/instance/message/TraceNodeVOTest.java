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

package org.apache.rocketmq.studio.instance.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TraceNodeVO}, one node of a message trace timeline: what happened (title),
 * when (timestamp), how it ended (status), what it cost (costTime) and the human-readable
 * description under it. The trace timeline sorts and badges by exactly these fields, so all
 * construction paths must agree and the no-args path must read as an empty node, not a
 * defaulted one.
 */
class TraceNodeVOTest {

    @Test
    void aNodeCarriesTheFullTimelineEntry() {
        TraceNodeVO node = TraceNodeVO.builder()
                .title("Send to order-topic")
                .timestamp(1700000000000L)
                .status("success")
                .costTime(12)
                .description("delivered to queue 3, broker-a")
                .build();

        assertThat(node.getTitle()).isEqualTo("Send to order-topic");
        assertThat(node.getTimestamp()).isEqualTo(1700000000000L);
        assertThat(node.getStatus()).isEqualTo("success");
        assertThat(node.getCostTime()).isEqualTo(12);
        assertThat(node.getDescription()).isEqualTo("delivered to queue 3, broker-a");
    }

    @Test
    void allConstructionPathsAgree() {
        TraceNodeVO allArgs = new TraceNodeVO(
                "Client receive", 1700000010000L, "success", 3, "consumer@192.168.1.20");
        TraceNodeVO built = TraceNodeVO.builder()
                .title("Client receive")
                .timestamp(1700000010000L)
                .status("success")
                .costTime(3)
                .description("consumer@192.168.1.20")
                .build();
        TraceNodeVO defaulted = new TraceNodeVO();
        defaulted.setTitle("Client receive");
        defaulted.setTimestamp(1700000010000L);
        defaulted.setStatus("success");
        defaulted.setCostTime(3);
        defaulted.setDescription("consumer@192.168.1.20");

        assertThat(allArgs).isEqualTo(built).isEqualTo(defaulted);

        TraceNodeVO empty = new TraceNodeVO();
        assertThat(empty.getTitle()).isNull();
        assertThat(empty.getTimestamp()).isZero();
        assertThat(empty.getStatus()).isNull();
        assertThat(empty.getCostTime()).isZero();
        assertThat(empty.getDescription()).isNull();
    }
}
