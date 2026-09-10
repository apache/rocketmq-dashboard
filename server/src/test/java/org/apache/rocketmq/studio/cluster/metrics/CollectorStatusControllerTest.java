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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CollectorStatusControllerTest {
    @Test
    void reportsNativeCollectionMetadataTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionInterval("PT1M");

        CollectorStatusVO status = new CollectorStatusController(properties, List.of(), List.of()).status().getData();

        assertThat(status.collectionInterval()).isEqualTo("PT1M");
        assertThat(status.clusterCollectorCount()).isZero();
        assertThat(status.businessCollectorCount()).isZero();
    }

    @Test
    void reportsRegisteredCollectorCountsTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionInterval("PT5M");
        ClusterMetricsCollector clusterA = mock(ClusterMetricsCollector.class);
        ClusterMetricsCollector clusterB = mock(ClusterMetricsCollector.class);
        BusinessMetricsCollector businessA = mock(BusinessMetricsCollector.class);
        BusinessMetricsCollector businessB = mock(BusinessMetricsCollector.class);
        BusinessMetricsCollector businessC = mock(BusinessMetricsCollector.class);

        CollectorStatusVO status = new CollectorStatusController(properties,
                List.of(clusterA, clusterB), List.of(businessA, businessB, businessC)).status().getData();

        assertThat(status.collectionInterval()).isEqualTo("PT5M");
        assertThat(status.clusterCollectorCount()).isEqualTo(2);
        assertThat(status.businessCollectorCount()).isEqualTo(3);
    }
}
