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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.service.ConsumerService;
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
public class ConsumerQuerySkillTest {

    @InjectMocks
    private ConsumerQuerySkill skill;

    @Mock
    private ConsumerService consumerService;

    @Test
    public void testMetadata() {
        assertEquals("consumer.query", skill.getId());
        assertEquals("group", skill.getResourceType());
        assertEquals("query", skill.getVerb());
        assertEquals(Skill.RiskLevel.L1, skill.getRiskLevel());
    }

    @Test
    public void testExecuteList() {
        List<ConsumerGroupInfo> groups = Arrays.asList(new ConsumerGroupInfo(), new ConsumerGroupInfo());
        when(consumerService.listConsumerGroups()).thenReturn(groups);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(groups, result.getData());
        assertEquals("LIST", result.getReturnType());
    }

    @Test
    public void testExecuteDetail() {
        ConsumerGroupInfo info = new ConsumerGroupInfo();
        when(consumerService.getConsumerGroup("groupA")).thenReturn(info);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        params.put("group", "groupA");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(info, result.getData());
        assertEquals("OBJECT", result.getReturnType());
    }

    @Test
    public void testExecuteDetailNotFound() {
        when(consumerService.getConsumerGroup("missing")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        params.put("group", "missing");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("missing"));
    }

    @Test
    public void testExecuteDetailWithoutGroupFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("group"));
    }

    @Test
    public void testExecuteUnknownActionFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "delete");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown action"));
    }

    @Test
    public void testExecuteServiceExceptionWrapped() {
        when(consumerService.listConsumerGroups()).thenThrow(new RuntimeException("broker offline"));

        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("broker offline"));
    }
}
