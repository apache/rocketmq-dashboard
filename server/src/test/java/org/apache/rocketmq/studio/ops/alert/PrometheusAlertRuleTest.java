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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the serialized Prometheus rule of {@link PrometheusAlertRule}: the group/alert identity
 * with the expression, duration and the annotation block (severity, team, summary,
 * description) round-trip - the exact shape that round-trips through the rules transfer YAML.
 */
class PrometheusAlertRuleTest {

    @Test
    void theRuleRoundTrips() {
        PrometheusAlertRule rule = new PrometheusAlertRule(
                "rocketmq-alerts",
                "ConsumerLagHigh",
                "rocketmq_consumer_lag_messages > 1000",
                "5m",
                "warning",
                "middleware",
                "Consumer lag is high",
                "Consumer lag for the group has exceeded the threshold for 5 minutes");

        assertThat(rule.group()).isEqualTo("rocketmq-alerts");
        assertThat(rule.alert()).isEqualTo("ConsumerLagHigh");
        assertThat(rule.expr()).isEqualTo("rocketmq_consumer_lag_messages > 1000");
        assertThat(rule.duration()).isEqualTo("5m");
        assertThat(rule.severity()).isEqualTo("warning");
        assertThat(rule.team()).isEqualTo("middleware");
        assertThat(rule.summary()).isEqualTo("Consumer lag is high");
        assertThat(rule.description()).contains("exceeded the threshold");
    }

    @Test
    void theRuleIsValueEqual() {
        assertThat(new PrometheusAlertRule("g", "a", "e", "5m", "warning", null, null, null))
                .isEqualTo(new PrometheusAlertRule("g", "a", "e", "5m", "warning", null, null, null));
        assertThat(new PrometheusAlertRule("g", "a", "e", "5m", "warning", null, null, null))
                .isNotEqualTo(new PrometheusAlertRule("g", "a", "e", "10m", "warning", null, null, null));
    }
}
