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
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerGroupUpdateToolHandlerTest {

    private MetadataService metadataService;
    private ConsumerGroupUpdateToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new ConsumerGroupUpdateToolHandler(metadataService);
    }

    private static ConsumerGroupVO existingGroup() {
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

    private static ToolExecutionContext context() {
        return new ToolExecutionContext("inst-1", null, null, null);
    }

    @Test
    void anAbsentGroupIsCreatedWithTheInputConfiguration() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.empty());
        when(metadataService.createConsumerGroup(any(ConsumerGroupVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GroupListItem output = handler.execute(
                new GroupInput(null, "orders", null, null, null, null, 5, null), context());

        assertThat(output.name()).isEqualTo("orders");
        assertThat(output.retryMaxTimes()).isEqualTo(5);
        verify(metadataService).createConsumerGroup(any(ConsumerGroupVO.class));
        verify(metadataService, never()).updateConsumerGroup(any(ConsumerGroupVO.class));
    }

    @Test
    void anExistingGroupIsUpdatedWithASparseMerge() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.of(existingGroup()));
        when(metadataService.updateConsumerGroup(any(ConsumerGroupVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Only the retry count is supplied; every other field must survive the merge.
        GroupListItem output = handler.execute(
                new GroupInput(null, "orders", null, null, null, null, 32, null), context());

        assertThat(output.retryMaxTimes()).isEqualTo(32);
        org.mockito.ArgumentCaptor<ConsumerGroupVO> captor =
                org.mockito.ArgumentCaptor.forClass(ConsumerGroupVO.class);
        verify(metadataService).updateConsumerGroup(captor.capture());
        verify(metadataService, never()).createConsumerGroup(any(ConsumerGroupVO.class));
        ConsumerGroupVO merged = captor.getValue();
        assertThat(merged.getSubscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(merged.getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(merged.getSubscriptionDataType()).isEqualTo("JSON");
        assertThat(merged.getDeliveryOrderType()).isEqualTo("CONCURRENT");
        assertThat(merged.getName()).isEqualTo("orders");
    }

    @Test
    void thePersistedGroupCarriesTheContextInstanceEvenOnTheCreatePath() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.empty());
        when(metadataService.createConsumerGroup(any(ConsumerGroupVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // The input carries a null instanceId; the context's instance must be set on the row.
        handler.execute(
                new GroupInput(null, "orders", null, null, null, null, 5, null), context());

        org.mockito.ArgumentCaptor<ConsumerGroupVO> captor =
                org.mockito.ArgumentCaptor.forClass(ConsumerGroupVO.class);
        verify(metadataService).createConsumerGroup(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo("inst-1");
    }

    @Test
    void thePreviewOfAnAbsentGroupHasNoBeforeState() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.empty());

        ToolPlan plan = handler.preview(
                new GroupInput(null, "orders", null, null, null, null, 5, null), context());

        assertThat(plan.before()).isEmpty();
        assertThat(plan.after(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput.class)).isNotNull();
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void thePreviewOfAnIdenticalConfigurationWarns() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.of(existingGroup()));

        ToolPlan plan = handler.preview(
                new GroupInput(null, "orders", SubscriptionMode.Push, ConsumeType.CLUSTERING,
                        "JSON", "CONCURRENT", 16, 0), context());

        assertThat(plan.warnings()).containsExactly(
                "The requested consumer group configuration already matches the current state.");
    }

    @Test
    void thePreviewMergesTheSuppliedValuesOntoTheCurrentState() {
        when(metadataService.findConsumerGroup("inst-1", "orders")).thenReturn(Optional.of(existingGroup()));

        ToolPlan plan = handler.preview(
                new GroupInput(null, "orders", null, null, null, null, 32, null), context());

        GroupInput before = plan.before(GroupInput.class);
        GroupInput after = plan.after(GroupInput.class);
        assertThat(before.retryMaxTimes()).isEqualTo(16);
        assertThat(after.retryMaxTimes()).isEqualTo(32);
        // The untouched fields keep the CURRENT values in the after state, not the defaults.
        assertThat(after.subscriptionDataType()).isEqualTo("JSON");
        assertThat(after.deliveryOrderType()).isEqualTo("CONCURRENT");
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheUpdateToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.group.update");
        assertThat(handler.inputType()).isEqualTo(GroupInput.class);
    }
}
