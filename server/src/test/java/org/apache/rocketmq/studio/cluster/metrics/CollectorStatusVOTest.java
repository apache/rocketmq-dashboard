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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the collector status of {@link CollectorStatusVO}: the configured collection interval
 * and the per-scope collector counts round-trip - the health line the metrics page shows.
 */
class CollectorStatusVOTest {

    @Test
    void theIntervalAndCountsRoundTrip() {
        CollectorStatusVO status = new CollectorStatusVO("PT30S", 3, 2);

        assertThat(status.collectionInterval()).isEqualTo("PT30S");
        assertThat(status.clusterCollectorCount()).isEqualTo(3);
        assertThat(status.businessCollectorCount()).isEqualTo(2);
    }

    @Test
    void aStatusWithoutCollectorsCountsZero() {
        CollectorStatusVO status = new CollectorStatusVO("PT30S", 0, 0);
        assertThat(status.clusterCollectorCount()).isZero();
        assertThat(status.businessCollectorCount()).isZero();
    }
}
