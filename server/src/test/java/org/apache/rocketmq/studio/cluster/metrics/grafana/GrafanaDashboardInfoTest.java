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

package org.apache.rocketmq.studio.cluster.metrics.grafana;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link GrafanaDashboardInfo}, the metadata of a bundled Grafana dashboard: the
 * {@code uid} doubles as the lookup key — it matches the JSON file name without the
 * {@code .json} suffix — so a dashboard whose uid stops matching its file silently
 * disappears from the lookup. Title, description and tags feed the dashboard picker.
 */
class GrafanaDashboardInfoTest {

    @Test
    void aDashboardCarriesItsLookupKeyAndPresentation() {
        GrafanaDashboardInfo dashboard = new GrafanaDashboardInfo(
                "rocketmq-cluster-overview",
                "RocketMQ Cluster Overview",
                "Broker and topic health at a glance",
                List.of("rocketmq", "cluster"));

        assertThat(dashboard.uid()).isEqualTo("rocketmq-cluster-overview");
        assertThat(dashboard.title()).isEqualTo("RocketMQ Cluster Overview");
        assertThat(dashboard.description()).isEqualTo("Broker and topic health at a glance");
        assertThat(dashboard.tags()).containsExactly("rocketmq", "cluster");
    }

    @Test
    void theUidIsTheLookupKeyAndIdenticalMetadataIsValueEqual() {
        GrafanaDashboardInfo left = new GrafanaDashboardInfo(
                "rocketmq-cluster-overview", "RocketMQ Cluster Overview", null, List.of());
        GrafanaDashboardInfo right = new GrafanaDashboardInfo(
                "rocketmq-cluster-overview", "RocketMQ Cluster Overview", null, List.of());

        assertThat(left).isEqualTo(right);
        assertThat(left).isNotEqualTo(new GrafanaDashboardInfo(
                "rocketmq-dlq", "RocketMQ Cluster Overview", null, List.of()));
    }
}
