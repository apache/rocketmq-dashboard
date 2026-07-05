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
package org.apache.rocketmq.dashboard.mcp.tools;

import java.util.Collections;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SecurityGateTest {

    // ---- Default configuration tests -------------------------------------------

    @Test
    public void testDefaultConfig() {
        SecurityGate gate = new SecurityGate();
        assertTrue("L1 should be allowed by default", gate.isAllowL1());
        assertTrue("L2 should be allowed by default", gate.isAllowL2());
        assertFalse("L3 should be blocked by default", gate.isAllowL3());
    }

    @Test
    public void testEnableL3() {
        SecurityGate gate = new SecurityGate(true);
        assertTrue("L3 should be allowed when explicitly enabled", gate.isAllowL3());
    }

    // ---- Setter tests ----------------------------------------------------------

    @Test
    public void testSetAllowL1() {
        SecurityGate gate = new SecurityGate();
        gate.setAllowL1(false);
        assertFalse("L1 should be disabled after setAllowL1(false)", gate.isAllowL1());
    }

    @Test
    public void testSetAllowL2() {
        SecurityGate gate = new SecurityGate();
        gate.setAllowL2(false);
        assertFalse("L2 should be disabled after setAllowL2(false)", gate.isAllowL2());
    }

    @Test
    public void testSetAllowL3() {
        SecurityGate gate = new SecurityGate();
        gate.setAllowL3(true);
        assertTrue("L3 should be enabled after setAllowL3(true)", gate.isAllowL3());
    }

    // ---- Tool check tests ------------------------------------------------------

    @Test
    public void testCheckL1ToolAllowed() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.list")
                .resource("topic")
                .verb("list")
                .riskLevel(RiskLevel.L1)
                .description("List topics")
                .params(Collections.emptyList())
                .returnType("LIST")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L1 tool should be ALLOW", SecurityCheckResult.Action.ALLOW, result.getAction());
        assertTrue("L1 tool should be allowed", result.isAllowed());
    }

    @Test
    public void testCheckL2ToolDryRun() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .params(Collections.emptyList())
                .returnType("OBJECT")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L2 tool should be DRY_RUN", SecurityCheckResult.Action.DRY_RUN, result.getAction());
        assertTrue("L2 tool should be dry run", result.isDryRun());
        assertFalse("L2 tool should not be blocked", result.isBlocked());
    }

    @Test
    public void testCheckL3ToolBlocked() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.delete")
                .resource("topic")
                .verb("delete")
                .riskLevel(RiskLevel.L3)
                .description("Delete topic")
                .params(Collections.emptyList())
                .returnType("VOID")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L3 tool should be BLOCK", SecurityCheckResult.Action.BLOCK, result.getAction());
        assertTrue("L3 tool should be blocked", result.isBlocked());
    }

    @Test
    public void testCheckL3ToolWithDangerousOpsEnabled() {
        SecurityGate gate = new SecurityGate(true);
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.delete")
                .resource("topic")
                .verb("delete")
                .riskLevel(RiskLevel.L3)
                .description("Delete topic")
                .params(Collections.emptyList())
                .returnType("VOID")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L3 tool with dangerous ops should be DRY_RUN",
                SecurityCheckResult.Action.DRY_RUN, result.getAction());
    }

    @Test
    public void testCheckNullTool() {
        SecurityGate gate = new SecurityGate();
        SecurityCheckResult result = gate.check(null);
        assertEquals("Null tool should be BLOCK", SecurityCheckResult.Action.BLOCK, result.getAction());
    }

    @Test
    public void testCheckToolWithNullRiskLevel() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.test.tool")
                .resource("test")
                .verb("test")
                .riskLevel(null)
                .description("Test tool")
                .params(Collections.emptyList())
                .returnType("VOID")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("Tool with null risk level should be BLOCK",
                SecurityCheckResult.Action.BLOCK, result.getAction());
    }

    @Test
    public void testCheckL1ToolWhenL1Disabled() {
        SecurityGate gate = new SecurityGate();
        gate.setAllowL1(false);
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.list")
                .resource("topic")
                .verb("list")
                .riskLevel(RiskLevel.L1)
                .description("List topics")
                .params(Collections.emptyList())
                .returnType("LIST")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L1 tool should be BLOCK when L1 disabled",
                SecurityCheckResult.Action.BLOCK, result.getAction());
    }

    @Test
    public void testCheckL2ToolWhenL2Disabled() {
        SecurityGate gate = new SecurityGate();
        gate.setAllowL2(false);
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .params(Collections.emptyList())
                .returnType("OBJECT")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertEquals("L2 tool should be BLOCK when L2 disabled",
                SecurityCheckResult.Action.BLOCK, result.getAction());
    }

    // ---- SecurityCheckResult tests ---------------------------------------------

    @Test
    public void testResultActionValues() {
        assertEquals("ALLOW", SecurityCheckResult.Action.ALLOW.name());
        assertEquals("DRY_RUN", SecurityCheckResult.Action.DRY_RUN.name());
        assertEquals("BLOCK", SecurityCheckResult.Action.BLOCK.name());
    }

    @Test
    public void testSecurityCheckResultFactoryAllow() {
        SecurityCheckResult result = SecurityCheckResult.allow();
        assertEquals("Action should be ALLOW", SecurityCheckResult.Action.ALLOW, result.getAction());
        assertTrue("Should be allowed", result.isAllowed());
        assertFalse("Should not be dry run", result.isDryRun());
        assertFalse("Should not be blocked", result.isBlocked());
        assertNotNull("Message should not be null", result.getMessage());
    }

    @Test
    public void testSecurityCheckResultFactoryDryRun() {
        SecurityCheckResult result = SecurityCheckResult.dryRun("test dry run message");
        assertEquals("Action should be DRY_RUN", SecurityCheckResult.Action.DRY_RUN, result.getAction());
        assertFalse("Should not be allowed", result.isAllowed());
        assertTrue("Should be dry run", result.isDryRun());
        assertFalse("Should not be blocked", result.isBlocked());
        assertEquals("Message should match", "test dry run message", result.getMessage());
        assertNotNull("Dry run data should not be null", result.getDryRunData());
        assertTrue("Dry run data should have mode", result.getDryRunData().containsKey("mode"));
        assertEquals("Dry run mode should be dry_run",
                "dry_run", result.getDryRunData().get("mode"));
    }

    @Test
    public void testSecurityCheckResultFactoryBlock() {
        SecurityCheckResult result = SecurityCheckResult.block("test block message");
        assertEquals("Action should be BLOCK", SecurityCheckResult.Action.BLOCK, result.getAction());
        assertFalse("Should not be allowed", result.isAllowed());
        assertFalse("Should not be dry run", result.isDryRun());
        assertTrue("Should be blocked", result.isBlocked());
        assertEquals("Message should match", "test block message", result.getMessage());
    }

    @Test
    public void testSecurityCheckResultToString() {
        SecurityCheckResult result = SecurityCheckResult.allow();
        String str = result.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain action", str.contains("ALLOW"));
        assertTrue("toString should contain message", str.contains("message"));
    }

    @Test
    public void testBlockedResultHasHint() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.delete")
                .resource("topic")
                .verb("delete")
                .riskLevel(RiskLevel.L3)
                .description("Delete topic")
                .params(Collections.emptyList())
                .returnType("VOID")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertTrue("Blocked message should mention enable-dangerous-ops",
                result.getMessage().contains("enable-dangerous-ops"));
    }

    @Test
    public void testDryRunResultHasConfirmationMessage() {
        SecurityGate gate = new SecurityGate();
        ToolDefinition tool = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .params(Collections.emptyList())
                .returnType("OBJECT")
                .build();

        SecurityCheckResult result = gate.check(tool);
        assertTrue("L2 dry-run message should mention confirmation",
                result.getMessage().contains("confirmation"));
    }
}
