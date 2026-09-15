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
import java.util.Optional;

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
                "instance-a", Map.of("instanceId", "instance-a", "topicName", "orders", "dry_run", true));

        ToolPlan plan = new TopicDeleteToolHandler(metadataService).preview(
                context.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput.class), context);

        assertThat(plan.summary()).isEqualTo("delete topic 'orders' in instance 'instance-a'.");
        assertThat(plan.before()).containsEntry("topicName", "orders")
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
        current.setClusterId("cluster-a");
        current.setInstanceId("instance-a");
        current.setType(TopicType.NORMAL);
        current.setWriteQueues(4);
        current.setReadQueues(4);
        current.setPerm(TopicPerm.RW);
        current.setMessageCount(100L);
        current.setTps(12.5);
        current.setConsumerGroupCount(3);
        when(metadataService.findTopic("instance-a", null, "orders"))
                .thenReturn(Optional.of(current));

        var updateInput = Map.<String, Object>of(
                "instanceId", "instance-a",
                "topicName", "orders",
                "type", "NORMAL",
                "writeQueues", 8,
                "readQueues", 8,
                "perm", "RW",
                "dry_run", true);
        ToolExecutionContext ctx = context(updateInput);
        ToolPlan plan = new TopicUpdateToolHandler(metadataService).preview(
                ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.before())
                .containsEntry("instanceId", "instance-a")
                .containsEntry("topicName", "orders")
                .containsEntry("writeQueues", 4)
                .doesNotContainKeys("cluster", "clusterId", "namespace",
                        "messageCount", "tps", "consumerGroupCount");
        assertThat(plan.after())
                .containsEntry("instanceId", "instance-a")
                .containsEntry("topicName", "orders")
                .containsEntry("writeQueues", 8)
                .doesNotContainKeys("cluster", "clusterId", "namespace");
    }

    @Test
    void remarkOnlyPreviewAndExecutionPreserveExistingConfiguration() {
        MetadataService metadataService = mock(MetadataService.class);
        TopicVO current = currentTopic();
        when(metadataService.findTopic("instance-a", null, "orders")).thenReturn(Optional.of(current));
        when(metadataService.updateTopic(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadataService);
        Map<String, Object> raw = Map.of("instanceId", "instance-a", "topicName", "orders", "remark", "new remark");
        ToolExecutionContext ctx = context(raw);

        ToolPlan plan = handler.preview(ctx.convertInput(TopicUpdateInput.class), ctx);
        verify(metadataService, never()).updateTopic(any(), any());
        handler.execute(ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.after()).containsEntry("type", "NORMAL")
                .containsEntry("writeQueues", 16)
                .containsEntry("readQueues", 16)
                .containsEntry("perm", "RO")
                .containsEntry("remark", "new remark")
                .doesNotContainKey("namespace");
        assertThat(plan.before()).containsEntry("remark", "old remark");
        ArgumentCaptor<TopicVO> sent = ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).updateTopic(any(), sent.capture());
        assertThat(TopicInput.from(sent.getValue())).isEqualTo(plan.after(TopicInput.class));
        assertThat(sent.getValue().getInstanceId()).isEqualTo("instance-a");
        assertThat(current.getRemark()).isEqualTo("old remark");
    }

    @Test
    void updateCreatesTopicWhenAbsent() {
        MetadataService metadataService = mock(MetadataService.class);
        when(metadataService.findTopic("instance-a", null, "orders")).thenReturn(Optional.empty());
        when(metadataService.createTopic(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadataService);
        Map<String, Object> raw = Map.of("instanceId", "instance-a", "topicName", "orders", "writeQueues", 4);
        ToolExecutionContext ctx = context(raw);

        ToolPlan plan = handler.preview(ctx.convertInput(TopicUpdateInput.class), ctx);
        handler.execute(ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.before()).isEmpty();
        assertThat(plan.after())
                .containsEntry("instanceId", "instance-a")
                .containsEntry("topicName", "orders")
                .containsEntry("writeQueues", 4)
                .containsEntry("readQueues", 8)
                .containsEntry("type", "NORMAL")
                .containsEntry("perm", "RW");
        ArgumentCaptor<TopicVO> sent = ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).createTopic(any(), sent.capture());
        verify(metadataService, never()).updateTopic(any(), any());
        assertThat(sent.getValue().getName()).isEqualTo("orders");
        assertThat(sent.getValue().getWriteQueues()).isEqualTo(4);
        assertThat(sent.getValue().getReadQueues()).isEqualTo(8);
    }

    @Test
    void updatePlanIgnoresRequestedTypeForExistingTopicTest() {
        // Decision 9: mergeWith never re-applies type; an existing topic keeps its registered type.
        MetadataService metadataService = mock(MetadataService.class);
        TopicVO current = currentTopic();
        when(metadataService.findTopic("instance-a", null, "orders")).thenReturn(Optional.of(current));
        TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadataService);
        Map<String, Object> raw = Map.of("instanceId", "instance-a", "topicName", "orders", "type", "FIFO");
        ToolExecutionContext ctx = context(raw);

        ToolPlan plan = handler.preview(ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.after()).containsEntry("type", "NORMAL");
        assertThat(current.getType()).isEqualTo(TopicType.NORMAL);
    }

    @Test
    void updateCreatesTopicWithRequestedTypeTest() {
        // Decision 9: the type parameter stays effective on the upsert creation branch.
        MetadataService metadataService = mock(MetadataService.class);
        when(metadataService.findTopic("instance-a", null, "orders")).thenReturn(Optional.empty());
        when(metadataService.createTopic(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadataService);
        Map<String, Object> raw = Map.of("instanceId", "instance-a", "topicName", "orders", "type", "FIFO");
        ToolExecutionContext ctx = context(raw);

        ToolPlan plan = handler.preview(ctx.convertInput(TopicUpdateInput.class), ctx);
        handler.execute(ctx.convertInput(TopicUpdateInput.class), ctx);

        assertThat(plan.after()).containsEntry("type", "FIFO");
        ArgumentCaptor<TopicVO> sent = ArgumentCaptor.forClass(TopicVO.class);
        verify(metadataService).createTopic(any(), sent.capture());
        assertThat(sent.getValue().getType()).isEqualTo(TopicType.FIFO);
        verify(metadataService, never()).updateTopic(any(), any());
    }

    private static TopicVO currentTopic() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-a");
        topic.setInstanceId("instance-a");
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
