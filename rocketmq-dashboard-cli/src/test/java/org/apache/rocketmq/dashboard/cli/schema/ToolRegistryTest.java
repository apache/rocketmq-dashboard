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
package org.apache.rocketmq.dashboard.cli.schema;

import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ToolRegistryTest {

    @Test
    public void testGetInstance() {
        ToolRegistry instance1 = ToolRegistry.getInstance();
        ToolRegistry instance2 = ToolRegistry.getInstance();
        Assert.assertNotNull(instance1);
        Assert.assertSame(instance1, instance2);
    }

    @Test
    public void testGetAllTools() {
        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        Assert.assertNotNull(tools);
        Assert.assertTrue("Should have at least 30 tools, found " + tools.size(), tools.size() >= 30);
    }

    @Test
    public void testGetTool() {
        ToolDefinition tool = ToolRegistry.getInstance().getTool("rmq.topic.create");
        Assert.assertNotNull(tool);
        Assert.assertEquals("rmq.topic.create", tool.getName());
        Assert.assertEquals("topic", tool.getResource());
        Assert.assertEquals("create", tool.getVerb());
        Assert.assertEquals(RiskLevel.L2, tool.getRiskLevel());
    }

    @Test
    public void testGetToolNotFound() {
        ToolDefinition tool = ToolRegistry.getInstance().getTool("rmq.nonexistent.fake");
        Assert.assertNull(tool);
    }

    @Test
    public void testGetToolsByResource() {
        List<ToolDefinition> topicTools = ToolRegistry.getInstance().getToolsByResource("topic");
        Assert.assertNotNull(topicTools);
        Assert.assertTrue("Should have at least 5 topic tools", topicTools.size() >= 5);
        for (ToolDefinition t : topicTools) {
            Assert.assertEquals("topic", t.getResource());
        }
    }

    @Test
    public void testGetToolsByResourceCaseInsensitive() {
        List<ToolDefinition> tools = ToolRegistry.getInstance().getToolsByResource("TOPIC");
        Assert.assertNotNull(tools);
        Assert.assertTrue(tools.size() >= 5);
    }

    @Test
    public void testGetToolsByRiskLevel() {
        List<ToolDefinition> l1Tools = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L1);
        List<ToolDefinition> l2Tools = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L2);
        List<ToolDefinition> l3Tools = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L3);

        Assert.assertNotNull(l1Tools);
        Assert.assertNotNull(l2Tools);
        Assert.assertNotNull(l3Tools);
        Assert.assertTrue("L1 tools should exist", l1Tools.size() > 0);
        Assert.assertTrue("L2 tools should exist", l2Tools.size() > 0);
        Assert.assertTrue("L3 tools should exist", l3Tools.size() > 0);

        int total = l1Tools.size() + l2Tools.size() + l3Tools.size();
        Assert.assertEquals(ToolRegistry.getInstance().getAllTools().size(), total);
    }

    @Test
    public void testToolNaming() {
        for (ToolDefinition t : ToolRegistry.getInstance().getAllTools()) {
            String name = t.getName();
            Assert.assertTrue("Tool name should start with 'rmq.': " + name, name.startsWith("rmq."));
            String[] parts = name.substring(4).split("\\.", 2);
            Assert.assertEquals("Tool name should have format rmq.resource.verb: " + name, 2, parts.length);
        }
    }

    @Test
    public void testAllToolsHaveClusterParam() {
        for (ToolDefinition t : ToolRegistry.getInstance().getAllTools()) {
            List<ParamSchema> params = t.getParams();
            Assert.assertNotNull("Params should not be null for " + t.getName(), params);
            Assert.assertTrue("Tool should have at least 1 param: " + t.getName(), params.size() >= 1);

            boolean hasClusterParam = params.stream()
                    .anyMatch(p -> "cluster".equals(p.getName()) && p.isRequired());
            Assert.assertTrue("Tool " + t.getName() + " should have required 'cluster' param",
                    hasClusterParam);
        }
    }

    @Test
    public void testAllToolsHaveDescription() {
        for (ToolDefinition t : ToolRegistry.getInstance().getAllTools()) {
            Assert.assertNotNull("Tool " + t.getName() + " should have a description", t.getDescription());
            Assert.assertFalse("Tool " + t.getName() + " description should not be empty", t.getDescription().isEmpty());
        }
    }

    @Test
    public void testRiskLevelDistribution() {
        List<ToolDefinition> l1 = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L1);
        List<ToolDefinition> l2 = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L2);
        List<ToolDefinition> l3 = ToolRegistry.getInstance().getToolsByRiskLevel(RiskLevel.L3);

        Assert.assertTrue("Should have L1 (read-only) tools", l1.size() > 0);
        Assert.assertTrue("Should have L2 (controlled mutation) tools", l2.size() > 0);
        Assert.assertTrue("Should have L3 (dangerous) tools", l3.size() > 0);
    }

    @Test
    public void testGroupTools() {
        List<ToolDefinition> groupTools = ToolRegistry.getInstance().getToolsByResource("group");
        Assert.assertNotNull(groupTools);
        Assert.assertTrue(groupTools.size() >= 5);
        Assert.assertNotNull(ToolRegistry.getInstance().getTool("rmq.group.reset-offset"));
    }

    @Test
    public void testGetMcpToolName() {
        ToolDefinition resetOffset = ToolRegistry.getInstance().getTool("rmq.group.reset-offset");
        Assert.assertNotNull(resetOffset);
        Assert.assertEquals("rmq.group.reset_offset", resetOffset.getMcpToolName());
    }

    @Test
    public void testGetCliCommand() {
        ToolDefinition topicCreate = ToolRegistry.getInstance().getTool("rmq.topic.create");
        Assert.assertNotNull(topicCreate);
        Assert.assertEquals("topic create", topicCreate.getCliCommand());
    }

    @Test
    public void testAllToolsHaveReturnType() {
        for (ToolDefinition t : ToolRegistry.getInstance().getAllTools()) {
            Assert.assertNotNull("Tool " + t.getName() + " should have a return type", t.getReturnType());
            Assert.assertFalse("Tool " + t.getName() + " return type should not be empty", t.getReturnType().isEmpty());
        }
    }
}
