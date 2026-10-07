/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerGroupDeleteToolHandlerTest {

    private MetadataService metadataService;
    private ConsumerGroupDeleteToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new ConsumerGroupDeleteToolHandler(metadataService);
    }

    private static ConsumerGroupVO group() {
        ConsumerGroupVO vo = new ConsumerGroupVO();
        vo.setName("orders");
        vo.setSubscriptionMode(SubscriptionMode.Push);
        vo.setConsumeType(ConsumeType.CLUSTERING);
        vo.setSubscriptionDataType("JSON");
        vo.setDeliveryOrderType("CONCURRENT");
        vo.setRetryMaxTimes(16);
        vo.setDelaySeconds(0);
        return vo;
    }

    @Test
    void executeDeletesTheNamedGroupUnderTheContextInstance() {
        Void result = handler.execute(
                new GroupInput(null, "orders", null, null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(result).isNull();
        // The two-argument overload: the delete must carry the context's instance.
        verify(metadataService).deleteConsumerGroup("inst-1", "orders");
    }

    @Test
    void thePreviewRequiresTheGroupAndShowsItAsTheBeforeDiff() {
        when(metadataService.requireConsumerGroup("inst-1", "orders")).thenReturn(group());

        ToolPlan plan = handler.preview(
                new GroupInput(null, "orders", null, null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        GroupInput before = plan.before(GroupInput.class);
        assertThat(before).isNotNull();
        assertThat(before.groupName()).isEqualTo("orders");
        assertThat(before.retryMaxTimes()).isEqualTo(16);
        assertThat(before.subscriptionDataType()).isEqualTo("JSON");
        // A delete has no after state.
        assertThat(plan.after()).isEmpty();
    }

    @Test
    void thePreviewRequiresTheGroupUnderTheContextInstance() {
        when(metadataService.requireConsumerGroup("inst-1", "orders")).thenReturn(group());

        handler.preview(
                new GroupInput(null, "orders", null, null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        // requireConsumerGroup (the 404-ing read) carries the context's instance too.
        verify(metadataService).requireConsumerGroup("inst-1", "orders");
    }

    @Test
    void theDeletePreviewCarriesTheConfigurationRemovalWarning() {
        when(metadataService.requireConsumerGroup("inst-1", "orders")).thenReturn(group());

        ToolPlan plan = handler.preview(
                new GroupInput(null, "orders", null, null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(plan.warnings()).anyMatch(
                warning -> warning.contains("subscription configuration"));
    }

    @Test
    void theHandlerAdvertisesTheDeleteToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.group.delete");
        assertThat(handler.inputType()).isEqualTo(GroupInput.class);
    }
}
