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
 * Pins {@link TopicInput}: the tool-side topic shape. The defaulting rule is the contract - a
 * model that answers with only the topic name still produces a creatable topic (8 queues, NORMAL,
 * RW), and the round trip with a stored TopicVO preserves every field.
 */
class TopicInputTest {

    @Test
    void aFullySpecifiedInputMapsEveryField() {
        TopicInput input = new TopicInput(
                "instance-a", "orders", TopicType.FIFO, 16, 12, TopicPerm.RO, "order events");

        TopicVO vo = input.toTopicVO();

        assertThat(vo.getInstanceId()).isEqualTo("instance-a");
        assertThat(vo.getName()).isEqualTo("orders");
        assertThat(vo.getType()).isEqualTo(TopicType.FIFO);
        assertThat(vo.getWriteQueues()).isEqualTo(16);
        assertThat(vo.getReadQueues()).isEqualTo(12);
        assertThat(vo.getPerm()).isEqualTo(TopicPerm.RO);
        assertThat(vo.getRemark()).isEqualTo("order events");
    }

    @Test
    void aSparseInputDefaultsToACreatableTopic() {
        TopicInput input = new TopicInput("instance-a", "orders", null, null, null, null, null);

        TopicVO vo = input.toTopicVO();

        assertThat(vo.getWriteQueues()).isEqualTo(8);
        assertThat(vo.getReadQueues()).isEqualTo(8);
        assertThat(vo.getType()).isEqualTo(TopicType.NORMAL);
        assertThat(vo.getPerm()).isEqualTo(TopicPerm.RW);
    }

    @Test
    void writeAndReadQueuesDefaultIndependently() {
        TopicInput input = new TopicInput(
                "instance-a", "orders", null, 4, null, null, null);

        TopicVO vo = input.toTopicVO();

        assertThat(vo.getWriteQueues()).isEqualTo(4);
        assertThat(vo.getReadQueues()).isEqualTo(8);
    }

    @Test
    void fromMapsEveryStoredFieldIntoTheInput() {
        TopicVO stored = new TopicVO();
        stored.setInstanceId("instance-a");
        stored.setName("orders");
        stored.setType(TopicType.TRANSACTION);
        stored.setWriteQueues(16);
        stored.setReadQueues(12);
        stored.setPerm(TopicPerm.WO);
        stored.setRemark("tx topic");

        TopicInput input = TopicInput.from(stored);

        assertThat(input).isEqualTo(new TopicInput(
                "instance-a", "orders", TopicType.TRANSACTION, 16, 12, TopicPerm.WO, "tx topic"));
    }

    @Test
    void theRoundTripThroughTheVOPreservesTheExplicitFields() {
        TopicInput original = new TopicInput(
                "instance-a", "orders", TopicType.DELAY, 4, 6, TopicPerm.RO, "delayed");

        TopicInput roundTripped = TopicInput.from(original.toTopicVO());

        assertThat(roundTripped).isEqualTo(original);
    }
}
