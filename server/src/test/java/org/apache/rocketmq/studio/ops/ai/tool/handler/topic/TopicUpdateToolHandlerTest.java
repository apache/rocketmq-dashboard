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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicUpdateToolHandlerTest {

    private MetadataService metadataService;
    private TopicUpdateToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new TopicUpdateToolHandler(metadataService);
    }

    private static TopicVO existingTopic() {
        TopicVO vo = new TopicVO();
        vo.setName("orders");
        vo.setType(TopicType.NORMAL);
        vo.setWriteQueues(8);
        vo.setReadQueues(8);
        vo.setPerm(TopicPerm.RW);
        vo.setRemark("current");
        return vo;
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext("inst-1", null, null, null);
    }

    @Test
    void anAbsentTopicIsCreatedWithCreationDefaults() {
        when(metadataService.findTopic("inst-1", null, "orders")).thenReturn(Optional.empty());
        when(metadataService.createTopic(eq("inst-1"), any(TopicVO.class))).thenAnswer(
                invocation -> invocation.getArgument(1));

        TopicOutput output = handler.execute(
                new TopicUpdateInput(null, "orders", null, null, null, null, null), context());

        assertThat(output.name()).isEqualTo("orders");
        verify(metadataService).createTopic(eq("inst-1"), any(TopicVO.class));
        verify(metadataService, never()).updateTopic(any(), any());
    }

    @Test
    void anExistingTopicIsUpdatedWithASparseMerge() {
        TopicVO existing = existingTopic();
        when(metadataService.findTopic("inst-1", null, "orders")).thenReturn(Optional.of(existing));
        when(metadataService.updateTopic(eq("inst-1"), any(TopicVO.class))).thenAnswer(
                invocation -> invocation.getArgument(1));

        // Only the queue count is supplied; type/perm/remark must survive the merge untouched.
        TopicOutput output = handler.execute(
                new TopicUpdateInput(null, "orders", null, 16, null, null, null), context());

        assertThat(output.name()).isEqualTo("orders");
        verify(metadataService, never()).createTopic(any(), any());
        org.mockito.ArgumentCaptor<TopicVO> captor =
                org.mockito.ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).updateTopic(eq("inst-1"), captor.capture());
        TopicVO merged = captor.getValue();
        assertThat(merged.getWriteQueues()).isEqualTo(16);
        assertThat(merged.getReadQueues()).isEqualTo(8);
        assertThat(merged.getPerm()).isEqualTo(TopicPerm.RW);
        assertThat(merged.getRemark()).isEqualTo("current");
        assertThat(merged.getType()).isEqualTo(TopicType.NORMAL);
    }

    @Test
    void thePreviewOfAnAbsentTopicHasNoBeforeState() {
        when(metadataService.findTopic("inst-1", null, "orders")).thenReturn(Optional.empty());

        ToolPlan plan = handler.preview(
                new TopicUpdateInput(null, "orders", null, 8, null, null, null), context());

        // A null before state is normalised to an empty state map by the plan, never null.
        assertThat(plan.before(TopicInput.class)).isNotNull();
        assertThat(plan.before()).isEmpty();
        assertThat(plan.after(TopicInput.class)).isNotNull();
        // Only a matching existing configuration triggers the no-change warning.
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void thePreviewOfAnIdenticalConfigurationWarns() {
        when(metadataService.findTopic("inst-1", null, "orders")).thenReturn(Optional.of(existingTopic()));

        ToolPlan plan = handler.preview(
                new TopicUpdateInput(null, "orders", null, 8, 8, TopicPerm.RW, "current"), context());

        assertThat(plan.before(TopicInput.class)).isEqualTo(TopicInput.from(existingTopic()));
        assertThat(plan.after(TopicInput.class)).isEqualTo(plan.before(TopicInput.class));
        assertThat(plan.warnings()).containsExactly(
                "The requested topic configuration already matches the current state.");
    }

    @Test
    void thePreviewMergesSuppliedValuesOntoTheCurrentState() {
        when(metadataService.findTopic("inst-1", null, "orders")).thenReturn(Optional.of(existingTopic()));

        ToolPlan plan = handler.preview(
                new TopicUpdateInput(null, "orders", null, 16, null, null, null), context());

        assertThat(plan.before(TopicInput.class).writeQueues()).isEqualTo(8);
        TopicInput after = plan.after(TopicInput.class);
        assertThat(after.writeQueues()).isEqualTo(16);
        // The merged target must keep the CURRENT type, perm and remark - not creation defaults.
        assertThat(after.perm()).isEqualTo(TopicPerm.RW);
        assertThat(after.remark()).isEqualTo("current");
        assertThat(plan.warnings()).isEmpty();
    }

    @Test
    void theHandlerAdvertisesTheUpdateToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.topic.update");
        assertThat(handler.inputType()).isEqualTo(TopicUpdateInput.class);
    }
}
