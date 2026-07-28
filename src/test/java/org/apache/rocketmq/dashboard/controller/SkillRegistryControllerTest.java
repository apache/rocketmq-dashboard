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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillExecutor;
import org.apache.rocketmq.dashboard.skill.SkillParameter;
import org.apache.rocketmq.dashboard.skill.SkillRegistry;
import org.apache.rocketmq.dashboard.skill.SkillResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SkillRegistryControllerTest {

    @InjectMocks
    private SkillRegistryController skillRegistryController;

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private SkillExecutor skillExecutor;

    /**
     * Minimal Skill implementation used for the dynamic-registration test,
     * which requires a loadable class with a no-arg constructor.
     */
    public static class DummySkill implements Skill {
        @Override
        public String getId() {
            return "dummy.skill";
        }

        @Override
        public String getName() {
            return "Dummy Skill";
        }

        @Override
        public String getDescription() {
            return "A dummy skill for tests";
        }

        @Override
        public List<SkillParameter> getParameters() {
            return Collections.emptyList();
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
        public SkillResult execute(Map<String, Object> parameters) {
            return null;
        }
    }

    /**
     * Skill implementation whose constructor always throws, used to cover the
     * generic exception branch of the register endpoint.
     */
    public static class BrokenSkill extends DummySkill {
        public BrokenSkill() {
            throw new IllegalStateException("cannot instantiate");
        }
    }

    private Skill mockSkill(String id) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getName()).thenReturn("name-" + id);
        when(skill.getDescription()).thenReturn("desc");
        when(skill.getResourceType()).thenReturn("topic");
        when(skill.getVerb()).thenReturn("query");
        when(skill.getRiskLevel()).thenReturn(Skill.RiskLevel.L1);
        when(skill.isAvailable()).thenReturn(true);
        when(skill.getParameters()).thenReturn(Collections.emptyList());
        return skill;
    }

    // ==================== listSkills ====================

    @Test
    public void testListSkills() {
        List<Map<String, Object>> metadata = Arrays.asList(
            Collections.singletonMap("id", "topic.query"));
        when(skillRegistry.getSkillMetadata()).thenReturn(metadata);

        ResponseEntity<List<Map<String, Object>>> response = skillRegistryController.listSkills();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    // ==================== getSkill ====================

    @Test
    public void testGetSkillNotFound() {
        when(skillRegistry.getSkill("missing")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = skillRegistryController.getSkill("missing");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    public void testGetSkillFound() {
        Skill skill = mockSkill("topic.query");
        when(skillRegistry.getSkill("topic.query")).thenReturn(skill);

        ResponseEntity<Map<String, Object>> response = skillRegistryController.getSkill("topic.query");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("topic.query", response.getBody().get("id"));
        assertEquals("L1", response.getBody().get("riskLevel"));
        assertEquals(Boolean.TRUE, response.getBody().get("available"));
    }

    // ==================== executeSkill ====================

    @Test
    public void testExecuteSkillSuccess() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        when(skillExecutor.executeTool(eq("topic.query"), any())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
            skillRegistryController.executeSkill("topic.query", new HashMap<>());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    public void testExecuteSkillFailure() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("error", "Skill not found");
        when(skillExecutor.executeTool(eq("missing"), any())).thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
            skillRegistryController.executeSkill("missing", null);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Skill not found", response.getBody().get("error"));
    }

    // ==================== listMcpTools ====================

    @Test
    public void testListMcpTools() {
        when(skillExecutor.listTools()).thenReturn(Collections.emptyList());

        ResponseEntity<List<Map<String, Object>>> response = skillRegistryController.listMcpTools();
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    // ==================== executeMcpTool ====================

    @Test
    public void testExecuteMcpToolMissingName() {
        Map<String, Object> request = new HashMap<>();
        request.put("arguments", new HashMap<>());

        ResponseEntity<Map<String, Object>> response = skillRegistryController.executeMcpTool(request);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Tool name is required", response.getBody().get("error"));
    }

    @Test
    public void testExecuteMcpToolBlankName() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", " ");

        ResponseEntity<Map<String, Object>> response = skillRegistryController.executeMcpTool(request);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    public void testExecuteMcpToolSuccess() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        when(skillExecutor.executeTool(eq("topic.query"), any())).thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("name", "topic.query");
        request.put("arguments", new HashMap<String, Object>());

        ResponseEntity<Map<String, Object>> response = skillRegistryController.executeMcpTool(request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    public void testExecuteMcpToolFailure() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        when(skillExecutor.executeTool(eq("bad.tool"), any())).thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("name", "bad.tool");

        ResponseEntity<Map<String, Object>> response = skillRegistryController.executeMcpTool(request);
        assertEquals(400, response.getStatusCode().value());
    }

    // ==================== getStats ====================

    @Test
    public void testGetStats() {
        Skill topicSkill = mockSkill("topic.query");
        Skill clusterSkill = mockSkill("cluster.info");
        when(clusterSkill.getResourceType()).thenReturn("cluster");
        when(skillRegistry.getSkillCount()).thenReturn(2);
        when(skillRegistry.getAvailableSkills()).thenReturn(Arrays.asList(topicSkill, clusterSkill));
        when(skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L1))
            .thenReturn(Arrays.asList(topicSkill, clusterSkill));
        when(skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L2)).thenReturn(Collections.emptyList());
        when(skillRegistry.getSkillsByRiskLevel(Skill.RiskLevel.L3)).thenReturn(Collections.emptyList());
        when(skillRegistry.getAllSkills()).thenReturn(Arrays.asList(topicSkill, clusterSkill));

        ResponseEntity<Map<String, Object>> response = skillRegistryController.getStats();
        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().get("totalSkills"));
        assertEquals(2, response.getBody().get("availableSkills"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byRiskLevel = (Map<String, Long>) response.getBody().get("byRiskLevel");
        assertEquals(Long.valueOf(2L), byRiskLevel.get("L1"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byResourceType = (Map<String, Long>) response.getBody().get("byResourceType");
        assertEquals(Long.valueOf(1L), byResourceType.get("topic"));
        assertEquals(Long.valueOf(1L), byResourceType.get("cluster"));
    }

    // ==================== registerSkill ====================

    @Test
    public void testRegisterSkillMissingClassName() {
        ResponseEntity<Map<String, Object>> response =
            skillRegistryController.registerSkill(new HashMap<>());
        assertEquals(400, response.getStatusCode().value());
        assertEquals("className is required", response.getBody().get("error"));
    }

    @Test
    public void testRegisterSkillClassNotFound() {
        Map<String, Object> request = new HashMap<>();
        request.put("className", "org.example.DoesNotExist");

        ResponseEntity<Map<String, Object>> response = skillRegistryController.registerSkill(request);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(((String) response.getBody().get("error")).contains("Class not found"));
    }

    @Test
    public void testRegisterSkillClassNotASkill() {
        Map<String, Object> request = new HashMap<>();
        request.put("className", "java.lang.String");

        ResponseEntity<Map<String, Object>> response = skillRegistryController.registerSkill(request);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(((String) response.getBody().get("error")).contains("does not implement Skill"));
    }

    @Test
    public void testRegisterSkillSuccess() {
        Map<String, Object> request = new HashMap<>();
        request.put("className", DummySkill.class.getName());

        ResponseEntity<Map<String, Object>> response = skillRegistryController.registerSkill(request);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
        assertEquals("dummy.skill", response.getBody().get("skillId"));
        verify(skillRegistry).registerSkill(any(Skill.class));
    }

    @Test
    public void testRegisterSkillConstructorThrows() {
        Map<String, Object> request = new HashMap<>();
        request.put("className", BrokenSkill.class.getName());

        ResponseEntity<Map<String, Object>> response = skillRegistryController.registerSkill(request);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(((String) response.getBody().get("error")).contains("Failed to register skill"));
    }

    // ==================== unregisterSkill ====================

    @Test
    public void testUnregisterSkillSuccess() {
        when(skillRegistry.unregisterSkill("topic.query")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response =
            skillRegistryController.unregisterSkill("topic.query");
        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().get("success"));
    }

    @Test
    public void testUnregisterSkillNotFound() {
        when(skillRegistry.unregisterSkill("missing")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response =
            skillRegistryController.unregisterSkill("missing");
        assertEquals(404, response.getStatusCode().value());
    }
}
