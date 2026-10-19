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

import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ClusterConfigVO}, the broker-side cluster config the config page reads and the
 * update preview diffs: queue counts, message size cap, trace topic, the two auto-create
 * switches, retention, flush mode and broker permission. All construction paths must agree
 * so a diffed config is never polluted by a defaulted field.
 */
class ClusterConfigVOTest {

    private ClusterConfigVO aFullConfig() {
        return ClusterConfigVO.builder()
                .writeQueueNums(8)
                .readQueueNums(8)
                .maxMessageSize(4194304)
                .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
                .autoCreateTopicEnable(false)
                .autoCreateSubscriptionGroup(false)
                .deleteWhen("04")
                .fileReservedTime(72)
                .flushDiskType(FlushDiskType.SYNC_FLUSH)
                .brokerPermission(6)
                .build();
    }

    @Test
    void aConfigCarriesEveryBrokerSideField() {
        ClusterConfigVO config = aFullConfig();

        assertThat(config.getWriteQueueNums()).isEqualTo(8);
        assertThat(config.getReadQueueNums()).isEqualTo(8);
        assertThat(config.getMaxMessageSize()).isEqualTo(4194304);
        assertThat(config.getMsgTraceTopicName()).isEqualTo("RMQ_SYS_TRACE_TOPIC");
        assertThat(config.isAutoCreateTopicEnable()).isFalse();
        assertThat(config.isAutoCreateSubscriptionGroup()).isFalse();
        assertThat(config.getDeleteWhen()).isEqualTo("04");
        assertThat(config.getFileReservedTime()).isEqualTo(72);
        assertThat(config.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(config.getBrokerPermission()).isEqualTo(6);
    }

    @Test
    void allConstructionPathsAgree() {
        ClusterConfigVO built = aFullConfig();
        ClusterConfigVO allArgs = new ClusterConfigVO(8, 8, 4194304, "RMQ_SYS_TRACE_TOPIC",
                false, false, "04", 72, FlushDiskType.SYNC_FLUSH, 6);
        ClusterConfigVO defaulted = new ClusterConfigVO();
        defaulted.setWriteQueueNums(8);
        defaulted.setReadQueueNums(8);
        defaulted.setMaxMessageSize(4194304);
        defaulted.setMsgTraceTopicName("RMQ_SYS_TRACE_TOPIC");
        defaulted.setAutoCreateTopicEnable(false);
        defaulted.setAutoCreateSubscriptionGroup(false);
        defaulted.setDeleteWhen("04");
        defaulted.setFileReservedTime(72);
        defaulted.setFlushDiskType(FlushDiskType.SYNC_FLUSH);
        defaulted.setBrokerPermission(6);

        assertThat(built).isEqualTo(allArgs).isEqualTo(defaulted);

        ClusterConfigVO empty = new ClusterConfigVO();
        assertThat(empty.getWriteQueueNums()).isZero();
        assertThat(empty.getFlushDiskType()).isNull();
        assertThat(empty.isAutoCreateTopicEnable()).isFalse();
    }
}
