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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule-feed filter shape of {@link AlertRuleQuery}: every filter round-trips, and the
 * enabled flag is a three-state Boolean - null means "both enabled and disabled", which is a
 * different query from either explicit state.
 */
class AlertRuleQueryTest {

    @Test
    void everyFilterRoundTrips() {
        AlertRuleQuery query = new AlertRuleQuery(
                AlertDomain.CLUSTER, "lag", Boolean.TRUE, 2, 20);

        assertThat(query.domain()).isEqualTo(AlertDomain.CLUSTER);
        assertThat(query.search()).isEqualTo("lag");
        assertThat(query.enabled()).isTrue();
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.pageSize()).isEqualTo(20);
    }

    @Test
    void aQueryWithoutFiltersAsksForEverything() {
        AlertRuleQuery query = new AlertRuleQuery(null, null, null, 1, 20);

        assertThat(query.domain()).isNull();
        assertThat(query.search()).isNull();
        // null enabled means both states - not "disabled"
        assertThat(query.enabled()).isNull();
    }
}
