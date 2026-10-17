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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the alert rule entity {@link RmqAlertRule}: it maps to the {@code rmq_alert_rule} table
 * and every column round-trips - the persisted form of the rule the alert engine evaluates.
 */
class RmqAlertRuleTest {

    @Test
    void theEntityMapsToTheRuleTable() {
        TableName annotation = RmqAlertRule.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_alert_rule");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAlertRule rule = new RmqAlertRule();
        rule.setId(7L);
        rule.setDomain("BUSINESS");
        rule.setName("lag");
        rule.setMetric("consumer_lag_messages");
        rule.setOperator(">");
        rule.setThreshold(1000.0);
        rule.setThresholdUnit("messages");
        rule.setDuration("5m");
        rule.setAggregation("AVG");
        rule.setWindowSeconds(300);
        rule.setChannels("dingtalk");
        rule.setEnabled(true);
        rule.setLastTriggered("2026-01-02T03:04:05Z");
        rule.setDescription("consumer lag");
        rule.setBrokerName("broker-a");
        rule.setClusterName("cluster-1");
        rule.setSeverity("warning");
        rule.setInstanceId("instance-1");
        rule.setConsumerGroup("order-group");

        assertThat(rule.getId()).isEqualTo(7L);
        assertThat(rule.getDomain()).isEqualTo("BUSINESS");
        assertThat(rule.getName()).isEqualTo("lag");
        assertThat(rule.getMetric()).isEqualTo("consumer_lag_messages");
        assertThat(rule.getOperator()).isEqualTo(">");
        assertThat(rule.getThreshold()).isEqualTo(1000.0);
        assertThat(rule.getThresholdUnit()).isEqualTo("messages");
        assertThat(rule.getDuration()).isEqualTo("5m");
        assertThat(rule.getAggregation()).isEqualTo("AVG");
        assertThat(rule.getWindowSeconds()).isEqualTo(300);
        assertThat(rule.getChannels()).isEqualTo("dingtalk");
        assertThat(rule.getEnabled()).isTrue();
        assertThat(rule.getLastTriggered()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(rule.getDescription()).isEqualTo("consumer lag");
        assertThat(rule.getBrokerName()).isEqualTo("broker-a");
        assertThat(rule.getClusterName()).isEqualTo("cluster-1");
        assertThat(rule.getSeverity()).isEqualTo("warning");
        assertThat(rule.getInstanceId()).isEqualTo("instance-1");
        assertThat(rule.getConsumerGroup()).isEqualTo("order-group");
    }
}
