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

import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.apache.rocketmq.dashboard.model.request.TopicTypeList;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.request.SendTopicMessageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.lenient;

@RunWith(MockitoJUnitRunner.class)
public class TopicServiceImplTest {

    @InjectMocks
    @Spy
    private TopicServiceImpl topicService;

    @Mock
    private MQAdminExt mqAdminExt;
    
    @Mock
    private RMQConfigure configure;

    @Before
    public void setUp() {
        // Setup common mocks
        when(configure.getNamesrvAddr()).thenReturn("localhost:9876");
        
        // Use lenient() to prevent the unnecessary stubbing error
        lenient().when(configure.isUseTLS()).thenReturn(false);
    }

    @Test
    public void testExamineAllTopicType() throws Exception {
        // Create mock TopicList with different types of topics
        TopicList topicList = new TopicList();
        Set<String> topicSet = new HashSet<>();
        topicSet.add("normalTopic");
        topicSet.add("%RETRY%someGroup");
        topicSet.add("%DLQ%someGroup");
        topicSet.add("%SYS%sysTopic");
        topicList.setTopicList(topicSet);
        
        // Mock fetchAllTopicList to return our test topics
        doReturn(topicList).when(topicService).fetchAllTopicList(anyBoolean(), anyBoolean());
        
        // Mock examineTopicConfig for the normal topic
        TopicConfigInfo configInfo = new TopicConfigInfo();
        configInfo.setMessageType("NORMAL");
        List<TopicConfigInfo> topicConfigInfos = new ArrayList<>();
        topicConfigInfos.add(configInfo);
        doReturn(topicConfigInfos).when(topicService).examineTopicConfig(anyString());
        
        // Call the method being tested
        TopicTypeList result = topicService.examineAllTopicType();
        
        // Verify the results
        Assert.assertNotNull(result);
        Assert.assertEquals(4, result.getTopicNameList().size());
        Assert.assertEquals(4, result.getMessageTypeList().size());
        
        // Verify that the topics contain the expected names and types
        // Note: the actual order might be different due to sorting in the method
        // So we're checking that all expected items are included
        Assert.assertTrue(result.getTopicNameList().contains("normalTopic"));
        Assert.assertTrue(result.getTopicNameList().contains("%RETRY%someGroup"));
        Assert.assertTrue(result.getTopicNameList().contains("%DLQ%someGroup"));
        Assert.assertTrue(result.getTopicNameList().contains("%SYS%sysTopic"));
        
        // Verify message types
        Assert.assertTrue(result.getMessageTypeList().contains("NORMAL"));
        Assert.assertTrue(result.getMessageTypeList().contains("RETRY"));
        Assert.assertTrue(result.getMessageTypeList().contains("DELAY")); 
        Assert.assertTrue(result.getMessageTypeList().contains("SYSTEM"));
    }

    @Test
    public void testSendTopicMessageRequestNormal() throws Exception {
        // Prepare test data
        SendTopicMessageRequest request = new SendTopicMessageRequest();
        request.setTopic("testTopic");
        request.setTag("testTag");
        request.setKey("testKey");
        request.setMessageBody("Hello RocketMQ");
        request.setTraceEnabled(false);
        
        // Mock the topic config
        TopicConfigInfo configInfo = new TopicConfigInfo();
        configInfo.setMessageType(TopicMessageType.NORMAL.name());
        List<TopicConfigInfo> topicConfigInfos = new ArrayList<>();
        topicConfigInfos.add(configInfo);
        doReturn(topicConfigInfos).when(topicService).examineTopicConfig("testTopic");
        
        // Mock ACL disabled
        when(configure.isACLEnabled()).thenReturn(false);
        
        // Mock producer
        DefaultMQProducer mockProducer = mock(DefaultMQProducer.class);
        doReturn(mockProducer).when(topicService).buildDefaultMQProducer(any(), any(), anyBoolean());
        
        // Mock send result
        SendResult expectedResult = new SendResult();
        expectedResult.setSendStatus(SendStatus.SEND_OK);
        when(mockProducer.send(any(Message.class))).thenReturn(expectedResult);
        
        // Call the method
        SendResult result = topicService.sendTopicMessageRequest(request);
        
        // Verify
        Assert.assertEquals(expectedResult, result);
        
        // Verify producer configuration and message sending
        verify(mockProducer).setInstanceName(anyString());
        verify(mockProducer).setNamesrvAddr("localhost:9876");
        verify(mockProducer).start();
        
        // Verify message content
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockProducer).send(messageCaptor.capture());
        Message sentMessage = messageCaptor.getValue();
        Assert.assertEquals("testTopic", sentMessage.getTopic());
        Assert.assertEquals("testTag", sentMessage.getTags());
        Assert.assertEquals("testKey", sentMessage.getKeys());
        Assert.assertEquals("Hello RocketMQ", new String(sentMessage.getBody()));
        
