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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageQueryDlqOutput}: the DLQ tool contract. The two factory shapes — groups
 * (no group name in the envelope) and messages (scoped to one group) — must stay distinct,
 * and the group item's null-safety must not crash on a group without a last-enqueue timestamp.
 */
class MessageQueryDlqOutputTest {

    @Test
    void theGroupListingHasNoGroupInTheEnvelope() {
        MessageQueryDlqOutput output = MessageQueryDlqOutput.ofGroups(
                "instance-a", 1, 20, 5L, List.of());

        assertThat(output.instanceId()).isEqualTo("instance-a");
        assertThat(output.group()).isNull();
        assertThat(output.page()).isEqualTo(1);
        assertThat(output.pageSize()).isEqualTo(20);
        assertThat(output.total()).isEqualTo(5L);
        assertThat(output.items()).isEmpty();
    }

    @Test
    void theMessageListingCarriesItsGroup() {
        MessageQueryDlqOutput output = MessageQueryDlqOutput.ofMessages(
                "instance-a", "orders-consumer", 2, 50, 100L, List.of());

        assertThat(output.group()).isEqualTo("orders-consumer");
        assertThat(output.page()).isEqualTo(2);
        assertThat(output.total()).isEqualTo(100L);
    }

    @Test
    void aGroupWithoutALastEnqueueTimeListsWithNull() {
        DLQGroupVO vo = DLQGroupVO.builder()
                .groupName("orders-consumer").dlqTopic("%DLQ%orders-consumer")
                .messageCount(42).retryCount(3).status("ACTIVE")
                .statsAvailable(true).lastEnqueueTime(null)
                .build();

        MessageQueryDlqOutput.DlqGroupItem item = MessageQueryDlqOutput.DlqGroupItem.from(vo);

        assertThat(item.groupName()).isEqualTo("orders-consumer");
        assertThat(item.messageCount()).isEqualTo(42);
        assertThat(item.lastEnqueueTime()).isNull();
        assertThat(item.statsAvailable()).isTrue();
    }
}
