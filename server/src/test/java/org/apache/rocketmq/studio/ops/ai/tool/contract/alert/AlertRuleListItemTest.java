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
package org.apache.rocketmq.studio.ops.ai.tool.contract.alert;

import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AlertRuleListItem}: the tool-side listing projection of an alert rule. The compact
 * constructor's null-safety is the contract - a rule saved before the channels field existed must
 * list with an empty channel set, not crash the whole listing.
 */
class AlertRuleListItemTest {

    @Test
    void mapsEveryListingField() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(7L).name("lag-high").metric("consumer_lag_messages")
                .operator(">").threshold(100000).thresholdUnit("messages")
                .duration("5m").channels(List.of("email", "webhook"))
                .enabled(true).description("lag exceeds 100k")
                .build();

        AlertRuleListItem item = AlertRuleListItem.from(rule);

        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.name()).isEqualTo("lag-high");
        assertThat(item.metric()).isEqualTo("consumer_lag_messages");
        assertThat(item.operator()).isEqualTo(">");
        assertThat(item.threshold()).isEqualTo(100000);
        assertThat(item.thresholdUnit()).isEqualTo("messages");
        assertThat(item.duration()).isEqualTo("5m");
        assertThat(item.channels()).containsExactly("email", "webhook");
        assertThat(item.enabled()).isTrue();
        assertThat(item.description()).isEqualTo("lag exceeds 100k");
    }

    @Test
    void aRuleWithoutChannelsListsWithAnEmptySet() {
        // a rule saved before the channels field existed (or with channels
        // unset) must not cost the listing its projection
        AlertRuleVO rule = AlertRuleVO.builder().name("minimal").build();

        AlertRuleListItem item = AlertRuleListItem.from(rule);

        assertThat(item.channels()).isNotNull().isEmpty();
        assertThat(item.name()).isEqualTo("minimal");
    }

    @Test
    void anExplicitlyNullChannelsSetAlsoNormalises() {
        AlertRuleListItem direct = new AlertRuleListItem(
                1L, "direct", "m", ">", 1, "x", "1m", null, false, null);

        assertThat(direct.channels()).isNotNull().isEmpty();
    }

    @Test
    void aDisabledRuleListsAsDisabled() {
        AlertRuleVO rule = AlertRuleVO.builder().name("paused").enabled(false).build();

        assertThat(AlertRuleListItem.from(rule).enabled()).isFalse();
    }
}
