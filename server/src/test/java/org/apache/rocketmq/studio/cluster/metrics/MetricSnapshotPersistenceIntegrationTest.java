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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies metric snapshot persistence against the documented H2 development profile. */
@SpringBootTest
@ActiveProfiles("dev")
class MetricSnapshotPersistenceIntegrationTest {

    @Autowired
    private MetricSnapshotRepository repository;

    @Test
    void shouldPersistAndReadMetricSnapshotBatchThroughTheDevSchemaTest() {
        Instant collectedAt = Instant.parse("2026-01-01T00:00:00Z");
        MetricSample availability = new MetricSample(
                "broker.availability", AlertDomain.CLUSTER, "runtime", null, Map.of(), 1D,
                MetricAvailability.AVAILABLE, collectedAt);
        MetricSample tps = new MetricSample(
                "broker.tps", AlertDomain.CLUSTER, "runtime", null, Map.of(), 2D,
                MetricAvailability.AVAILABLE, collectedAt.plusMillis(1));

        repository.saveAll(List.of(availability, tps));

        List<MetricSample> persisted = repository.findRecent(availability, collectedAt.minusSeconds(1));
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).value()).isEqualTo(1D);
    }
}
