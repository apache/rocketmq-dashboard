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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueryHistorySummaryVOTest {

    @Test
    void builderDefaultsDescribeEmptyHistory() {
        QueryHistorySummaryVO vo = QueryHistorySummaryVO.builder().build();

        assertEquals(0L, vo.getMessageQueries());
        assertEquals(0L, vo.getTraceQueries());
        assertNull(vo.getLatestQueryAt());
    }

    @Test
    void allArgsCarryHistorySummary() {
        LocalDateTime latest = LocalDateTime.parse("2026-09-01T08:00:00");

        QueryHistorySummaryVO vo = QueryHistorySummaryVO.builder()
            .messageQueries(7L)
            .traceQueries(3L)
            .latestQueryAt(latest)
            .build();

        assertEquals(7L, vo.getMessageQueries());
        assertEquals(3L, vo.getTraceQueries());
        assertEquals(latest, vo.getLatestQueryAt());
    }
}
