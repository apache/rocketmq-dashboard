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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule test preview of {@link AlertRuleTestResultVO}: the sample series the test-run
 * dialog renders - each sample's labels, the availability it came from, the evaluated value and
 * whether the rule's condition was met.
 */
class AlertRuleTestResultVOTest {

    @Test
    void theSampleSeriesRoundTrips() {
        AlertRuleTestResultVO.Sample sample = new AlertRuleTestResultVO.Sample(
                Map.of("group", "order"),
                "AVAILABLE",
                1200.5,
                true,
                "2026-01-02T03:04:05Z");
        AlertRuleTestResultVO result = new AlertRuleTestResultVO(List.of(sample));

        assertThat(result.samples()).hasSize(1);
        AlertRuleTestResultVO.Sample only = result.samples().get(0);
        assertThat(only.labels()).containsEntry("group", "order");
        assertThat(only.availability()).isEqualTo("AVAILABLE");
        assertThat(only.currentValue()).isEqualTo(1200.5);
        assertThat(only.conditionMet()).isTrue();
    }

    @Test
    void anEmptyPreviewIsAnEmptySeries() {
        assertThat(new AlertRuleTestResultVO(List.of()).samples()).isEmpty();
    }
}
