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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleTestResultVOTest {

    @Test
    void exposesSampleSections() {
        AlertRuleTestResultVO.Sample sample = AlertRuleTestResultVO.Sample.builder()
            .labels(Map.of("node", "broker-a"))
            .availability("AVAILABLE")
            .currentValue(0.9)
            .conditionMet(true)
            .unavailableReason(null)
            .build();

        AlertRuleTestResultVO vo = new AlertRuleTestResultVO(List.of(sample));

        assertEquals(1, vo.samples().size());
        assertEquals(Map.of("node", "broker-a"), sample.labels());
        assertEquals(0.9, sample.currentValue());
        assertTrue(sample.conditionMet());
    }

    @Test
    void nullSampleValueStaysAbsent() {
        AlertRuleTestResultVO.Sample sample = AlertRuleTestResultVO.Sample.builder()
            .availability("UNAVAILABLE")
            .conditionMet(false)
            .build();

        assertFalse(sample.conditionMet());
        assertEquals("UNAVAILABLE", sample.availability());
    }
}
