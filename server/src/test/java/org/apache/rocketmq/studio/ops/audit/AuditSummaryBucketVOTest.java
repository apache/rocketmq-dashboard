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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the summary bucket of {@link AuditSummaryBucketVO}: one row of the audit dashboard's
 * breakdowns - a name and the count of audit records under it.
 */
class AuditSummaryBucketVOTest {

    @Test
    void theBucketRoundTripsThroughTheBuilder() {
        AuditSummaryBucketVO bucket = AuditSummaryBucketVO.builder()
                .name("CREATE_TOPIC")
                .count(42L)
                .build();

        assertThat(bucket.getName()).isEqualTo("CREATE_TOPIC");
        assertThat(bucket.getCount()).isEqualTo(42L);
    }

    @Test
    void aFreshBucketCountsZero() {
        AuditSummaryBucketVO bucket = new AuditSummaryBucketVO();
        assertThat(bucket.getName()).isNull();
        assertThat(bucket.getCount()).isZero();
    }
}
