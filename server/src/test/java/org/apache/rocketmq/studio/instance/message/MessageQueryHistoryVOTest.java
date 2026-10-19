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
 * Pins {@link MessageQueryHistoryVO}, one row of the message query history: what was asked
 * (type, topic, msgId, tag, key, time window), what came back (resultCount) and who asked
 * when (cluster, user, timestamp). The history table renders exactly these columns, so the
 * row must carry the full query and serialise every column to the frontend.
 */
class MessageQueryHistoryVOTest {

    @Test
    void aHistoryRowCarriesTheFullQuery() {
        MessageQueryHistoryVO row = MessageQueryHistoryVO.builder()
                .id(91L)
                .queryType("BY_MSG_ID")
                .topic("order-topic")
                .msgId("AC11000100001A3B0000000000000000")
                .tag("created")
                .messageKey("order-4711")
                .startTime(1700000000000L)
                .endTime(1700003600000L)
                .resultCount(3)
                .clusterId("cluster-1")
                .queriedBy("ops-admin")
                .queriedAt(LocalDateTime.of(2026, 10, 13, 9, 30))
                .build();

        assertThat(row.getId()).isEqualTo(91L);
        assertThat(row.getQueryType()).isEqualTo("BY_MSG_ID");
        assertThat(row.getTopic()).isEqualTo("order-topic");
        assertThat(row.getMsgId()).isEqualTo("AC11000100001A3B0000000000000000");
        assertThat(row.getTag()).isEqualTo("created");
        assertThat(row.getMessageKey()).isEqualTo("order-4711");
        assertThat(row.getStartTime()).isEqualTo(1700000000000L);
        assertThat(row.getEndTime()).isEqualTo(1700003600000L);
        assertThat(row.getResultCount()).isEqualTo(3);
        assertThat(row.getClusterId()).isEqualTo("cluster-1");
        assertThat(row.getQueriedBy()).isEqualTo("ops-admin");
        assertThat(row.getQueriedAt()).isEqualTo(LocalDateTime.of(2026, 10, 13, 9, 30));
    }

    @Test
    void theRowSerialisesWithEveryColumn() throws Exception {
        MessageQueryHistoryVO row = MessageQueryHistoryVO.builder()
                .queryType("BY_TOPIC")
                .topic("order-topic")
                .resultCount(0)
                .queriedAt(LocalDateTime.of(2026, 10, 13, 9, 30))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(row);

        assertThat(json).contains("\"queryType\":\"BY_TOPIC\"");
        assertThat(json).contains("\"topic\":\"order-topic\"");
        assertThat(json).contains("\"resultCount\":0");
        assertThat(json).contains("\"queriedAt\"");
    }
}
