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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TraceQueryHistoryVO}, one row of the trace query history: which message was
 * traced (msgId, topic, trace topic), how big the answer was (node and consumer counts) and
 * who asked when (cluster, user, timestamp). The trace history table renders exactly these
 * columns, so the row must carry the full trace and serialise every column.
 */
class TraceQueryHistoryVOTest {

    @Test
    void aHistoryRowCarriesTheFullTraceQuery() {
        TraceQueryHistoryVO row = TraceQueryHistoryVO.builder()
                .id(77L)
                .msgId("AC11000100001A3B0000000000000000")
                .topic("order-topic")
                .traceTopic("RMQ_SYS_TRACE_TOPIC")
                .nodeCount(5)
                .consumerCount(2)
                .clusterId("cluster-1")
                .queriedBy("ops-admin")
                .queriedAt(LocalDateTime.of(2026, 10, 13, 11, 0))
                .build();

        assertThat(row.getId()).isEqualTo(77L);
        assertThat(row.getMsgId()).isEqualTo("AC11000100001A3B0000000000000000");
        assertThat(row.getTopic()).isEqualTo("order-topic");
        assertThat(row.getTraceTopic()).isEqualTo("RMQ_SYS_TRACE_TOPIC");
        assertThat(row.getNodeCount()).isEqualTo(5);
        assertThat(row.getConsumerCount()).isEqualTo(2);
        assertThat(row.getClusterId()).isEqualTo("cluster-1");
        assertThat(row.getQueriedBy()).isEqualTo("ops-admin");
        assertThat(row.getQueriedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 11, 0));
    }

    @Test
    void theRowSerialisesWithEveryColumn() throws Exception {
        TraceQueryHistoryVO row = TraceQueryHistoryVO.builder()
                .msgId("AC11000100001A3B0000000000000000")
                .nodeCount(5)
                .queriedAt(LocalDateTime.of(2026, 10, 13, 11, 0))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(row);

        assertThat(json).contains("\"msgId\":\"AC11000100001A3B0000000000000000\"");
        assertThat(json).contains("\"nodeCount\":5");
        assertThat(json).contains("\"queriedAt\"");
    }
}
