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
package org.apache.rocketmq.dashboard.skill.skills;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.skill.Skill;
import org.apache.rocketmq.dashboard.skill.SkillResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ClusterInfoSkillTest {

    @InjectMocks
    private ClusterInfoSkill skill;

    @Mock
    private ClusterService clusterService;

    @Test
    public void testMetadata() {
        assertEquals("cluster.info", skill.getId());
        assertEquals("cluster", skill.getResourceType());
        assertEquals("info", skill.getVerb());
        assertEquals(Skill.RiskLevel.L1, skill.getRiskLevel());
    }

    @Test
    public void testExecuteOverview() {
        Map<String, Object> clusterInfo = new HashMap<>();
        clusterInfo.put("brokerServer", "broker-a");
        when(clusterService.list()).thenReturn(clusterInfo);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "overview");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(clusterInfo, result.getData());
        assertEquals("OBJECT", result.getReturnType());
    }

    @Test
    public void testExecuteBrokerConfig() {
        Properties config = new Properties();
        config.put("brokerName", "broker-a");
        when(clusterService.getBrokerConfig("127.0.0.1:10911")).thenReturn(config);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "broker-config");
        params.put("brokerAddr", "127.0.0.1:10911");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(config, result.getData());
    }

    @Test
    public void testExecuteBrokerConfigNotFound() {
        when(clusterService.getBrokerConfig("127.0.0.1:10911")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "broker-config");
        params.put("brokerAddr", "127.0.0.1:10911");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("127.0.0.1:10911"));
    }

    @Test
    public void testExecuteBrokerConfigWithoutAddrFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "broker-config");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("brokerAddr"));
    }

    @Test
    public void testExecuteUnknownActionFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "restart");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown action"));
    }

    @Test
    public void testExecuteServiceExceptionWrapped() {
        when(clusterService.list()).thenThrow(new RuntimeException("timeout"));

        Map<String, Object> params = new HashMap<>();
        params.put("action", "overview");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("timeout"));
    }
}
