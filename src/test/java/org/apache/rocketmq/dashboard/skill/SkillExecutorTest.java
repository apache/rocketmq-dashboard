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
package org.apache.rocketmq.dashboard.skill;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SkillExecutorTest {

    /**
     * Stub skill with a scripted execution result.
     */
    private static class ScriptedSkill extends AbstractSkill {
        private final String id;
        private final boolean available;
        private final SkillResult result;
        private final RuntimeException toThrow;
        private final List<SkillParameter> parameters;

        ScriptedSkill(String id, boolean available, SkillResult result, RuntimeException toThrow,
                      List<SkillParameter> parameters) {
            this.id = id;
            this.available = available;
            this.result = result;
            this.toThrow = toThrow;
            this.parameters = parameters;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return "Scripted " + id;
        }

        @Override
        public String getDescription() {
            return "scripted skill " + id;
        }

        @Override
        public List<SkillParameter> getParameters() {
            return parameters;
        }

        @Override
        public String getResourceType() {
            return "test";
        }

        @Override
        public String getVerb() {
            return "query";
        }

        @Override
        public RiskLevel getRiskLevel() {
            return RiskLevel.L1;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public SkillResult execute(Map<String, Object> parameters) {
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }

    private SkillRegistry registry;
    private SkillExecutor executor;

    @Before
    public void setUp() {
        registry = new SkillRegistry();
        executor = new SkillExecutor();
        ReflectionTestUtils.setField(executor, "skillRegistry", registry);
    }

    private void register(ScriptedSkill skill) {
        registry.registerSkill(skill);
    }

    @Test
    public void testExecuteToolNotFound() {
        Map<String, Object> result = executor.executeTool("unknown", new HashMap<>());
        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(((String) result.get("error")).contains("not found"));
    }

    @Test
    public void testExecuteToolUnavailable() {
        register(new ScriptedSkill("offline", false, SkillResult.successVoid(), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("offline", new HashMap<>());
        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(((String) result.get("error")).contains("not available"));
    }

    @Test
    public void testExecuteToolTextResult() {
        register(new ScriptedSkill("text", true, SkillResult.successText("hello"), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("text", null);
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("hello", result.get("content"));
    }

    @Test
    public void testExecuteToolListResultIncludesCount() {
        List<String> data = Arrays.asList("a", "b", "c");
        register(new ScriptedSkill("list", true, SkillResult.successList(data), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("list", new HashMap<>());
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals(data, result.get("content"));
        assertEquals(3, result.get("count"));
    }

    @Test
    public void testExecuteToolObjectResult() {
        Map<String, Object> data = Collections.singletonMap("k", "v");
        register(new ScriptedSkill("obj", true, SkillResult.successObject(data), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("obj", new HashMap<>());
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals(data, result.get("content"));
    }

    @Test
    public void testExecuteToolVoidResultStringified() {
        register(new ScriptedSkill("void", true, SkillResult.successVoid(), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("void", new HashMap<>());
        assertEquals(Boolean.TRUE, result.get("success"));
        assertEquals("", result.get("content"));
    }

    @Test
    public void testExecuteToolIncludesMetadata() {
        Map<String, Object> metadata = Collections.singletonMap("elapsedMs", 5L);
        SkillResult result = SkillResult.builder()
                .success(true).data("x").returnType("TEXT").metadata(metadata).build();
        register(new ScriptedSkill("meta", true, result, null, Collections.emptyList()));
        Map<String, Object> response = executor.executeTool("meta", new HashMap<>());
        assertEquals(metadata, response.get("metadata"));
    }

    @Test
    public void testExecuteToolFailureResult() {
        register(new ScriptedSkill("fail", true, SkillResult.failure("bad input"), null, Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("fail", new HashMap<>());
        assertEquals(Boolean.FALSE, result.get("success"));
        assertEquals("bad input", result.get("error"));
    }

    @Test
    public void testExecuteToolExceptionWrapped() {
        register(new ScriptedSkill("boom", true, null, new IllegalStateException("kaput"), Collections.emptyList()));
        Map<String, Object> result = executor.executeTool("boom", new HashMap<>());
        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(((String) result.get("error")).contains("kaput"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListToolsBuildsInputSchema() {
        List<SkillParameter> params = Arrays.asList(
                SkillParameter.builder()
                        .name("action").type("ENUM").required(true)
                        .description("the action")
                        .allowedValues(Arrays.asList("list", "detail"))
                        .build(),
                SkillParameter.builder()
                        .name("limit").type("INTEGER").required(false)
                        .description("max entries").defaultValue("10")
                        .build(),
                SkillParameter.builder()
                        .name("verbose").type("BOOLEAN").required(false)
                        .description("verbose output")
                        .build(),
                SkillParameter.builder()
                        .name("custom").type("UNKNOWN_TYPE").required(false)
                        .description("falls back to string")
                        .build());
        register(new ScriptedSkill("schema", true, SkillResult.successVoid(), null, params));
        // Unavailable skills must not be listed
        register(new ScriptedSkill("hidden", false, SkillResult.successVoid(), null, Collections.emptyList()));

        List<Map<String, Object>> tools = executor.listTools();
        assertEquals(1, tools.size());

        Map<String, Object> tool = tools.get(0);
        assertEquals("schema", tool.get("name"));
        assertNotNull(tool.get("description"));

        Map<String, Object> inputSchema = (Map<String, Object>) tool.get("inputSchema");
        assertEquals("object", inputSchema.get("type"));
        assertEquals(Collections.singletonList("action"), inputSchema.get("required"));

        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        Map<String, Object> action = (Map<String, Object>) properties.get("action");
        assertEquals("string", action.get("type"));
        assertEquals(Arrays.asList("list", "detail"), action.get("enum"));

        Map<String, Object> limit = (Map<String, Object>) properties.get("limit");
        assertEquals("integer", limit.get("type"));
        assertEquals("10", limit.get("default"));

        Map<String, Object> verbose = (Map<String, Object>) properties.get("verbose");
        assertEquals("boolean", verbose.get("type"));

        Map<String, Object> custom = (Map<String, Object>) properties.get("custom");
        assertEquals("string", custom.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListToolsOmitsRequiredWhenNone() {
        register(new ScriptedSkill("norequired", true, SkillResult.successVoid(), null,
                Collections.singletonList(SkillParameter.builder()
                        .name("opt").type("STRING").required(false).description("optional").build())));

        List<Map<String, Object>> tools = executor.listTools();
        Map<String, Object> inputSchema = (Map<String, Object>) tools.get(0).get("inputSchema");
        assertTrue(!inputSchema.containsKey("required"));
    }
}
