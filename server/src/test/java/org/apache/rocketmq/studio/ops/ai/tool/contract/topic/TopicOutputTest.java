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
 * Pins {@link TopicOutput}: the tool-side single-topic view. The enum-to-name translations
 * (type and perm travel as their wire names) must be exact, and an unknown perm survives
 * as null rather than crashing the view.
 */
class TopicOutputTest {

    @Test
    void mapsEveryFieldWithTheEnumWireNames() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("instance-a");
        topic.setType(TopicType.FIFO);
        topic.setWriteQueues(16);
        topic.setReadQueues(12);
        topic.setPerm(TopicPerm.RO);
        topic.setRemark("order events");

        TopicOutput output = TopicOutput.from(topic);

        assertThat(output.name()).isEqualTo("orders");
        assertThat(output.clusterId()).isEqualTo("instance-a");
        assertThat(output.type()).isEqualTo("FIFO");
        assertThat(output.writeQueues()).isEqualTo(16);
        assertThat(output.readQueues()).isEqualTo(12);
        assertThat(output.perm()).isEqualTo("RO");
        assertThat(output.remark()).isEqualTo("order events");
    }

    @Test
    void anUnknownPermOrTypeSurvivesAsNull() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");

        TopicOutput output = TopicOutput.from(topic);

        assertThat(output.type()).isNull();
        assertThat(output.perm()).isNull();
        assertThat(output.name()).isEqualTo("orders");
    }
}
