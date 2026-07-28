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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractSkillTest {

    /**
     * Minimal concrete skill exposing the protected helpers for testing.
     */
    private static class TestSkill extends AbstractSkill {
        @Override
        public String getId() {
            return "test.skill";
        }

        @Override
        public String getName() {
            return "Test Skill";
        }

        @Override
        public String getDescription() {
            return "A skill for unit tests";
        }

        @Override
        public List<SkillParameter> getParameters() {
            return Arrays.asList(
                    SkillParameter.builder().name("action").type("STRING").required(true).build(),
                    SkillParameter.builder().name("opt").type("STRING").required(false).build());
        }

        @Override
        public String getResourceType() {
            return "test";
        }

        @Override
        public String getVerb() {
            return "run";
        }

        @Override
        public RiskLevel getRiskLevel() {
            return RiskLevel.L1;
        }

        @Override
        public SkillResult execute(Map<String, Object> parameters) {
            validateParameters(parameters);
            return SkillResult.successVoid();
        }

        // Expose protected helpers
        <T> T param(Map<String, Object> parameters, String name, Class<T> type) {
            return getParameter(parameters, name, type);
        }

        <T> T requiredParam(Map<String, Object> parameters, String name, Class<T> type) {
            return getRequiredParameter(parameters, name, type);
        }

        <T> T paramOrDefault(Map<String, Object> parameters, String name, Class<T> type, T defaultValue) {
            return getParameterOrDefault(parameters, name, type, defaultValue);
        }
    }

    private TestSkill skill;

    @Before
    public void setUp() {
        skill = new TestSkill();
    }

    @Test
    public void testLifecycleDefaults() {
        skill.initialize();
        skill.destroy();
        assertTrue(skill.isAvailable());
    }

    @Test
    public void testValidateParametersPasses() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        assertTrue(skill.execute(params).isSuccess());
    }

    @Test
    public void testValidateParametersRejectsMissingRequired() {
        try {
            skill.execute(new HashMap<>());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("action"));
        }
    }

    @Test
    public void testValidateParametersRejectsBlankRequired() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "   ");
        try {
            skill.execute(params);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("action"));
        }
    }

    @Test
    public void testGetParameterReturnsNullForMissing() {
        assertNull(skill.param(new HashMap<>(), "missing", String.class));
    }

    @Test
    public void testGetParameterDirectTypeMatch() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "value");
        assertEquals("value", skill.param(params, "name", String.class));
    }

    @Test
    public void testGetParameterConvertsToString() {
        Map<String, Object> params = new HashMap<>();
        params.put("num", 42);
        assertEquals("42", skill.param(params, "num", String.class));
    }

    @Test
    public void testGetParameterConvertsNumberToInteger() {
        Map<String, Object> params = new HashMap<>();
        params.put("num", 42L);
        assertEquals(Integer.valueOf(42), skill.param(params, "num", Integer.class));
    }

    @Test
    public void testGetParameterConvertsNumberToLong() {
        Map<String, Object> params = new HashMap<>();
        params.put("num", 42);
        assertEquals(Long.valueOf(42L), skill.param(params, "num", Long.class));
    }

    @Test
    public void testGetParameterConvertsStringToBoolean() {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", "true");
        assertEquals(Boolean.TRUE, skill.param(params, "flag", Boolean.class));
    }

    @Test
    public void testGetParameterRejectsIncompatibleType() {
        Map<String, Object> params = new HashMap<>();
        params.put("num", "not-a-number");
        try {
            skill.param(params, "num", Integer.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("num"));
        }
    }

    @Test
    public void testGetRequiredParameterThrowsWhenMissing() {
        try {
            skill.requiredParam(new HashMap<>(), "action", String.class);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("action"));
        }
    }

    @Test
    public void testGetRequiredParameterReturnsValue() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        assertEquals("list", skill.requiredParam(params, "action", String.class));
    }

    @Test
    public void testGetParameterOrDefault() {
        Map<String, Object> params = new HashMap<>();
        params.put("present", "yes");
        assertEquals("yes", skill.paramOrDefault(params, "present", String.class, "fallback"));
        assertEquals("fallback", skill.paramOrDefault(params, "absent", String.class, "fallback"));
    }
}
