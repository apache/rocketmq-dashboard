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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.service.MessageService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessageQuerySkillTest {

    @InjectMocks
    private MessageQuerySkill skill;

    @Mock
    private MessageService messageService;

    @Test
    public void testMetadata() {
        assertEquals("message.query", skill.getId());
        assertEquals("message", skill.getResourceType());
        assertEquals("query", skill.getVerb());
        assertEquals(Skill.RiskLevel.L1, skill.getRiskLevel());
        assertEquals(6, skill.getParameters().size());
    }

    @Test
    public void testExecuteById() {
        MessageInfo message = new MessageInfo();
        when(messageService.getMessageById("MSG001")).thenReturn(message);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-id");
        params.put("msgId", "MSG001");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(message, result.getData());
        assertEquals("OBJECT", result.getReturnType());
    }

    @Test
    public void testExecuteByIdNotFound() {
        when(messageService.getMessageById("MSG404")).thenReturn(null);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-id");
        params.put("msgId", "MSG404");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("MSG404"));
    }

    @Test
    public void testExecuteByIdWithoutMsgIdFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-id");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("msgId"));
    }

    @Test
    public void testExecuteByTopic() {
        List<MessageInfo> messages = Collections.singletonList(new MessageInfo());
        when(messageService.queryMessageByTopic("topicA", 1000L, 2000L)).thenReturn(messages);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-topic");
        params.put("topic", "topicA");
        params.put("beginTime", 1000L);
        params.put("endTime", 2000L);
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(messages, result.getData());
        assertEquals("LIST", result.getReturnType());
    }

    @Test
    public void testExecuteByTopicWithoutTimeRangeFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-topic");
        params.put("topic", "topicA");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("beginTime"));
    }

    @Test
    public void testExecuteByKeyWithTimeRange() {
        List<MessageInfo> messages = Collections.singletonList(new MessageInfo());
        when(messageService.queryMessageByTopicAndKey("topicA", "orderId", 1000L, 2000L)).thenReturn(messages);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-key");
        params.put("topic", "topicA");
        params.put("key", "orderId");
        params.put("beginTime", 1000L);
        params.put("endTime", 2000L);
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(messages, result.getData());
        verify(messageService).queryMessageByTopicAndKey("topicA", "orderId", 1000L, 2000L);
    }

    @Test
    public void testExecuteByKeyWithoutTimeRangeUsesDefaultWindow() {
        List<MessageInfo> messages = Collections.singletonList(new MessageInfo());
        when(messageService.queryMessageByTopicAndKey("topicA", "orderId")).thenReturn(messages);

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-key");
        params.put("topic", "topicA");
        params.put("key", "orderId");
        SkillResult result = skill.execute(params);

        assertTrue(result.isSuccess());
        assertEquals(messages, result.getData());
    }

    @Test
    public void testExecuteByKeyWithoutKeyFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-key");
        params.put("topic", "topicA");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("key"));
    }

    @Test
    public void testExecuteUnknownActionFails() {
        Map<String, Object> params = new HashMap<>();
        params.put("action", "resend");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown action"));
    }

    @Test
    public void testExecuteServiceExceptionWrapped() {
        when(messageService.getMessageById("MSG001")).thenThrow(new RuntimeException("store error"));

        Map<String, Object> params = new HashMap<>();
        params.put("action", "by-id");
        params.put("msgId", "MSG001");
        SkillResult result = skill.execute(params);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("store error"));
    }
}
