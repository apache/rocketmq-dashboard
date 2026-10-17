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

package org.apache.rocketmq.studio.cluster.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ClusterConfigPreviewVO}, the answer of the config update preview: the current
 * and the proposed config side by side, one row per differing field with the broker property
 * it maps to, the brokers the change will hit — and the {@code changed} verdict that lets the
 * UI show a diff or say "nothing to apply" without re-deriving it from the change list.
 */
class ClusterConfigPreviewVOTest {

    @Test
    void aPreviewCarriesBothSidesAndOneRowPerDifferingField() {
        ClusterConfigVO current = ClusterConfigVO.builder().writeQueueNums(8).build();
        ClusterConfigVO proposed = ClusterConfigVO.builder().writeQueueNums(16).build();

        ClusterConfigPreviewVO preview = ClusterConfigPreviewVO.builder()
                .currentConfig(current)
                .proposedConfig(proposed)
                .targetBrokers(List.of(
                        ClusterConfigPreviewVO.BrokerTargetVO.builder()
                                .name("broker-a").address("10.0.0.1:10911").build()))
                .brokerProperties(Map.of("writeQueueNums", "16"))
                .changes(List.of(ClusterConfigPreviewVO.ConfigChangeVO.builder()
                        .field("writeQueueNums")
                        .currentValue("8")
                        .proposedValue("16")
                        .brokerProperty("defaultWriteQueueNums")
                        .build()))
                .changed(true)
                .build();

        assertThat(preview.getCurrentConfig().getWriteQueueNums()).isEqualTo(8);
        assertThat(preview.getProposedConfig().getWriteQueueNums()).isEqualTo(16);
        assertThat(preview.getTargetBrokers()).hasSize(1);
        assertThat(preview.getTargetBrokers().get(0).getName()).isEqualTo("broker-a");
        assertThat(preview.getTargetBrokers().get(0).getAddress()).isEqualTo("10.0.0.1:10911");
        assertThat(preview.getBrokerProperties()).containsEntry("writeQueueNums", "16");

        ClusterConfigPreviewVO.ConfigChangeVO change = preview.getChanges().get(0);
        assertThat(change.getField()).isEqualTo("writeQueueNums");
        assertThat(change.getCurrentValue()).isEqualTo("8");
        assertThat(change.getProposedValue()).isEqualTo("16");
        assertThat(change.getBrokerProperty()).isEqualTo("defaultWriteQueueNums");
        assertThat(preview.isChanged()).isTrue();
    }

    @Test
    void anUnchangedPreviewCarriesTheHonestVerdict() {
        ClusterConfigVO same = ClusterConfigVO.builder().writeQueueNums(8).build();

        ClusterConfigPreviewVO preview = ClusterConfigPreviewVO.builder()
                .currentConfig(same)
                .proposedConfig(ClusterConfigVO.builder().writeQueueNums(8).build())
                .targetBrokers(List.of())
                .brokerProperties(Map.of())
                .changes(List.of())
                .changed(false)
                .build();

        assertThat(preview.getChanges()).isEmpty();
        assertThat(preview.isChanged()).isFalse();
    }
}
