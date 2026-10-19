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

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the audit summary view of {@link AuditSummaryVO}: the aggregate counters (total,
 * per-outcome, unique operators), the latest timestamp and the two breakdowns (by operation,
 * by resource type) round-trip, and a fresh summary counts zero of everything.
 */
class AuditSummaryVOTest {

    @Test
    void theSummaryRoundTrips() {
        AuditSummaryBucketVO bucket = AuditSummaryBucketVO.builder().name("CREATE_TOPIC").count(3L).build();
        AuditSummaryVO summary = AuditSummaryVO.builder()
                .total(100L)
                .successful(80L)
                .failed(15L)
                .partial(5L)
                .uniqueOperators(3L)
                .latestAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .byOperation(List.of(bucket))
                .byResourceType(List.of(bucket, bucket))
                .build();

        assertThat(summary.getTotal()).isEqualTo(100L);
        assertThat(summary.getSuccessful()).isEqualTo(80L);
        assertThat(summary.getFailed()).isEqualTo(15L);
        assertThat(summary.getPartial()).isEqualTo(5L);
        assertThat(summary.getUniqueOperators()).isEqualTo(3L);
        assertThat(summary.getLatestAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(summary.getByOperation()).hasSize(1);
        assertThat(summary.getByResourceType()).hasSize(2);
    }

    @Test
    void aFreshSummaryCountsZeroOfEverything() {
        AuditSummaryVO summary = new AuditSummaryVO();
        assertThat(summary.getTotal()).isZero();
        assertThat(summary.getSuccessful()).isZero();
        assertThat(summary.getFailed()).isZero();
        assertThat(summary.getPartial()).isZero();
        assertThat(summary.getUniqueOperators()).isZero();
        assertThat(summary.getByOperation()).isNull();
    }
}
