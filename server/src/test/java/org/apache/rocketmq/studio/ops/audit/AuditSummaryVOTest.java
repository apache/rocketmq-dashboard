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
package org.apache.rocketmq.studio.ops.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditSummaryVOTest {

    @Test
    void builderDefaultsDescribeEmptySummary() {
        AuditSummaryVO vo = AuditSummaryVO.builder().build();

        assertEquals(0L, vo.getTotal());
        assertEquals(0L, vo.getSuccessful());
        assertEquals(0L, vo.getFailed());
        assertEquals(0L, vo.getPartial());
        assertEquals(0L, vo.getUniqueOperators());
        assertNull(vo.getLatestAt());
        assertNull(vo.getByOperation());
        assertNull(vo.getByResourceType());
    }

    @Test
    void allArgsCarrySummaryState() {
        LocalDateTime latest = LocalDateTime.parse("2026-09-01T08:00:00");

        AuditSummaryVO vo = AuditSummaryVO.builder()
            .total(10L)
            .successful(8L)
            .failed(1L)
            .partial(1L)
            .uniqueOperators(3L)
            .latestAt(latest)
            .byOperation(List.of())
            .byResourceType(List.of())
            .build();

        assertEquals(10L, vo.getTotal());
        assertEquals(8L, vo.getSuccessful());
        assertEquals(1L, vo.getFailed());
        assertEquals(1L, vo.getPartial());
        assertEquals(3L, vo.getUniqueOperators());
        assertEquals(latest, vo.getLatestAt());
        assertEquals(List.of(), vo.getByOperation());
    }
}
