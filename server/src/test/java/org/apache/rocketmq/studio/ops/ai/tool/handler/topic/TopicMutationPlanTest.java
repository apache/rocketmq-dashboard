/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TopicMutationPlanTest {

    @Test
    void deleteCombinesFixedDescriptionWithCurrentTopicState() {
        MetadataService metadataService = mock(MetadataService.class);
        TopicVO current = currentTopic();
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(current);
        ToolExecutionContext context = org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context(
                "instance-a", Map.of("cluster", "instance-a", "topic", "orders", "dry_run", true));

        ToolPlan plan = new TopicDeleteToolHandler(metadataService).preview(
                context.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput.class), context);

        assertThat(plan.summary()).isEqualTo("delete topic 'orders' in cluster 'instance-a'.");
        assertThat(plan.before()).containsEntry("topic", "orders")
                .containsEntry("writeQueues", 16).containsEntry("perm", "RO");
        assertThat(plan.after()).isEmpty();
        assertThat(plan.impact()).containsExactly("Deletes the topic route and makes retained messages unavailable.");
        assertThat(plan.warnings()).containsExactly(
                "Deleting the topic makes its retained messages unavailable to producers and consumers.");
        verify(metadataService, never()).deleteTopic(any(), any());
    }

    @Test
    void updatePlansOnlyTopicConfigurationFields() {
        MetadataService metadataService = mock(MetadataService.class);
        TopicVO current = new TopicVO();
        current.setName("orders");
        current.setNamespace("sales");
        current.setClusterId("cluster-a");
        current.setInstanceId("instance-a");
        current.setType(TopicType.NORMAL);
        current.setWriteQueues(4);
        current.setReadQueues(4);
        current.setPerm(TopicPerm.RW);
        current.setMessageCount(100L);
        current.setTps(12.5);
        current.setConsumerGroupCount(3);
        when(metadataService.getTopic("instance-a", null, "orders"))
                .thenReturn(current);

        var updateInput = Map.<String, Object>of(
                "cluster", "instance-a",
                "topic", "orders",
                "namespace", "sales",
                "type", "NORMAL",
                "writeQueues", 8,
                "readQueues", 8,
                "perm", "RW",
                "dry_run", true);
        ToolExecutionContext ctx = context(updateInput);
        ToolPlan plan = new TopicUpdateToolHandler(metadataService).preview(
                ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.before())
                .containsEntry("cluster", "instance-a")
                .containsEntry("topic", "orders")
                .containsEntry("writeQueues", 4)
                .doesNotContainKeys("clusterId", "instanceId",
                        "messageCount", "tps", "consumerGroupCount");
        assertThat(plan.after())
                .containsEntry("cluster", "instance-a")
                .containsEntry("topic", "orders")
                .containsEntry("writeQueues", 8)
                .doesNotContainKeys("clusterId", "instanceId");
    }

    @Test
    void remarkOnlyPreviewAndExecutionPreserveExistingConfiguration() {
        MetadataService metadataService = mock(MetadataService.class);
        TopicVO current = currentTopic();
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(current);
        when(metadataService.updateTopic(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadataService);
        Map<String, Object> raw = Map.of("cluster", "instance-a", "topic", "orders", "remark", "new remark");
        ToolExecutionContext ctx = context(raw);

        ToolPlan plan = handler.preview(ctx.convertInput(TopicUpdateInput.class), ctx);
        verify(metadataService, never()).updateTopic(any(), any());
        handler.execute(ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.after()).containsEntry("type", "NORMAL")
                .containsEntry("writeQueues", 16)
                .containsEntry("readQueues", 16)
                .containsEntry("perm", "RO")
                .containsEntry("namespace", "sales")
                .containsEntry("remark", "new remark");
        assertThat(plan.before()).containsEntry("remark", "old remark");
        ArgumentCaptor<TopicVO> sent = ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).updateTopic(any(), sent.capture());
        assertThat(TopicInput.from(sent.getValue())).isEqualTo(plan.after(TopicInput.class));
        assertThat(sent.getValue().getInstanceId()).isEqualTo("instance-a");
        assertThat(current.getRemark()).isEqualTo("old remark");
    }

    private static TopicVO currentTopic() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-a");
        topic.setInstanceId("instance-a");
        topic.setNamespace("sales");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(16);
        topic.setReadQueues(16);
        topic.setPerm(TopicPerm.RO);
        topic.setRemark("old remark");
        return topic;
    }

    private static ToolExecutionContext context(Map<String, Object> input) {
        ToolDefinition definition = new ToolDefinition(
                "rmq.topic.update",
                new ToolDefinition.Cli("topic", "update"),
                "Update topic.",
                ToolRiskLevel.L2,
                "topic:write",
                List.of(),
                Map.of("type", "object"),
                Map.of("type", "object"),
                "object",
                false,
                null);
        return ToolExecutionContext.of("instance-a", definition, input);
    }
}