        // Verify producer shutdown
        verify(mockProducer).shutdown();
    }
    
    @Test
    public void testSendTopicMessageRequestTransaction() throws Exception {
        // Prepare test data
        SendTopicMessageRequest request = new SendTopicMessageRequest();
        request.setTopic("testTopic");
        request.setTag("testTag");
        request.setKey("testKey");
        request.setMessageBody("Hello RocketMQ");
        request.setTraceEnabled(false);
        
        // Mock the topic config
        TopicConfigInfo configInfo = new TopicConfigInfo();
        configInfo.setMessageType(TopicMessageType.TRANSACTION.name());
        List<TopicConfigInfo> topicConfigInfos = new ArrayList<>();
        topicConfigInfos.add(configInfo);
        doReturn(topicConfigInfos).when(topicService).examineTopicConfig("testTopic");
        
        // Mock ACL disabled
        when(configure.isACLEnabled()).thenReturn(false);
        
        // Mock producer
        TransactionMQProducer mockProducer = mock(TransactionMQProducer.class);
        doReturn(mockProducer).when(topicService).buildTransactionMQProducer(any(), any(), anyBoolean());
        
        // Mock send result - use org.apache.rocketmq.client.producer.TransactionSendResult instead of SendResult
        org.apache.rocketmq.client.producer.TransactionSendResult expectedResult = new org.apache.rocketmq.client.producer.TransactionSendResult();
        expectedResult.setSendStatus(SendStatus.SEND_OK);
        when(mockProducer.sendMessageInTransaction(any(Message.class), isNull())).thenReturn(expectedResult);
        
        // Call the method
        SendResult result = topicService.sendTopicMessageRequest(request);
        
        // Verify
        Assert.assertEquals(expectedResult, result);
        
        // Verify producer configuration and message sending
        verify(mockProducer).setInstanceName(anyString());
        verify(mockProducer).setNamesrvAddr("localhost:9876");
        verify(mockProducer).setTransactionListener(any());
        verify(mockProducer).start();
        
        // Verify message content
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mockProducer).sendMessageInTransaction(messageCaptor.capture(), isNull());
        Message sentMessage = messageCaptor.getValue();
        Assert.assertEquals("testTopic", sentMessage.getTopic());
        Assert.assertEquals("testTag", sentMessage.getTags());
        Assert.assertEquals("testKey", sentMessage.getKeys());
        Assert.assertEquals("Hello RocketMQ", new String(sentMessage.getBody()));
        
        // Verify producer shutdown
        verify(mockProducer).shutdown();
    }
    
    @Test
    public void testSendTopicMessageRequestWithACLEnabled() throws Exception {
        // Prepare test data
        SendTopicMessageRequest request = new SendTopicMessageRequest();
        request.setTopic("testTopic");
        request.setTag("testTag");
        request.setKey("testKey");
        request.setMessageBody("Hello RocketMQ");
        request.setTraceEnabled(false);
        
        // Mock the topic config
        TopicConfigInfo configInfo = new TopicConfigInfo();
        configInfo.setMessageType(TopicMessageType.NORMAL.name());
        List<TopicConfigInfo> topicConfigInfos = new ArrayList<>();
        topicConfigInfos.add(configInfo);
        doReturn(topicConfigInfos).when(topicService).examineTopicConfig("testTopic");
        
        // Mock ACL enabled
        when(configure.isACLEnabled()).thenReturn(true);
        when(configure.getAccessKey()).thenReturn("testAccessKey");
        when(configure.getSecretKey()).thenReturn("testSecretKey");
        
        // Mock producer
        DefaultMQProducer mockProducer = mock(DefaultMQProducer.class);
        doReturn(mockProducer).when(topicService).buildDefaultMQProducer(any(), any(AclClientRPCHook.class), anyBoolean());
        
        // Mock send result
        SendResult expectedResult = new SendResult();
        expectedResult.setSendStatus(SendStatus.SEND_OK);
        when(mockProducer.send(any(Message.class))).thenReturn(expectedResult);
        
        // Call the method
        SendResult result = topicService.sendTopicMessageRequest(request);
        
        // Verify
        Assert.assertEquals(expectedResult, result);
        
        // Since we can't directly verify the AclClientRPCHook content, we verify that build was called with non-null hook
        verify(topicService).buildDefaultMQProducer(any(), any(AclClientRPCHook.class), eq(false));
        
        // Verify producer methods
        verify(mockProducer).start();
        verify(mockProducer).send(any(Message.class));
        verify(mockProducer).shutdown();
    }
    
    @Test
    public void testSendTopicMessageRequestWithTraceEnabled() throws Exception {
        // Prepare test data
        SendTopicMessageRequest request = new SendTopicMessageRequest();
        request.setTopic("testTopic");
        request.setTag("testTag");
        request.setKey("testKey");
        request.setMessageBody("Hello RocketMQ");
        request.setTraceEnabled(true); // Enable tracing
        
        // Mock the topic config
        TopicConfigInfo configInfo = new TopicConfigInfo();
        configInfo.setMessageType(TopicMessageType.NORMAL.name());
        List<TopicConfigInfo> topicConfigInfos = new ArrayList<>();
        topicConfigInfos.add(configInfo);
        doReturn(topicConfigInfos).when(topicService).examineTopicConfig("testTopic");
        
        // Mock ACL disabled
        when(configure.isACLEnabled()).thenReturn(false);
        
        // Mock producer
        DefaultMQProducer mockProducer = mock(DefaultMQProducer.class);
        doReturn(mockProducer).when(topicService).buildDefaultMQProducer(any(), any(), eq(true));
        
        // Cannot mock waitSendTraceFinish as it's private
        // doNothing().when(topicService).waitSendTraceFinish(any(DefaultMQProducer.class), eq(true));
        
        // Mock send result
        SendResult expectedResult = new SendResult();
        expectedResult.setSendStatus(SendStatus.SEND_OK);
        when(mockProducer.send(any(Message.class))).thenReturn(expectedResult);
        
        // Call the method
        SendResult result = topicService.sendTopicMessageRequest(request);
        
        // Verify
        Assert.assertEquals(expectedResult, result);
        
        // Verify that buildDefaultMQProducer was called with traceEnabled=true
        verify(topicService).buildDefaultMQProducer(any(), any(), eq(true));
        
        // Cannot verify waitSendTraceFinish as it's private
        // verify(topicService).waitSendTraceFinish(mockProducer, true);
    }
} 