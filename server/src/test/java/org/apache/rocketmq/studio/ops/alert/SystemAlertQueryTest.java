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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the server-side alert-event filters of {@link SystemAlertQuery}: every filter field
 * round-trips, and the paging convenience constructor leaves the suppression filter unset -
 * "not filtering by suppression" and "filtering for unsuppressed" are different questions.
 */
class SystemAlertQueryTest {

    @Test
    void everyFilterRoundTrips() {
        SystemAlertQuery query = new SystemAlertQuery(
                "error", AlertDomain.BUSINESS, "instance-1", "firing",
                "group", "order", LocalDateTime.of(2026, 1, 2, 3, 0), LocalDateTime.of(2026, 1, 2, 5, 0),
                2, 20, true);

        assertThat(query.level()).isEqualTo("error");
        assertThat(query.domain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(query.instanceId()).isEqualTo("instance-1");
        assertThat(query.transition()).isEqualTo("firing");
        assertThat(query.labelKey()).isEqualTo("group");
        assertThat(query.labelValue()).isEqualTo("order");
        assertThat(query.from()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 0));
        assertThat(query.to()).isEqualTo(LocalDateTime.of(2026, 1, 2, 5, 0));
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.pageSize()).isEqualTo(20);
        assertThat(query.notificationSuppressed()).isTrue();
    }

    @Test
    void thePagingConstructorLeavesTheSuppressionFilterUnset() {
        SystemAlertQuery query = new SystemAlertQuery(
                null, null, null, null, null, null, null, null, 1, 20);

        assertThat(query.page()).isEqualTo(1);
        assertThat(query.pageSize()).isEqualTo(20);
        // the convenience constructor asks every question except suppression
        assertThat(query.notificationSuppressed()).isNull();
    }
}
