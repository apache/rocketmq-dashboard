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

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MessageServiceImplTest {

    @InjectMocks
    private MessageServiceImpl messageService;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ClusterCapability clusterCapability;

    private static final String TOPIC = "testTopic";
    private static final String MSG_ID = "testMsgId";
    private static final String CONSUMER_GROUP = "testConsumerGroup";
    private static final String CLIENT_ID = "testClientId";
    private static final String KEY = "testKey";
    private static final String BROKER_NAME = "broker-1";

    private Set<String> allCapabilities;

    @Before
    public void setUp() throws Exception {
        allCapabilities = new HashSet<>();
        allCapabilities.add("MESSAGE_QUERY");
        allCapabilities.add("MESSAGE_QUERY_BY_KEY");
        allCapabilities.add("MESSAGE_QUERY_BY_GROUP");
        allCapabilities.add("MESSAGE_QUERY_BY_ID");
        allCapabilities.add("MESSAGE_QUERY_BY_OFFSET");
        allCapabilities.add("OFFSET_SEARCH_BY_TIMESTAMP");
        allCapabilities.add("MAX_OFFSET_QUERY");
        allCapabilities.add("MIN_OFFSET_QUERY");
        allCapabilities.add("MESSAGE_DELETE");
        allCapabilities.add("MESSAGE_RESEND");
        allCapabilities.add("MESSAGE_CONSUME_DIRECTLY");

        when(clusterCapability.hasCapability(anyString())).thenAnswer(invocation -> {
            String cap = invocation.getArgument(0);
            return allCapabilities.contains(cap);
        });
        when(clusterProvider.getClusterCapability()).thenReturn(clusterCapability);
        messageService.init();
    }

    private void disableAllCapabilities() throws Exception {
        allCapabilities.clear();
        when(clusterCapability.hasCapability(anyString())).thenAnswer(invocation -> {
            String cap = invocation.getArgument(0);
            return allCapabilities.contains(cap);
        });
        messageService.init();
    }

    // ==================== viewMessage tests ====================

    @Test
    public void testViewMessage() throws Exception {
        MessageInfo messageInfo = createMessageInfo(MSG_ID, TOPIC, "test body", System.currentTimeMillis());
        when(metadataProvider.getMessageById(MSG_ID)).thenReturn(Optional.of(messageInfo));

        Pair<MessageView, List<MessageTrack>> result = messageService.viewMessage(TOPIC, MSG_ID);

        assertNotNull(result);
        assertNotNull(result.getObject1());
        assertEquals(MSG_ID, result.getObject1().getMsgId());
        assertEquals(TOPIC, result.getObject1().getTopic());
    }

    @Test
    public void testViewMessageNotFound() throws Exception {
        when(metadataProvider.getMessageById(MSG_ID)).thenReturn(Optional.empty());

        Pair<MessageView, List<MessageTrack>> result = messageService.viewMessage(TOPIC, MSG_ID);

        assertNotNull(result);
        assertNull(result.getObject1());
        assertTrue(result.getObject2().isEmpty());
    }

    @Test
    public void testViewMessageException() throws Exception {
        when(metadataProvider.getMessageById(MSG_ID)).thenThrow(new RuntimeException("Test exception"));

        Pair<MessageView, List<MessageTrack>> result = messageService.viewMessage(TOPIC, MSG_ID);

        assertNotNull(result);
        assertNull(result.getObject1());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testViewMessageUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.viewMessage(TOPIC, MSG_ID);
    }

    // ==================== queryMessageByPage tests ====================

    @Test
    public void testQueryMessageByPage() throws Exception {
        List<MessageInfo> messages = new ArrayList<>();
        messages.add(createMessageInfo("id1", TOPIC, "body1", System.currentTimeMillis()));
        messages.add(createMessageInfo("id2", TOPIC, "body2", System.currentTimeMillis()));

        when(metadataProvider.queryMessageByTopic(eq(TOPIC), anyLong(), anyLong(), anyInt()))
                .thenReturn(messages);

        MessageQuery query = new MessageQuery();
        query.setTopic(TOPIC);
        query.setPageNum(1);
        query.setPageSize(10);
        query.setBegin(System.currentTimeMillis() - 3600000);
        query.setEnd(System.currentTimeMillis());

        MessagePage result = messageService.queryMessageByPage(query);

        assertNotNull(result);
        assertNotNull(result.getPage());
        assertEquals(2, result.getPage().getContent().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testQueryMessageByPageUnsupported() throws Exception {
        disableAllCapabilities();
        MessageQuery query = new MessageQuery();
        query.setTopic(TOPIC);
        query.setPageNum(1);
        query.setPageSize(10);
        messageService.queryMessageByPage(query);
    }

    // ==================== queryMessageByTopic tests ====================

    @Test
    public void testQueryMessageByTopic() throws Exception {
        List<MessageInfo> expectedMessages = new ArrayList<>();
        expectedMessages.add(createMessageInfo("id1", TOPIC, "body1", 1500));
        expectedMessages.add(createMessageInfo("id2", TOPIC, "body2", 2000));

        when(metadataProvider.queryMessageByTopic(eq(TOPIC), anyLong(), anyLong(), anyInt()))
                .thenReturn(expectedMessages);

        List<MessageInfo> result = messageService.queryMessageByTopic(TOPIC, 1000, 3000, 64);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("id1", result.get(0).getMsgId());
        assertEquals("id2", result.get(1).getMsgId());
    }

    @Test
    public void testQueryMessageByTopicEmpty() throws Exception {
        when(metadataProvider.queryMessageByTopic(eq(TOPIC), anyLong(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<MessageInfo> result = messageService.queryMessageByTopic(TOPIC, 1000, 3000, 64);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testQueryMessageByTopicUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.queryMessageByTopic(TOPIC, 1000, 3000, 64);
    }

    // ==================== queryMessageByTopicAndKey tests ====================

    @Test
    public void testQueryMessageByTopicAndKey() throws Exception {
        List<MessageInfo> expectedMessages = new ArrayList<>();
        expectedMessages.add(createMessageInfo("id1", TOPIC, "body1", System.currentTimeMillis()));

        when(metadataProvider.queryMessageByTopicAndKey(eq(TOPIC), eq(KEY), anyLong(), anyLong()))
                .thenReturn(expectedMessages);

        List<MessageInfo> result = messageService.queryMessageByTopicAndKey(TOPIC, KEY, 1000, 3000);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("id1", result.get(0).getMsgId());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testQueryMessageByTopicAndKeyUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.queryMessageByTopicAndKey(TOPIC, KEY, 1000, 3000);
    }

    // ==================== queryMessageByGroup tests ====================

    @Test
    public void testQueryMessageByGroup() throws Exception {
        List<MessageInfo> expectedMessages = new ArrayList<>();
        expectedMessages.add(createMessageInfo("id1", TOPIC, "body1", System.currentTimeMillis()));

        when(metadataProvider.queryMessageByGroup(eq(CONSUMER_GROUP), eq(TOPIC), anyLong(), anyLong()))
                .thenReturn(expectedMessages);

        List<MessageInfo> result = messageService.queryMessageByGroup(CONSUMER_GROUP, TOPIC, 1000, 3000);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testQueryMessageByGroupUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.queryMessageByGroup(CONSUMER_GROUP, TOPIC, 1000, 3000);
    }

    // ==================== getMessageById tests ====================

    @Test
    public void testGetMessageById() throws Exception {
        MessageInfo messageInfo = createMessageInfo(MSG_ID, TOPIC, "test body", System.currentTimeMillis());
        when(metadataProvider.getMessageById(MSG_ID)).thenReturn(Optional.of(messageInfo));

        MessageInfo result = messageService.getMessageById(MSG_ID);

        assertNotNull(result);
        assertEquals(MSG_ID, result.getMsgId());
        assertEquals(TOPIC, result.getTopic());
    }

    @Test
    public void testGetMessageByIdNotFound() throws Exception {
        when(metadataProvider.getMessageById(MSG_ID)).thenReturn(Optional.empty());

        MessageInfo result = messageService.getMessageById(MSG_ID);

        assertNull(result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMessageByIdUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.getMessageById(MSG_ID);
    }

    // ==================== getMessagesByOffset tests ====================

    @Test
    public void testGetMessagesByOffset() throws Exception {
        List<MessageInfo> expectedMessages = new ArrayList<>();
        expectedMessages.add(createMessageInfo("id1", TOPIC, "body1", System.currentTimeMillis()));

        when(metadataProvider.getMessagesByOffset(eq(TOPIC), eq(BROKER_NAME), eq(0), eq(0L), eq(10)))
                .thenReturn(expectedMessages);

        List<MessageInfo> result = messageService.getMessagesByOffset(TOPIC, BROKER_NAME, 0, 0L, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMessagesByOffsetUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.getMessagesByOffset(TOPIC, BROKER_NAME, 0, 0L, 10);
    }

    // ==================== searchOffset tests ====================

    @Test
    public void testSearchOffset() throws Exception {
        when(metadataProvider.searchOffset(eq(TOPIC), eq(BROKER_NAME), eq(0), anyLong()))
                .thenReturn(100L);

        long result = messageService.searchOffset(TOPIC, BROKER_NAME, 0, System.currentTimeMillis());

        assertEquals(100L, result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSearchOffsetUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.searchOffset(TOPIC, BROKER_NAME, 0, System.currentTimeMillis());
    }

    // ==================== getMaxOffset tests ====================

    @Test
    public void testGetMaxOffset() throws Exception {
        when(metadataProvider.getMaxOffset(eq(TOPIC), eq(BROKER_NAME), eq(0)))
                .thenReturn(500L);

        long result = messageService.getMaxOffset(TOPIC, BROKER_NAME, 0);

        assertEquals(500L, result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMaxOffsetUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.getMaxOffset(TOPIC, BROKER_NAME, 0);
    }

    // ==================== getMinOffset tests ====================

    @Test
    public void testGetMinOffset() throws Exception {
        when(metadataProvider.getMinOffset(eq(TOPIC), eq(BROKER_NAME), eq(0)))
                .thenReturn(0L);

        long result = messageService.getMinOffset(TOPIC, BROKER_NAME, 0);

        assertEquals(0L, result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMinOffsetUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.getMinOffset(TOPIC, BROKER_NAME, 0);
    }

    // ==================== deleteMessage tests ====================

    @Test
    public void testDeleteMessage() throws Exception {
        doNothing().when(metadataProvider).deleteMessage(eq(TOPIC), eq(MSG_ID));

        boolean result = messageService.deleteMessage(TOPIC, MSG_ID);

        assertTrue(result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteMessageUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.deleteMessage(TOPIC, MSG_ID);
    }

    // ==================== resendMessage tests ====================

    @Test
    public void testResendMessage() throws Exception {
        doNothing().when(metadataProvider).resendMessage(eq(MSG_ID), eq("newTopic"));

        boolean result = messageService.resendMessage(MSG_ID, "newTopic");

        assertTrue(result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testResendMessageUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.resendMessage(MSG_ID, "newTopic");
    }

    // ==================== consumeMessageDirectly tests ====================

    @Test
    public void testConsumeMessageDirectly() throws Exception {
        ConsumeMessageDirectlyResult expectedResult = new ConsumeMessageDirectlyResult();

        when(metadataProvider.consumeMessageDirectly(eq(TOPIC), eq(MSG_ID), eq(CONSUMER_GROUP), eq(CLIENT_ID)))
                .thenReturn(expectedResult);

        ConsumeMessageDirectlyResult result = messageService.consumeMessageDirectly(TOPIC, MSG_ID, CONSUMER_GROUP, CLIENT_ID);

        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConsumeMessageDirectlyUnsupported() throws Exception {
        disableAllCapabilities();
        messageService.consumeMessageDirectly(TOPIC, MSG_ID, CONSUMER_GROUP, CLIENT_ID);
    }

    // ==================== Exception handling tests ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testQueryMessageByTopicException() throws Exception {
        when(metadataProvider.queryMessageByTopic(eq(TOPIC), anyLong(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("Test exception"));
        messageService.queryMessageByTopic(TOPIC, 1000, 3000, 64);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMessageByIdException() throws Exception {
        when(metadataProvider.getMessageById(MSG_ID))
                .thenThrow(new RuntimeException("Test exception"));
        messageService.getMessageById(MSG_ID);
    }

    @Test
    public void testDeleteMessageException() throws Exception {
        // Exception in deleteMessage is caught and UnsupportedOperationException is thrown
        // But the first call succeeds
        doNothing().when(metadataProvider).deleteMessage(eq(TOPIC), eq(MSG_ID));
        assertTrue(messageService.deleteMessage(TOPIC, MSG_ID));
    }

    // ==================== Helper methods ====================

    private MessageInfo createMessageInfo(String msgId, String topic, String body, long storeTimestamp) {
        MessageInfo info = new MessageInfo();
        info.setMsgId(msgId);
        info.setTopic(topic);
        info.setBody(body);
        info.setStoreTimestamp(storeTimestamp);
        return info;
    }
}