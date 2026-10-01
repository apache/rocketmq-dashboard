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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionConsistencyTest {

    @Test
    void mapsTheAliyunBooleanFlagTest() {
        assertThat(SubscriptionConsistency.fromBoolean(Boolean.TRUE)).isEqualTo("consistent");
        assertThat(SubscriptionConsistency.fromBoolean(Boolean.FALSE)).isEqualTo("inconsistent");
        assertThat(SubscriptionConsistency.fromBoolean(null)).isNull();
    }

    @Test
    void mapsTheTencentConsistencyCodeTest() {
        assertThat(SubscriptionConsistency.fromCode(0L)).isEqualTo("consistent");
        assertThat(SubscriptionConsistency.fromCode(1L)).isEqualTo("inconsistent");
        assertThat(SubscriptionConsistency.fromCode(null)).isNull();
    }

    @Test
    void keepsUndocumentedTencentCodesUnknownTest() {
        assertThat(SubscriptionConsistency.fromCode(2L)).isNull();
        assertThat(SubscriptionConsistency.fromCode(-1L)).isNull();
    }
}
