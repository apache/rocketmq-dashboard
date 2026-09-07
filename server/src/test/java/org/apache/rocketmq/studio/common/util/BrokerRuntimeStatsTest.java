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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerRuntimeStatsTest {

    @Test
    void prefersFiveXKeyWhenPresentTest() {
        Map<String, String> stats = new HashMap<>();
        stats.put("getTransferredTps", "5.0 6.0 7.0");
        stats.put("getTransferedTps", "4.0 5.0 6.0");
        assertThat(BrokerRuntimeStats.outboundTps(stats)).isEqualTo("5.0 6.0 7.0");
    }

    @Test
    void fallsBackToFourXKeyWhenFiveXAbsentTest() {
        Map<String, String> stats = new HashMap<>();
        stats.put("getTransferedTps", "4.0 5.0 6.0");
        assertThat(BrokerRuntimeStats.outboundTps(stats)).isEqualTo("4.0 5.0 6.0");
    }

    @Test
    void fallsBackToFourXKeyWhenFiveXBlankTest() {
        Map<String, String> stats = new HashMap<>();
        stats.put("getTransferredTps", "   ");
        stats.put("getTransferedTps", "4.0 5.0 6.0");
        assertThat(BrokerRuntimeStats.outboundTps(stats)).isEqualTo("4.0 5.0 6.0");
    }

    @Test
    void returnsNullWhenNeitherKeyUsableTest() {
        assertThat(BrokerRuntimeStats.outboundTps(new HashMap<>())).isNull();
        assertThat(BrokerRuntimeStats.outboundTps(null)).isNull();
    }
}
