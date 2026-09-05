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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrometheusAlertRuleTest {

    @Test
    void exposesAllRecordComponents() {
        PrometheusAlertRule rule = new PrometheusAlertRule(
                "DefaultCluster",
                "BrokerDiskHigh",
                "broker_disk_usage_ratio > 0.9",
                "5m",
                "critical",
                "rocketmq",
                "Broker disk usage is high",
                "Broker disk usage crossed the 90% threshold");

        assertEquals("DefaultCluster", rule.group());
        assertEquals("BrokerDiskHigh", rule.alert());
        assertEquals("broker_disk_usage_ratio > 0.9", rule.expr());
        assertEquals("5m", rule.duration());
        assertEquals("critical", rule.severity());
        assertEquals("rocketmq", rule.team());
        assertEquals("Broker disk usage is high", rule.summary());
        assertEquals("Broker disk usage crossed the 90% threshold", rule.description());
    }

    @Test
    void nullableFieldsStayNull() {
        PrometheusAlertRule rule = new PrometheusAlertRule("g", "a", "e", "1m", "warning", null, null, null);

        assertNull(rule.team());
        assertNull(rule.summary());
        assertNull(rule.description());
    }

    @Test
    void equalityFollowsRecordComponents() {
        PrometheusAlertRule a = new PrometheusAlertRule("g", "a", "e", "1m", "warning", "t", "s", "d");
        PrometheusAlertRule same = new PrometheusAlertRule("g", "a", "e", "1m", "warning", "t", "s", "d");
        PrometheusAlertRule different = new PrometheusAlertRule("g2", "a", "e", "1m", "warning", "t", "s", "d");

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
