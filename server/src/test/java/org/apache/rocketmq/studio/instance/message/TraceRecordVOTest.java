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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the trace view of {@link TraceRecordVO}: the node list and the per-group consume
 * status travel together - one is the topology, the other the delivery outcome per group.
 */
class TraceRecordVOTest {

    @Test
    void theNodesAndConsumeStatusTravelTogether() {
        TraceNodeVO node = TraceNodeVO.builder().title("send").build();
        ConsumerStatusVO status = ConsumerStatusVO.builder().group("order-group").build();
        TraceRecordVO record = TraceRecordVO.builder()
                .nodes(List.of(node))
                .consumerStatus(List.of(status))
                .build();

        assertThat(record.getNodes()).containsExactly(node);
        assertThat(record.getConsumerStatus()).containsExactly(status);
    }

    @Test
    void aFreshTraceCarriesNeither() {
        TraceRecordVO record = new TraceRecordVO();
        assertThat(record.getNodes()).isNull();
        assertThat(record.getConsumerStatus()).isNull();
    }
}
