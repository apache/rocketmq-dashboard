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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SkillRegistryTest {

    /**
     * Simple configurable stub skill.
     */
    private static class StubSkill extends AbstractSkill {
        private final String id;
        private final String resourceType;
        private final RiskLevel riskLevel;
        private final boolean available;
        final AtomicBoolean initialized = new AtomicBoolean(false);
        final AtomicBoolean destroyed = new AtomicBoolean(false);

        StubSkill(String id, String resourceType, RiskLevel riskLevel, boolean available) {
            this.id = id;
            this.resourceType = resourceType;
            this.riskLevel = riskLevel;
            this.available = available;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getName() {
            return "Stub " + id;
        }

        @Override
        public String getDescription() {
            return "stub skill " + id;
        }

        @Override
        public List<SkillParameter> getParameters() {
            return Collections.emptyList();
        }

        @Override
        public String getResourceType() {
            return resourceType;
        }

        @Override
        public String getVerb() {
            return "query";
        }

        @Override
        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void initialize() {
            initialized.set(true);
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }

        @Override
        public SkillResult execute(Map<String, Object> parameters) {
            return SkillResult.successVoid();
        }
    }

    private SkillRegistry registry;

    @Before
    public void setUp() {
        registry = new SkillRegistry();
    }

    @Test
    public void testRegisterAndGetSkill() {
        StubSkill skill = new StubSkill("topic.query", "topic", Skill.RiskLevel.L1, true);
        registry.registerSkill(skill);

        assertTrue(skill.initialized.get());
        assertTrue(registry.hasSkill("topic.query"));
        assertEquals(1, registry.getSkillCount());
        assertEquals(skill, registry.getSkill("topic.query"));
    }

    @Test
    public void testRegisterNullSkillRejected() {
        try {
            registry.registerSkill(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"));
        }
    }

    @Test
    public void testRegisterBlankIdRejected() {
        StubSkill skill = new StubSkill("  ", "topic", Skill.RiskLevel.L1, true);
        try {
            registry.registerSkill(skill);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("ID"));
        }
    }

    @Test
    public void testRegisterDuplicateIdRejected() {
        registry.registerSkill(new StubSkill("dup", "topic", Skill.RiskLevel.L1, true));
        try {
            registry.registerSkill(new StubSkill("dup", "topic", Skill.RiskLevel.L1, true));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("already registered"));
        }
    }

    @Test
    public void testUnregisterSkill() {
        StubSkill skill = new StubSkill("gone", "topic", Skill.RiskLevel.L1, true);
        registry.registerSkill(skill);

        assertTrue(registry.unregisterSkill("gone"));
        assertTrue(skill.destroyed.get());
        assertFalse(registry.hasSkill("gone"));
        assertNull(registry.getSkill("gone"));
    }

    @Test
    public void testUnregisterUnknownSkillReturnsFalse() {
        assertFalse(registry.unregisterSkill("does-not-exist"));
    }

    @Test
    public void testGetAvailableSkillsFiltersUnavailable() {
        registry.registerSkill(new StubSkill("on", "topic", Skill.RiskLevel.L1, true));
        registry.registerSkill(new StubSkill("off", "topic", Skill.RiskLevel.L1, false));

        List<Skill> available = registry.getAvailableSkills();
        assertEquals(1, available.size());
        assertEquals("on", available.get(0).getId());
        assertEquals(2, registry.getAllSkills().size());
    }

    @Test
    public void testGetSkillsByResourceType() {
        registry.registerSkill(new StubSkill("t1", "topic", Skill.RiskLevel.L1, true));
        registry.registerSkill(new StubSkill("g1", "group", Skill.RiskLevel.L1, true));

        List<Skill> topicSkills = registry.getSkillsByResourceType("topic");
        assertEquals(1, topicSkills.size());
        assertEquals("t1", topicSkills.get(0).getId());
    }

    @Test
    public void testGetSkillsByRiskLevel() {
        registry.registerSkill(new StubSkill("safe", "topic", Skill.RiskLevel.L1, true));
        registry.registerSkill(new StubSkill("danger", "topic", Skill.RiskLevel.L3, true));

        List<Skill> dangerous = registry.getSkillsByRiskLevel(Skill.RiskLevel.L3);
        assertEquals(1, dangerous.size());
        assertEquals("danger", dangerous.get(0).getId());
    }

    @Test
    public void testGetSkillMetadata() {
        registry.registerSkill(new StubSkill("meta", "topic", Skill.RiskLevel.L2, true));

        List<Map<String, Object>> metadata = registry.getSkillMetadata();
        assertEquals(1, metadata.size());
        Map<String, Object> meta = metadata.get(0);
        assertEquals("meta", meta.get("id"));
        assertEquals("Stub meta", meta.get("name"));
        assertEquals("topic", meta.get("resourceType"));
        assertEquals("query", meta.get("verb"));
        assertEquals("L2", meta.get("riskLevel"));
        assertEquals(Boolean.TRUE, meta.get("available"));
    }

    @Test
    public void testDestroyAllClearsRegistry() {
        StubSkill skill = new StubSkill("bye", "topic", Skill.RiskLevel.L1, true);
        registry.registerSkill(skill);

        registry.destroyAll();
        assertTrue(skill.destroyed.get());
        assertEquals(0, registry.getSkillCount());
    }

    @Test
    public void testOnApplicationEventDiscoversSkillBeans() {
        ApplicationContext context = mock(ApplicationContext.class);
        StubSkill skill = new StubSkill("auto", "topic", Skill.RiskLevel.L1, true);
        when(context.getBeansOfType(Skill.class))
                .thenReturn(Collections.singletonMap("autoSkill", skill));
        ReflectionTestUtils.setField(registry, "applicationContext", context);

        registry.onApplicationEvent(new ContextRefreshedEvent(context));
        assertTrue(registry.hasSkill("auto"));

        // A second refresh must not attempt re-registration
        registry.onApplicationEvent(new ContextRefreshedEvent(context));
        assertEquals(1, registry.getSkillCount());
    }

    @Test
    public void testOnApplicationEventWithoutContextIsNoop() {
        registry.onApplicationEvent(new ContextRefreshedEvent(mock(ApplicationContext.class)));
        assertEquals(0, registry.getSkillCount());
    }
}
