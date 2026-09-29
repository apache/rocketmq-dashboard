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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "studio.auth.login-required=false")
@ActiveProfiles("dev")
@Transactional
class MybatisPlusMetricSnapshotRepositoryIntegrationTest {
    @Autowired
    private MetricSnapshotRepository repository;

    @Test
    void saveAllShouldPersistAndReadMetricValuesOnH2Test() {
        Instant firstTime = Instant.parse("2026-09-29T00:00:00Z");
        MetricSample first = new MetricSample("h2.value.column", AlertDomain.CLUSTER,
                "h2-metric-value-column", null, Map.of(), 2.5D, MetricAvailability.AVAILABLE, firstTime);
        MetricSample second = new MetricSample("h2.value.column", AlertDomain.CLUSTER,
                "h2-metric-value-column", null, Map.of(), 0D, MetricAvailability.AVAILABLE,
                firstTime.plusSeconds(1));

        repository.saveAll(List.of(first, second));

        assertThat(repository.findRecent(first, firstTime.minusSeconds(1)))
                .extracting(MetricSample::value)
                .containsExactly(2.5D, 0D);
    }
}
