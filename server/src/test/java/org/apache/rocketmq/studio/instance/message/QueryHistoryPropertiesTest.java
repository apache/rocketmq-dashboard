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
 * Pins the query-history defaults of {@link QueryHistoryProperties}: a zero-config deployment
 * keeps 90 days of query history and cleans it up in bounded batches (500 rows per batch, 20
 * batches per pass) - the same budget pattern the alerting outbox cleanup uses.
 */
class QueryHistoryPropertiesTest {

    @Test
    void theRetentionDefaultIsNinetyDays() {
        assertThat(new QueryHistoryProperties().getRetentionDays()).isEqualTo(90);
    }

    @Test
    void theCleanupBudgetDefaultsAreBounded() {
        QueryHistoryProperties properties = new QueryHistoryProperties();
        assertThat(properties.getCleanupBatchSize()).isEqualTo(500);
        assertThat(properties.getCleanupMaxBatches()).isEqualTo(20);
    }

    @Test
    void explicitOverridesWin() {
        QueryHistoryProperties properties = new QueryHistoryProperties();
        properties.setRetentionDays(30);
        properties.setCleanupBatchSize(100);
        properties.setCleanupMaxBatches(5);

        assertThat(properties.getRetentionDays()).isEqualTo(30);
        assertThat(properties.getCleanupBatchSize()).isEqualTo(100);
        assertThat(properties.getCleanupMaxBatches()).isEqualTo(5);
    }
}
