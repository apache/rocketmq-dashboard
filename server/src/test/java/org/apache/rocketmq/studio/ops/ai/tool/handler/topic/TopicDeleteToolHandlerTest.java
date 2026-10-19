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
 * limits under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicDeleteToolHandlerTest {

    private MetadataService metadataService;
    private TopicDeleteToolHandler handler;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        handler = new TopicDeleteToolHandler(metadataService);
    }

    private static TopicVO topic() {
        TopicVO vo = new TopicVO();
        vo.setName("orders");
        vo.setType(TopicType.NORMAL);
        vo.setWriteQueues(8);
        vo.setReadQueues(8);
        vo.setPerm(TopicPerm.RW);
        vo.setRemark("to be removed");
        return vo;
    }

    @Test
    void executeDeletesTheNamedTopicUnderTheContextInstance() {
        TopicInput input = new TopicInput(null, "orders", null, null, null, null, null);

        Void result = handler.execute(input, new ToolExecutionContext("inst-1", null, null, null));

        assertThat(result).isNull();
        verify(metadataService).deleteTopic("inst-1", "orders");
    }

    @Test
    void thePreviewShowsTheCurrentStateAsTheBeforeDiff() {
        when(metadataService.getTopic("inst-1", null, "orders")).thenReturn(topic());

        ToolPlan plan = handler.preview(
                new TopicInput(null, "orders", null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        TopicInput before = plan.before(TopicInput.class);
        assertThat(before).isNotNull();
        assertThat(before.topicName()).isEqualTo("orders");
        assertThat(before.remark()).isEqualTo("to be removed");
        // A delete never has an after state.
        assertThat(plan.after()).isEmpty();
    }

    @Test
    void theDeletePreviewCarriesTheUnavailabilityWarning() {
        when(metadataService.getTopic("inst-1", null, "orders")).thenReturn(topic());

        ToolPlan plan = handler.preview(
                new TopicInput(null, "orders", null, null, null, null, null),
                new ToolExecutionContext("inst-1", null, null, null));

        assertThat(plan.warnings()).anyMatch(
                warning -> warning.contains("retained messages unavailable"));
    }

    @Test
    void theHandlerAdvertisesTheDeleteToolNameAndInputType() {
        assertThat(handler.name()).isEqualTo("rmq.topic.delete");
        assertThat(handler.inputType()).isEqualTo(TopicInput.class);
    }
}
