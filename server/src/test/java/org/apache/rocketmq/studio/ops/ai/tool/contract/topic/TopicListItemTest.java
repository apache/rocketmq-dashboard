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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TopicListItem}: one row of the topic listing. The statistics block is the part a
 * triage conversation leans on - messageCount, tps and consumerGroupCount must arrive as
 * measured, never as defaults.
 */
class TopicListItemTest {

    @Test
    void mapsEveryListingField() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("instance-a");
        topic.setType(TopicType.FIFO);
        topic.setWriteQueues(16);
        topic.setReadQueues(12);
        topic.setPerm(TopicPerm.RO);
        topic.setMessageCount(9_000_000);
        topic.setTps(1234.5);
        topic.setConsumerGroupCount(3);

        TopicListItem item = TopicListItem.from(topic);

        assertThat(item.name()).isEqualTo("orders");
        assertThat(item.clusterId()).isEqualTo("instance-a");
        assertThat(item.type()).isEqualTo(TopicType.FIFO);
        assertThat(item.writeQueues()).isEqualTo(16);
        assertThat(item.readQueues()).isEqualTo(12);
        assertThat(item.perm()).isEqualTo(TopicPerm.RO);
        assertThat(item.messageCount()).isEqualTo(9_000_000);
        assertThat(item.tps()).isEqualTo(1234.5);
        assertThat(item.consumerGroupCount()).isEqualTo(3);
    }

    @Test
    void aTopicWithoutStatisticsStillLists() {
        // a freshly created topic has no collected metrics yet: the row must
        // project zeroed statistics, not vanish from the listing
        TopicVO topic = new TopicVO();
        topic.setName("fresh");

        TopicListItem item = TopicListItem.from(topic);

        assertThat(item.name()).isEqualTo("fresh");
        assertThat(item.messageCount()).isZero();
        assertThat(item.tps()).isZero();
        assertThat(item.consumerGroupCount()).isZero();
        assertThat(item.writeQueues()).isZero();
    }

    @Test
    void thePermTravelsAsTheEnumNotAString() {
        // the listing keeps the typed perm: consumers branch on the enum
        TopicVO topic = new TopicVO();
        topic.setName("t");
        topic.setPerm(TopicPerm.WO);

        assertThat(TopicListItem.from(topic).perm()).isEqualTo(TopicPerm.WO);
    }
}
