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

import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillParameter;
import org.apache.rocketmq.dashboard.skill.SkillRegistry;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CompatibilityAliasControllerTest {

    @InjectMocks
    private CompatibilityAliasController compatibilityAliasController;

    @Mock
    private SkillRegistry skillRegistry;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MQAdminExt mqAdminExt;

    private Skill mockSkill(String id, Skill.RiskLevel riskLevel, List<SkillParameter> parameters) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(id);
        when(skill.getName()).thenReturn("name-" + id);
        when(skill.getDescription()).thenReturn("desc");
        when(skill.getResourceType()).thenReturn("topic");
        when(skill.getVerb()).thenReturn("query");
        when(skill.getRiskLevel()).thenReturn(riskLevel);
        when(skill.isAvailable()).thenReturn(true);
        when(skill.getParameters()).thenReturn(parameters);
        return skill;
    }

    // ==================== listSkills ====================

    @Test
    public void testListSkillsEmpty() {
        when(skillRegistry.getAllSkills()).thenReturn(Collections.emptyList());

        Map<String, Object> result = compatibilityAliasController.listSkills();
        assertEquals(0, result.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byRiskLevel = (Map<String, Long>) result.get("byRiskLevel");
        assertEquals(Long.valueOf(0L), byRiskLevel.get("L1"));
        assertEquals(Long.valueOf(0L), byRiskLevel.get("L2"));
        assertEquals(Long.valueOf(0L), byRiskLevel.get("L3"));
    }

    @Test
    public void testListSkillsWithParameters() {
        SkillParameter paramWithAllowed = mock(SkillParameter.class);
        when(paramWithAllowed.getName()).thenReturn("mode");
        when(paramWithAllowed.getType()).thenReturn("string");
        when(paramWithAllowed.isRequired()).thenReturn(true);
        when(paramWithAllowed.getDescription()).thenReturn("mode param");
        when(paramWithAllowed.getDefaultValue()).thenReturn("fast");
        when(paramWithAllowed.getAllowedValues()).thenReturn(Arrays.asList("fast", "slow"));

        SkillParameter paramNoAllowed = mock(SkillParameter.class);
        when(paramNoAllowed.getName()).thenReturn("topic");
        when(paramNoAllowed.getType()).thenReturn("string");
        when(paramNoAllowed.isRequired()).thenReturn(false);
        when(paramNoAllowed.getDescription()).thenReturn("topic name");
        when(paramNoAllowed.getDefaultValue()).thenReturn(null);
        when(paramNoAllowed.getAllowedValues()).thenReturn(null);

        Skill l1Skill = mockSkill("topic.query", Skill.RiskLevel.L1,
            Arrays.asList(paramWithAllowed, paramNoAllowed));
        Skill l2Skill = mockSkill("topic.create", Skill.RiskLevel.L2, null);
        Skill l3Skill = mockSkill("topic.delete", Skill.RiskLevel.L3, Collections.emptyList());
        when(skillRegistry.getAllSkills()).thenReturn(Arrays.asList(l1Skill, l2Skill, l3Skill));

        Map<String, Object> result = compatibilityAliasController.listSkills();
        assertEquals(3, result.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills = (List<Map<String, Object>>) result.get("skills");
        assertEquals(3, skills.size());

        Map<String, Object> first = skills.get(0);
        assertEquals("topic.query", first.get("id"));
        assertEquals("L1", first.get("riskLevel"));
        assertEquals(Boolean.TRUE, first.get("available"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) first.get("parameters");
        assertEquals(2, params.size());
        assertEquals("mode", params.get(0).get("name"));
        assertTrue(params.get(0).containsKey("allowedValues"));
        assertTrue(!params.get(1).containsKey("allowedValues"));

        // Skill with null parameter list serializes to an empty list
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nullParams = (List<Map<String, Object>>) skills.get(1).get("parameters");
        assertEquals(0, nullParams.size());

        @SuppressWarnings("unchecked")
        Map<String, Long> byRiskLevel = (Map<String, Long>) result.get("byRiskLevel");
        assertEquals(Long.valueOf(1L), byRiskLevel.get("L1"));
        assertEquals(Long.valueOf(1L), byRiskLevel.get("L2"));
        assertEquals(Long.valueOf(1L), byRiskLevel.get("L3"));
    }

    // ==================== queryBrokerStatus ====================

    @Test
    public void testQueryBrokerStatusBlankAddr() {
        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus(" ");
        assertEquals("brokerAddr parameter is required", result.get("error"));
    }

    @Test
    public void testQueryBrokerStatusNullAddr() {
        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus(null);
        assertEquals("brokerAddr parameter is required", result.get("error"));
    }

    @Test
    public void testQueryBrokerStatusSuccess() throws Exception {
        Properties config = new Properties();
        config.setProperty("brokerName", "broker-a");
        when(clusterService.getBrokerConfig("127.0.0.1:10911")).thenReturn(config);

        KVTable kvTable = new KVTable();
        HashMap<String, String> table = new HashMap<>();
        table.put("putTps", "100.0");
        kvTable.setTable(table);
        when(mqAdminExt.fetchBrokerRuntimeStats("127.0.0.1:10911")).thenReturn(kvTable);

        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus(" 127.0.0.1:10911 ");
        assertEquals(config, result.get("brokerConfig"));
        @SuppressWarnings("unchecked")
        Map<String, String> stats = (Map<String, String>) result.get("runtimeStats");
        assertEquals("100.0", stats.get("putTps"));
        assertEquals("127.0.0.1:10911", result.get("brokerAddr"));
        assertTrue(!result.containsKey("error"));
    }

    @Test
    public void testQueryBrokerStatusNullKvTable() throws Exception {
        when(clusterService.getBrokerConfig("addr")).thenReturn(new Properties());
        when(mqAdminExt.fetchBrokerRuntimeStats("addr")).thenReturn(null);

        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus("addr");
        assertNull(result.get("runtimeStats"));
        assertNotNull(result.get("brokerConfig"));
    }

    @Test
    public void testQueryBrokerStatusNullTableInKvTable() throws Exception {
        when(clusterService.getBrokerConfig("addr")).thenReturn(new Properties());
        KVTable kvTable = new KVTable();
        kvTable.setTable(null);
        when(mqAdminExt.fetchBrokerRuntimeStats("addr")).thenReturn(kvTable);

        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus("addr");
        assertNull(result.get("runtimeStats"));
    }

    @Test
    public void testQueryBrokerStatusBothFail() throws Exception {
        when(clusterService.getBrokerConfig("addr"))
            .thenThrow(new RuntimeException("config unreachable"));
        when(mqAdminExt.fetchBrokerRuntimeStats("addr"))
            .thenThrow(new RuntimeException("stats unreachable"));

        Map<String, Object> result = compatibilityAliasController.queryBrokerStatus("addr");
        assertNull(result.get("brokerConfig"));
        assertEquals("config unreachable", result.get("brokerConfigError"));
        assertNull(result.get("runtimeStats"));
        assertEquals("stats unreachable", result.get("runtimeStatsError"));
        assertEquals("addr", result.get("brokerAddr"));
    }
}
