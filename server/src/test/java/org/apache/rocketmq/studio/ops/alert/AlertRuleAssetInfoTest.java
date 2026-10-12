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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the asset summary of {@link AlertRuleAssetInfo}: one alertable asset - name, group, the
 * rule count covering it and the severities those rules use - the row the asset picker groups
 * by.
 */
class AlertRuleAssetInfoTest {

    @Test
    void theAssetSummaryRoundTrips() {
        AlertRuleAssetInfo asset = new AlertRuleAssetInfo(
                "order-cluster", "cluster", 3, List.of("critical", "warning"));

        assertThat(asset.name()).isEqualTo("order-cluster");
        assertThat(asset.group()).isEqualTo("cluster");
        assertThat(asset.ruleCount()).isEqualTo(3);
        assertThat(asset.severities()).containsExactly("critical", "warning");
    }

    @Test
    void theRecordIsValueEqual() {
        assertThat(new AlertRuleAssetInfo("a", "cluster", 1, List.of("warning")))
                .isEqualTo(new AlertRuleAssetInfo("a", "cluster", 1, List.of("warning")));
        assertThat(new AlertRuleAssetInfo("a", "cluster", 1, List.of("warning")))
                .isNotEqualTo(new AlertRuleAssetInfo("a", "cluster", 2, List.of("warning")));
    }
}
