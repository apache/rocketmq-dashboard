/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.acl;

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AclMutationPlanTest {

    @Test
    void updatePlansOnlyAclRuleConfigurationFields() {
        AclService aclService = mock(AclService.class);
        AclRuleVO current = AclRuleVO.builder()
                .id(7L)
                .principal("user-a")
                .resource("orders")
                .resourceType("TOPIC")
                .actions(List.of("SUB"))
                .decision("GRANT")
                .gmtCreate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();
        when(aclService.getRule("7", "cluster-a")).thenReturn(current);

        var updateInput2 = Map.<String, Object>of(
                "cluster", "cluster-a",
                "id", "7",
                "principal", "user-a",
                "resource", "orders",
                "resourceType", "TOPIC",
                "actions", List.of("PUB", "SUB"),
                "decision", "GRANT",
                "dry_run", true);
        ToolExecutionContext ctx = context(updateInput2);
        ToolPlan plan = new AclUpdateToolHandler(aclService).preview(
                ctx.convertInput(org.apache.rocketmq.studio.ops.ai.tool.contract.acl.AclMutationInput.class), ctx);

        assertThat(plan.before())
                .containsEntry("id", "7")
                .containsEntry("actions", List.of("SUB"))
                .doesNotContainKeys("cluster", "gmtCreate");
        assertThat(plan.after())
                .containsEntry("id", "7")
                .containsEntry("actions", List.of("PUB", "SUB"))
                .doesNotContainKeys("cluster", "gmtCreate");
        assertThat(plan.warnings()).containsExactly(
                "The updated ACL rule changes permissions for subsequent broker requests.");
    }

    private static ToolExecutionContext context(Map<String, Object> input) {
        ToolDefinition definition = new ToolDefinition(
                "rmq.acl.update",
                new ToolDefinition.Cli("acl", "update"),
                "Update ACL rule.",
                ToolRiskLevel.L2,
                "acl:write",
                List.of(),
                Map.of("type", "object"),
                Map.of("type", "object"),
                "object",
                false,
                null);
        return ToolExecutionContext.of("cluster-a", definition, input);
    }
}
