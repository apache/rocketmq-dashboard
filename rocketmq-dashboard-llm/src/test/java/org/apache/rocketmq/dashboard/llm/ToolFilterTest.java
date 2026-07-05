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
package org.apache.rocketmq.dashboard.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ToolFilterTest {

    private List<ToolDefinition> allTools;

    @Before
    public void setUp() {
        allTools = new ArrayList<>();
        // L1 tools: read-only
        allTools.add(buildTool("rmq.topic.list", "topic", RiskLevel.L1));
        allTools.add(buildTool("rmq.group.list", "group", RiskLevel.L1));
        allTools.add(buildTool("rmq.cluster.list", "cluster", RiskLevel.L1));
        allTools.add(buildTool("rmq.broker.list", "broker", RiskLevel.L1));

        // L2 tools: controlled mutation
        allTools.add(buildTool("rmq.topic.create", "topic", RiskLevel.L2));
        allTools.add(buildTool("rmq.topic.update", "topic", RiskLevel.L2));
        allTools.add(buildTool("rmq.group.create", "group", RiskLevel.L2));

        // L3 tools: dangerous operations
        allTools.add(buildTool("rmq.topic.delete", "topic", RiskLevel.L3));
        allTools.add(buildTool("rmq.group.delete", "group", RiskLevel.L3));
        allTools.add(buildTool("rmq.namespace.delete", "namespace", RiskLevel.L3));
    }

    // ---- ADMIN user tests ------------------------------------------------------

    @Test
    public void testAdminSeesAllTools() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "ADMIN", ClusterCapability.all());

        assertNotNull("Filtered list should not be null", filtered);
        assertEquals("Admin should see all 10 tools", 10, filtered.size());
    }

    @Test
    public void testAdminSeesAllToolsWhenCapabilityLimited() {
        ClusterCapability topicOnly = ClusterCapability.of("topic");

        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "ADMIN", topicOnly);

        // Should see only topic tools: list(L1), create(L2), update(L2), delete(L3)
        assertEquals("Admin should see 4 topic tools", 4, filtered.size());
        assertAllAreResource(filtered, "topic");
    }

    @Test
    public void testAdminSeesAllToolsWhenCapabilityIsGroupOnly() {
        ClusterCapability groupOnly = ClusterCapability.of("group");

        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "ADMIN", groupOnly);

        // Should see only group tools: list(L1), create(L2), delete(L3)
        assertEquals("Admin should see 3 group tools", 3, filtered.size());
        assertAllAreResource(filtered, "group");
    }

    // ---- Normal (non-ADMIN) user tests -----------------------------------------

    @Test
    public void testNormalUserSeesOnlyL1Tools() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "USER", ClusterCapability.all());

        assertNotNull("Filtered list should not be null", filtered);
        assertEquals("Normal user should see only 4 L1 tools", 4, filtered.size());

        // All should be L1
        for (ToolDefinition tool : filtered) {
            assertEquals("Non-ADMIN user should only see L1 tools",
                    RiskLevel.L1, tool.getRiskLevel());
        }
    }

    @Test
    public void testNormalUserSeesOnlyL1ToolsWithTopicCapability() {
        ClusterCapability topicOnly = ClusterCapability.of("topic");

        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "USER", topicOnly);

        // Should see only L1 topic tools: rmq.topic.list
        assertEquals("Normal user should see 1 L1 topic tool", 1, filtered.size());
        assertEquals("rmq.topic.list", filtered.get(0).getName());
    }

    @Test
    public void testNormalUserSeesNoToolsWhenCapabilityDoesNotMatch() {
        ClusterCapability noMatch = ClusterCapability.of("metrics", "acl", "client");

        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "USER", noMatch);

        assertEquals("Normal user should see 0 tools with non-matching capability",
                0, filtered.size());
    }

    // ---- Null capability tests -------------------------------------------------

    @Test
    public void testNullCapabilityPassesAll() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "ADMIN", null);

        assertEquals("Null capability should pass all tools for admin", 10, filtered.size());
    }

    @Test
    public void testNullCapabilityPassesL1ForNormalUser() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "USER", null);

        assertEquals("Null capability should pass all L1 for normal user", 4, filtered.size());
    }

    // ---- Edge case tests -------------------------------------------------------

    @Test
    public void testEmptyToolList() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                Collections.emptyList(), "ADMIN", ClusterCapability.all());

        assertNotNull("Result should not be null", filtered);
        assertTrue("Result should be empty", filtered.isEmpty());
    }

    @Test
    public void testEmptyToolListForNormalUser() {
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                Collections.emptyList(), "USER", ClusterCapability.all());

        assertNotNull("Result should not be null", filtered);
        assertTrue("Result should be empty", filtered.isEmpty());
    }

    @Test
    public void testCapabilityAllSupportsEverything() {
        ClusterCapability all = ClusterCapability.all();
        assertTrue("Should support topic", all.supports("topic"));
        assertTrue("Should support group", all.supports("group"));
        assertTrue("Should support cluster", all.supports("cluster"));
        assertTrue("Should support namespace", all.supports("namespace"));
        assertTrue("Should support message", all.supports("message"));
        assertTrue("Should support client", all.supports("client"));
        assertTrue("Should support acl", all.supports("acl"));
        assertTrue("Should support broker", all.supports("broker"));
        assertTrue("Should support metrics", all.supports("metrics"));
        assertTrue("Should support capabilities", all.supports("capabilities"));
        assertFalse("Should not support unknown resource",
                all.supports("unknown_resource"));
    }

    @Test
    public void testCapabilityOf() {
        ClusterCapability cap = ClusterCapability.of("topic", "group");
        assertTrue("Should support topic", cap.supports("topic"));
        assertTrue("Should support group", cap.supports("group"));
        assertFalse("Should not support cluster", cap.supports("cluster"));
        assertFalse("Should not support namespace", cap.supports("namespace"));
    }

    @Test
    public void testCapabilityOfSingleResource() {
        ClusterCapability cap = ClusterCapability.of("topic");
        assertTrue("Should support topic", cap.supports("topic"));
        assertFalse("Should not support group", cap.supports("group"));
    }

    @Test
    public void testCapabilityWithNoResources() {
        ClusterCapability cap = ClusterCapability.of();
        assertFalse("Should not support any resource", cap.supports("topic"));
        assertFalse("Should not support any resource", cap.supports("group"));
    }

    @Test
    public void testFilterDoesNotModifyOriginalList() {
        List<ToolDefinition> original = new ArrayList<>(allTools);
        int originalSize = original.size();

        ToolFilter.filterForUser(allTools, "ADMIN", ClusterCapability.all());

        assertEquals("Original list should not be modified", originalSize, original.size());
    }

    @Test
    public void testAdminWithMixedLevelsAndCapabilities() {
        ClusterCapability topicAndGroup = ClusterCapability.of("topic", "group");

        // Admin should see all topic and group tools regardless of risk level
        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "ADMIN", topicAndGroup);

        // Expected: topic.list(L1), topic.create(L2), topic.update(L2), topic.delete(L3),
        //           group.list(L1), group.create(L2), group.delete(L3) = 7 tools
        assertEquals("Admin should see 7 tools for topic+group", 7, filtered.size());

        // Verify all tools have resource "topic" or "group"
        for (ToolDefinition tool : filtered) {
            assertTrue("Tool resource should be topic or group",
                    "topic".equals(tool.getResource()) || "group".equals(tool.getResource()));
        }
    }

    @Test
    public void testNormalUserWithMixedLevelsAndCapabilities() {
        ClusterCapability topicAndGroup = ClusterCapability.of("topic", "group");

        List<ToolDefinition> filtered = ToolFilter.filterForUser(
                allTools, "USER", topicAndGroup);

        // Normal user should see only L1 tools for topic and group: topic.list, group.list
        assertEquals("Normal user should see 2 L1 tools for topic+group", 2, filtered.size());

        for (ToolDefinition tool : filtered) {
            assertEquals("Normal user should only see L1 tools", RiskLevel.L1, tool.getRiskLevel());
        }
    }

    // ---- Utility assertions ----------------------------------------------------

    private void assertAllAreResource(List<ToolDefinition> tools, String expectedResource) {
        for (ToolDefinition tool : tools) {
            assertEquals("All tools should have resource " + expectedResource,
                    expectedResource, tool.getResource());
        }
    }

    private ToolDefinition buildTool(String name, String resource, RiskLevel riskLevel) {
        return ToolDefinition.builder()
                .name(name)
                .resource(resource)
                .verb(name.substring(name.lastIndexOf('.') + 1))
                .riskLevel(riskLevel)
                .description("Test tool: " + name)
                .params(Collections.emptyList())
                .returnType(riskLevel == RiskLevel.L1 ? "LIST" : "OBJECT")
                .build();
    }
}
