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

import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ToolDefinitionTest {

    @Test
    public void testBuilder() {
        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create a new topic")
                .returnType("VOID")
                .build();

        Assert.assertNotNull(def);
        Assert.assertEquals("rmq.topic.create", def.getName());
        Assert.assertEquals("topic", def.getResource());
        Assert.assertEquals("create", def.getVerb());
        Assert.assertEquals(RiskLevel.L2, def.getRiskLevel());
        Assert.assertEquals("Create a new topic", def.getDescription());
        Assert.assertEquals("VOID", def.getReturnType());
    }

    @Test
    public void testBuilderWithParams() {
        ParamSchema param = ParamSchema.builder()
                .name("cluster")
                .type("string")
                .required(true)
                .description("Cluster name")
                .build();

        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .params(Arrays.asList(param))
                .returnType("VOID")
                .build();

        Assert.assertNotNull(def.getParams());
        Assert.assertEquals(1, def.getParams().size());
        Assert.assertEquals("cluster", def.getParams().get(0).getName());
    }

    @Test
    public void testNoArgsConstructor() {
        ToolDefinition def = new ToolDefinition();
        Assert.assertNull(def.getName());
        Assert.assertNull(def.getResource());
        Assert.assertNull(def.getVerb());
        Assert.assertNull(def.getRiskLevel());
        Assert.assertNull(def.getDescription());
        Assert.assertNull(def.getParams());
        Assert.assertNull(def.getReturnType());
    }

    @Test
    public void testAllArgsConstructor() {
        ParamSchema param = ParamSchema.builder().name("p1").build();
        List<ParamSchema> params = Arrays.asList(param);

        ToolDefinition def = new ToolDefinition(
                "rmq.group.list", "group", "list", RiskLevel.L1,
                "List groups", params, "LIST");

        Assert.assertEquals("rmq.group.list", def.getName());
        Assert.assertEquals("group", def.getResource());
        Assert.assertEquals("list", def.getVerb());
        Assert.assertEquals(RiskLevel.L1, def.getRiskLevel());
        Assert.assertEquals("List groups", def.getDescription());
        Assert.assertEquals(params, def.getParams());
        Assert.assertEquals("LIST", def.getReturnType());
    }

    @Test
    public void testSetters() {
        ToolDefinition def = new ToolDefinition();
        def.setName("rmq.test.run");
        def.setResource("test");
        def.setVerb("run");
        def.setRiskLevel(RiskLevel.L3);
        def.setDescription("Test run");
        def.setReturnType("OBJECT");
        def.setParams(Arrays.asList());

        Assert.assertEquals("rmq.test.run", def.getName());
        Assert.assertEquals("test", def.getResource());
        Assert.assertEquals("run", def.getVerb());
        Assert.assertEquals(RiskLevel.L3, def.getRiskLevel());
        Assert.assertEquals("Test run", def.getDescription());
        Assert.assertEquals("OBJECT", def.getReturnType());
        Assert.assertEquals(0, def.getParams().size());
    }

    @Test
    public void testGetMcpToolNameWithHyphen() {
        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.group.reset-offset")
                .resource("group")
                .verb("reset-offset")
                .build();

        Assert.assertEquals("rmq.group.reset_offset", def.getMcpToolName());
    }

    @Test
    public void testGetMcpToolNameWithoutHyphen() {
        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .build();

        Assert.assertEquals("rmq.topic.create", def.getMcpToolName());
    }

    @Test
    public void testGetMcpToolNameMultipleHyphens() {
        ToolDefinition def = ToolDefinition.builder()
                .resource("acl")
                .verb("update-perm")
                .build();

        Assert.assertEquals("rmq.acl.update_perm", def.getMcpToolName());
    }

    @Test
    public void testGetCliCommand() {
        ToolDefinition def = ToolDefinition.builder()
                .resource("topic")
                .verb("create")
                .build();

        Assert.assertEquals("topic create", def.getCliCommand());
    }

    @Test
    public void testGetCliCommandWithHyphen() {
        ToolDefinition def = ToolDefinition.builder()
                .resource("group")
                .verb("reset-offset")
                .build();

        Assert.assertEquals("group reset-offset", def.getCliCommand());
    }

    @Test
    public void testEqualsAndHashCode() {
        ToolDefinition def1 = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .build();

        ToolDefinition def2 = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create topic")
                .build();

        Assert.assertEquals(def1, def2);
        Assert.assertEquals(def1.hashCode(), def2.hashCode());
    }

    @Test
    public void testToString() {
        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .build();

        String str = def.toString();
        Assert.assertNotNull(str);
        Assert.assertTrue(str.contains("rmq.topic.create"));
        Assert.assertTrue(str.contains("topic"));
        Assert.assertTrue(str.contains("create"));
    }

    @Test
    public void testNullParams() {
        ToolDefinition def = ToolDefinition.builder()
                .name("rmq.topic.list")
                .resource("topic")
                .verb("list")
                .build();

        Assert.assertNull(def.getParams());
    }
}