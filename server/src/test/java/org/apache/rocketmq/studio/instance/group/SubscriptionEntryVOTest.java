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
package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the subscription entry of {@link SubscriptionEntryVO}: one subscribed topic with its
 * expression, type, filter mode and consistency verdict - the row the group detail's
 * subscription table renders.
 */
class SubscriptionEntryVOTest {

    @Test
    void theSubscriptionRoundTripsThroughTheBuilder() {
        SubscriptionEntryVO entry = SubscriptionEntryVO.builder()
                .topic("orders")
                .expression("TagA || TagB")
                .type("TAG")
                .filterMode("TAG")
                .consistency("consistent")
                .build();

        assertThat(entry.getTopic()).isEqualTo("orders");
        assertThat(entry.getExpression()).isEqualTo("TagA || TagB");
        assertThat(entry.getType()).isEqualTo("TAG");
        assertThat(entry.getFilterMode()).isEqualTo("TAG");
        assertThat(entry.getConsistency()).isEqualTo("consistent");
    }

    @Test
    void aFreshSubscriptionCarriesNothing() {
        SubscriptionEntryVO entry = new SubscriptionEntryVO();
        assertThat(entry.getTopic()).isNull();
        assertThat(entry.getExpression()).isNull();
        assertThat(entry.getConsistency()).isNull();
    }
}
