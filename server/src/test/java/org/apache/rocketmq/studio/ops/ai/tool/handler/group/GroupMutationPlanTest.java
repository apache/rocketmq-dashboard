/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupMutationPlanTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 20})
    void updatePlansAndAppliesOnlyConsumerGroupConfigurationFields(int retryMaxTimes) {
        MetadataService metadataService = mock(MetadataService.class);
        ConsumerGroupVO current = new ConsumerGroupVO();
        current.setName("orders-group");
        current.setNamespace("sales");
        current.setClusterId("cluster-a");
        current.setInstanceId("cluster-a");
        current.setSubscriptionMode(SubscriptionMode.Push);
        current.setConsumeType(ConsumeType.CLUSTERING);
        current.setRetryMaxTimes(16);
        current.setDelaySeconds(3);
        current.setOnlineInstances(4);
        current.setTotalLag(100L);
        current.setSubscribedTopics(List.of("orders"));
        when(metadataService.requireConsumerGroup(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("orders-group")))
                .thenReturn(current);
        when(metadataService.updateConsumerGroup(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var updateInput = Map.<String, Object>ofEntries(
                Map.entry("cluster", "cluster-a"),
                Map.entry("group", "orders-group"),
                Map.entry("retryMaxTimes", retryMaxTimes),
                Map.entry("dry_run", true));
        ToolExecutionContext ctx = context("rmq.group.update", updateInput);
        ConsumerGroupUpdateToolHandler handler = new ConsumerGroupUpdateToolHandler(metadataService);
        var input = ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput.class);
        ToolPlan plan = handler.preview(input, ctx);

        assertThat(plan.before())
                .containsEntry("cluster", "cluster-a")
                .containsEntry("group", "orders-group")
                .containsEntry("retryMaxTimes", 16)
                .doesNotContainKeys("onlineInstances",
                        "totalLag", "subscribedTopics", "instances");
        assertThat(plan.after())
                .containsEntry("cluster", "cluster-a")
                .containsEntry("retryMaxTimes", retryMaxTimes)
                .doesNotContainKeys("onlineInstances", "totalLag");

        var result = handler.execute(input, ctx);

        ArgumentCaptor<ConsumerGroupVO> applied = ArgumentCaptor.forClass(ConsumerGroupVO.class);
        verify(metadataService).updateConsumerGroup(applied.capture());
        assertThat(applied.getValue().getRetryMaxTimes()).isEqualTo(plan.after().get("retryMaxTimes"));
        assertThat(applied.getValue()).usingRecursiveComparison().ignoringFields("retryMaxTimes").isEqualTo(current);
        assertThat(result.retryMaxTimes()).isEqualTo(retryMaxTimes);
        assertThat(current.getRetryMaxTimes()).isEqualTo(16);
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

        var resetInput = Map.<String, Object>of("cluster", "cluster-a", "group", "orders-group",
                "topic", "orders", "timestamp", 1234L, "dry_run", true);
        ToolExecutionContext ctx = context("rmq.group.reset_offset", resetInput);
        ToolPlan plan = new GroupResetOffsetToolHandler(metadataService).preview(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput.class), ctx);

        assertThat(plan.summary()).isEqualTo("reset offsets for consumer group 'orders-group' in cluster 'cluster-a'.");
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
    void skipAccumulatedPlansLatestOffsets() {
        MetadataService metadataService = mock(MetadataService.class);
        when(metadataService.resolveSkipAccumulatedTopics("cluster-a", "orders-group", null))
                .thenReturn(List.of("orders", "payments"));
        var input = Map.<String, Object>of("cluster", "cluster-a", "group", "orders-group", "dry_run", true);
        ToolExecutionContext ctx = context("rmq.group.skip_accumulated", input);
        ToolPlan plan = new GroupSkipAccumulatedToolHandler(metadataService).preview(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupTopicInput.class), ctx);

        assertThat(plan.before()).containsEntry("position", "CURRENT_OFFSETS")
                .containsEntry("topics", List.of("orders", "payments"));
        assertThat(plan.after()).containsEntry("position", "LATEST_OFFSETS")
                .containsEntry("topics", List.of("orders", "payments"))
                .doesNotContainKeys("timestamp", "retryMaxTimes", "subscriptionMode");
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
