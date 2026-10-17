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
 * Pins {@link TopicUpdateInput}: the partial-update semantics of a topic mutation. The two
 * load-bearing rules: absent values keep the current configuration (an update is not a recreate),
 * and the topic TYPE is immutable - mergeWith never applies it, only the creation path honours it.
 */
class TopicUpdateInputTest {

    private static TopicVO current() {
        TopicVO vo = new TopicVO();
        vo.setInstanceId("instance-a");
        vo.setName("orders");
        vo.setType(TopicType.NORMAL);
        vo.setWriteQueues(16);
        vo.setReadQueues(12);
        vo.setPerm(TopicPerm.RO);
        vo.setRemark("current remark");
        return vo;
    }

    @Test
    void absentFieldsKeepTheCurrentConfiguration() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", null, null, null, null, null);

        TopicVO merged = input.mergeWith(current());

        assertThat(merged.getWriteQueues()).isEqualTo(16);
        assertThat(merged.getReadQueues()).isEqualTo(12);
        assertThat(merged.getPerm()).isEqualTo(TopicPerm.RO);
        assertThat(merged.getRemark()).isEqualTo("current remark");
    }

    @Test
    void suppliedFieldsOverrideTheCurrentConfiguration() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", null, 32, 24, TopicPerm.RW, "new remark");

        TopicVO merged = input.mergeWith(current());

        assertThat(merged.getWriteQueues()).isEqualTo(32);
        assertThat(merged.getReadQueues()).isEqualTo(24);
        assertThat(merged.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(merged.getRemark()).isEqualTo("new remark");
    }

    /**
     * The topic type is immutable: an update that asks for a different type must be ignored by
     * the merge - changing a NORMAL topic into a FIFO topic is not a config edit, it is a
     * different topic, and brokers reject the reconfiguration anyway.
     */
    @Test
    void theTopicTypeIsImmutableUnderMerge() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", TopicType.FIFO, null, null, null, null);

        TopicVO merged = input.mergeWith(current());

        assertThat(merged.getType()).isEqualTo(TopicType.NORMAL);
    }

    @Test
    void mergeStartsFromACopyOfTheCurrentConfiguration() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", null, null, null, null, null);
        TopicVO before = current();

        input.mergeWith(before);

        // the caller's object is untouched
        assertThat(before.getWriteQueues()).isEqualTo(16);
        assertThat(before.getRemark()).isEqualTo("current remark");
    }

    @Test
    void theCreationPathFillsTheCreationDefaults() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", null, null, null, null, null);

        TopicVO vo = input.toTopicVO();

        assertThat(vo.getWriteQueues()).isEqualTo(8);
        assertThat(vo.getReadQueues()).isEqualTo(8);
        assertThat(vo.getType()).isEqualTo(TopicType.NORMAL);
        assertThat(vo.getPerm()).isEqualTo(TopicPerm.RW);
    }

    @Test
    void theCreationPathHonoursAnExplicitType() {
        TopicUpdateInput input = new TopicUpdateInput(
                "instance-a", "orders", TopicType.TRANSACTION, 4, 6, TopicPerm.WO, "tx");

        TopicVO vo = input.toTopicVO();

        assertThat(vo.getType()).isEqualTo(TopicType.TRANSACTION);
        assertThat(vo.getWriteQueues()).isEqualTo(4);
        assertThat(vo.getReadQueues()).isEqualTo(6);
        assertThat(vo.getPerm()).isEqualTo(TopicPerm.WO);
    }
}
