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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the topic view of {@link TopicVO}: the identity, queue counts, permission and the
 * computed traffic counters (message count, TPS, consumer group count) round-trip through the
 * builder, and a fresh topic reads as zeroed counters rather than nulls.
 */
class TopicVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setNamespace("ns-1");
        topic.setClusterId("cluster-1");
        topic.setInstanceId("instance-1");
        topic.setType(TopicType.FIFO);
        topic.setWriteQueues(8);
        topic.setReadQueues(16);
        topic.setPerm(TopicPerm.RW);
        topic.setMessageCount(1_000_000L);
        topic.setTps(42.5);
        topic.setConsumerGroupCount(3);
        topic.setRemark("order events");

        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getNamespace()).isEqualTo("ns-1");
        assertThat(topic.getClusterId()).isEqualTo("cluster-1");
        assertThat(topic.getInstanceId()).isEqualTo("instance-1");
        assertThat(topic.getType()).isEqualTo(TopicType.FIFO);
        assertThat(topic.getWriteQueues()).isEqualTo(8);
        assertThat(topic.getReadQueues()).isEqualTo(16);
        assertThat(topic.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(topic.getMessageCount()).isEqualTo(1_000_000L);
        assertThat(topic.getTps()).isEqualTo(42.5);
        assertThat(topic.getConsumerGroupCount()).isEqualTo(3);
        assertThat(topic.getRemark()).isEqualTo("order events");
    }

    @Test
    void aFreshTopicReadsAsZeroedCounters() {
        TopicVO topic = new TopicVO();
        assertThat(topic.getWriteQueues()).isZero();
        assertThat(topic.getMessageCount()).isZero();
        assertThat(topic.getTps()).isZero();
        assertThat(topic.getConsumerGroupCount()).isZero();
    }
}
