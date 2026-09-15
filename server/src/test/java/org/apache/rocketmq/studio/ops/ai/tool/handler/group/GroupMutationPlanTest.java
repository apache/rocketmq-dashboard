/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GroupMutationPlanTest {

    @Test
    void updatePlansOnlyConsumerGroupConfigurationFields() {
        MetadataService metadataService = mock(MetadataService.class);
        ConsumerGroupVO current = new ConsumerGroupVO();
        current.setName("orders-group");
        current.setClusterId("cluster-a");
        current.setInstanceId("cluster-a");
        current.setSubscriptionMode(SubscriptionMode.Push);
        current.setConsumeType(ConsumeType.CLUSTERING);
        current.setRetryMaxTimes(16);
        current.setDelaySeconds(3);
        current.setOnlineInstances(4);
        current.setTotalLag(100L);
        current.setSubscribedTopics(List.of("orders"));
        when(metadataService.findConsumerGroup(any(), eq("orders-group")))
                .thenReturn(Optional.of(current));

        var updateInput = Map.<String, Object>ofEntries(
                Map.entry("instanceId", "cluster-a"),
                Map.entry("groupName", "orders-group"),
                Map.entry("subscriptionMode", "Push"),
                Map.entry("consumeType", "CLUSTERING"),
                Map.entry("retryMaxTimes", 20),
                Map.entry("delaySeconds", 3),
                Map.entry("dry_run", true));
        ToolExecutionContext ctx = context("rmq.group.update", updateInput);
        ToolPlan plan = new ConsumerGroupUpdateToolHandler(metadataService).preview(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput.class), ctx);

        assertThat(plan.summary()).isEqualTo("update consumer group 'orders-group' in instance 'cluster-a'.");
        assertThat(plan.before())
                .containsEntry("instanceId", "cluster-a")
                .containsEntry("groupName", "orders-group")
                .containsEntry("retryMaxTimes", 16)
                .doesNotContainKeys("namespace", "onlineInstances",
                        "totalLag", "subscribedTopics", "instances");
        assertThat(plan.after())
                .containsEntry("instanceId", "cluster-a")
                .containsEntry("retryMaxTimes", 20)
                .doesNotContainKeys("namespace", "onlineInstances", "totalLag");
    }

    @Test
    void resetOffsetPlansQueuePositionsInsteadOfGroupConfiguration() {
        MetadataService metadataService = mock(MetadataService.class);
        ResetConsumerOffsetQueuePreviewVO queue = ResetConsumerOffsetQueuePreviewVO.builder()
                .topic("orders")
                .broker("broker-a")
                .queueId(1)
                .consumerOffset(120)
                .targetOffset(80)
                .currentLag(30)
                .projectedLag(70)
                .build();
        when(metadataService.previewResetOffset("cluster-a", "orders-group", 1234L, "orders"))
                .thenReturn(ResetConsumerOffsetPreviewVO.builder()
                        .groupName("orders-group")
                        .topic("orders")
                        .timestamp(1234L)
                        .complete(true)
                        .allowReset(true)
                        .queueCount(1)
                        .currentTotalLag(30)
                        .projectedTotalLag(70)
                        .queues(List.of(queue))
                        .warnings(List.of())
                        .build());

        var resetInput = Map.<String, Object>of("instanceId", "cluster-a", "groupName", "orders-group",
                "topicName", "orders", "timestamp", 1234L, "dry_run", true);
        ToolExecutionContext ctx = context("rmq.group.reset_offset", resetInput);
        ToolPlan plan = new GroupResetOffsetToolHandler(metadataService).preview(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput.class), ctx);

        assertThat(plan.summary()).isEqualTo("reset offsets for consumer group 'orders-group' in instance 'cluster-a'.");
        assertThat(plan.impact()).containsExactly("Moves offsets for 1 queues; projected lag changes from 30 to 70.");
        assertThat(plan.before()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "group", "orders-group", "topic", "orders", "totalLag", 30L,
                "queues", List.of(Map.of("topic", "orders", "broker", "broker-a", "queueId", 1,
                        "offset", 120L, "lag", 30L))));
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "group", "orders-group", "topic", "orders", "timestamp", 1234L, "totalLag", 70L,
                "queues", List.of(Map.of("topic", "orders", "broker", "broker-a", "queueId", 1,
                        "offset", 80L, "lag", 70L))));
    }

    @Test
    void updateCreatesConsumerGroupWhenAbsent() {
        MetadataService metadataService = mock(MetadataService.class);
        when(metadataService.findConsumerGroup(any(), eq("new-group")))
                .thenReturn(Optional.empty());
        ConsumerGroupVO created = new ConsumerGroupVO();
        created.setName("new-group");
        created.setInstanceId("cluster-a");
        when(metadataService.createConsumerGroup(any())).thenReturn(created);

        var input = Map.<String, Object>of("instanceId", "cluster-a", "groupName", "new-group",
                "retryMaxTimes", 8);
        ToolExecutionContext ctx = context("rmq.group.update", input);
        new ConsumerGroupUpdateToolHandler(metadataService).execute(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput.class), ctx);

        // When absent, upsert takes the create path and never calls update.
        verify(metadataService).createConsumerGroup(any());
        verify(metadataService, never()).updateConsumerGroup(any());
    }

    @Test
    void resetOffsetWithoutTimestampIsRejectedTest() {
        // Decision 13: timestamp is required; the current-time default lives in the rmqctl client.
        MetadataService metadataService = mock(MetadataService.class);
        var resetInput = Map.<String, Object>of("instanceId", "cluster-a", "groupName", "orders-group",
                "topicName", "orders", "dry_run", true);
        ToolExecutionContext ctx = context("rmq.group.reset_offset", resetInput);
        GroupResetOffsetToolHandler handler = new GroupResetOffsetToolHandler(metadataService);
        var input = ctx.convertInput(
                org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput.class);

        assertThatThrownBy(() -> handler.preview(input, ctx))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timestamp is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> handler.execute(input, ctx))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timestamp is required");

        verifyNoInteractions(metadataService);
    }

    private static ToolExecutionContext context(String name, Map<String, Object> input) {
        ToolDefinition definition = new ToolDefinition(
                name,
                new ToolDefinition.Cli("group", "update"),
                "Mutate group.",
                ToolRiskLevel.L2,
                "group:write",
                List.of(),
                Map.of("type", "object"),
                Map.of("type", "object"),
                "object",
                false,
                null);
        return ToolExecutionContext.of("cluster-a", definition, input);
    }
}
