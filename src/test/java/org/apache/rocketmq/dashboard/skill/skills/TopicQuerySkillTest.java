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
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.service.TopicService;
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
public class TopicQuerySkillTest {

    @InjectMocks
    private TopicQuerySkill skill;

    @Mock
    private TopicService topicService;

    @Test
    public void testMetadata() {
        assertEquals("topic.query", skill.getId());
        assertEquals("topic", skill.getResourceType());
        assertEquals("query", skill.getVerb());
        assertEquals(Skill.RiskLevel.L1, skill.getRiskLevel());
        assertEquals(2, skill.getParameters().size());
    }

    @Test
    public void testExecuteList() {
        List<String> topics = Arrays.asList("topicA", "topicB");
        when(topicService.getTopicList()).thenReturn(topics);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(topics, result.getData());
        assertEquals("LIST", result.getReturnType());
    }

    @Test
    public void testExecuteDetail() {
        TopicInfo info = new TopicInfo();
        info.setTopicName("topicA");
        when(topicService.getTopicInfo("topicA")).thenReturn(info);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        params.put("topic", "topicA");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(info, result.getData());
        assertEquals("OBJECT", result.getReturnType());
    }

    @Test
    public void testExecuteDetailNotFound() {
        when(topicService.getTopicInfo("missing")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        params.put("topic", "missing");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("missing"));
    }

    @Test
    public void testExecuteDetailWithoutTopicFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "detail");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("topic"));
    }

    @Test
    public void testExecuteMissingActionFails() {
        SkillResult result = skill.execute(new HashMap<>());
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("action"));
    }

    @Test
    public void testExecuteUnknownActionFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "purge");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown action"));
    }

    @Test
    public void testExecuteServiceExceptionWrapped() {
        when(topicService.getTopicList()).thenThrow(new RuntimeException("nameserver down"));

        Map<String, Object> params = new HashMap<>();
        params.put("action", "list");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("nameserver down"));
    }
}
