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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Prometheus defaults of {@link PrometheusProperties}: the connection budget (3s
 * connect, 10s read), and the default metric profile that decides which profile the catalog
 * lists first - the 5.x native profile unless configured otherwise.
 */
class PrometheusPropertiesTest {

    @Test
    void theConnectionBudgetDefaultsAreLoadBearing() {
        PrometheusProperties properties = new PrometheusProperties();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void theDefaultProfileIsTheFiveXNativeOne() {
        assertThat(new PrometheusProperties().getProfile()).isEqualTo("rocketmq5-native");
    }

    @Test
    void theCredentialsDefaultToUnauthenticated() {
        PrometheusProperties properties = new PrometheusProperties();
        assertThat(properties.getUsername()).isNull();
        assertThat(properties.getPassword()).isNull();
        assertThat(properties.getBearerToken()).isNull();
    }
}
