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
package org.apache.rocketmq.studio.cluster.metrics;

import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetricSampleTest {

    @Test
    void unavailableSamplesCannotCarryAValueTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, 0D, MetricAvailability.UNAVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only available metric samples may have a value");
    }

    @Test
    void availableSamplesRequireAValueTest() {
        assertThatThrownBy(() -> new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null,
                null, null, MetricAvailability.AVAILABLE, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Available metric samples require a value");
    }
}
