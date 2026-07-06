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

import org.apache.rocketmq.dashboard.BaseTest;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TopicServiceImplTest extends BaseTest {

    @InjectMocks
    @Spy
    private TopicServiceImpl topicService;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private MetadataProvider metadataProvider;

    @Before
    public void setUp() {
        when(rmqConfigure.getNamesrvAddr()).thenReturn("localhost:9876");
        lenient().when(rmqConfigure.isUseTLS()).thenReturn(false);
    }

    @Test
    public void testGetTopicList() throws Exception {
        // Prepare test data
        List<TopicInfo> topicInfos = new ArrayList<>();
        TopicInfo topic1 = new TopicInfo();
        topic1.setTopicName("test_topic1");
        topic1.setTopicType(TopicType.NORMAL);
        topicInfos.add(topic1);

        TopicInfo topic2 = new TopicInfo();
        topic2.setTopicName("test_topic2");
        topic2.setTopicType(TopicType.FIFO);
        topicInfos.add(topic2);

        // Mock metadataProvider
        when(metadataProvider.listTopics(any(Optional.class))).thenReturn(topicInfos);

        // Call the method
        List<String> result = topicService.getTopicList();

        // Verify
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("test_topic1"));
        Assert.assertTrue(result.contains("test_topic2"));
        verify(metadataProvider, times(1)).listTopics(any(Optional.class));
    }

    @Test
    public void testGetTopicInfo() throws Exception {
        // Prepare test data
        TopicInfo topicInfo = new TopicInfo();
        topicInfo.setTopicName("test_topic");
        topicInfo.setTopicType(TopicType.NORMAL);
        topicInfo.setReadQueueNums(8);
        topicInfo.setWriteQueueNums(8);

        // Mock metadataProvider
        when(metadataProvider.getTopic(eq("test_topic"), any(Optional.class)))
                .thenReturn(Optional.of(topicInfo));

        // Call the method
        TopicInfo result = topicService.getTopicInfo("test_topic");

        // Verify
        Assert.assertNotNull(result);
        Assert.assertEquals("test_topic", result.getTopicName());
        Assert.assertEquals(TopicType.NORMAL, result.getTopicType());
        Assert.assertEquals(8, result.getReadQueueNums());
        Assert.assertEquals(8, result.getWriteQueueNums());
        verify(metadataProvider, times(1)).getTopic(eq("test_topic"), any(Optional.class));
    }

    @Test
    public void testGetTopicInfoNotFound() throws Exception {
        // Mock metadataProvider returns empty
        when(metadataProvider.getTopic(eq("nonexistent"), any(Optional.class)))
                .thenReturn(Optional.empty());

        // Call the method
        TopicInfo result = topicService.getTopicInfo("nonexistent");

        // Verify
        Assert.assertNull(result);
        verify(metadataProvider, times(1)).getTopic(eq("nonexistent"), any(Optional.class));
    }

    @Test
    public void testIsTopicExist() throws Exception {
        // Prepare test data
        TopicInfo topicInfo = new TopicInfo();
        topicInfo.setTopicName("existing_topic");

        // Mock metadataProvider
        when(metadataProvider.getTopic(eq("existing_topic"), any(Optional.class)))
                .thenReturn(Optional.of(topicInfo));
        when(metadataProvider.getTopic(eq("nonexistent_topic"), any(Optional.class)))
                .thenReturn(Optional.empty());

        // Call and verify
        Assert.assertTrue(topicService.isTopicExist("existing_topic"));
        Assert.assertFalse(topicService.isTopicExist("nonexistent_topic"));
    }

    @Test
    public void testGetAllTopicList() throws Exception {
        // Prepare test data
        List<TopicInfo> topicInfos = new ArrayList<>();
        TopicInfo topic1 = new TopicInfo();
        topic1.setTopicName("topic1");
        topicInfos.add(topic1);

        TopicInfo topic2 = new TopicInfo();
        topic2.setTopicName("topic2");
        topicInfos.add(topic2);

        // Mock metadataProvider
        when(metadataProvider.listTopics(any(Optional.class))).thenReturn(topicInfos);

        // Call the method
        List<TopicInfo> result = topicService.getAllTopicList();

        // Verify
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("topic1", result.get(0).getTopicName());
        Assert.assertEquals("topic2", result.get(1).getTopicName());
    }

    @Test
    public void testDeleteTopic() throws Exception {
        // Mock metadataProvider - deleteTopic returns void
        doNothing().when(metadataProvider).deleteTopic(eq("test_topic"), any(Optional.class));

        // Call the method
        boolean result = topicService.deleteTopic("test_topic");

        // Verify
        Assert.assertTrue(result);
        verify(metadataProvider, times(1)).deleteTopic(eq("test_topic"), any(Optional.class));
    }

    @Test
    public void testCreateTopicWithType() throws Exception {
        // Prepare test data
        doNothing().when(metadataProvider).createTopic(any(TopicInfo.class));

        // Call the method
        boolean result = topicService.createTopicWithType("new_topic", 4, 4, 6, TopicType.NORMAL);

        // Verify
        Assert.assertTrue(result);
        verify(metadataProvider, times(1)).createTopic(any(TopicInfo.class));
    }

    @Test
    public void testUpdateTopic() throws Exception {
        // Mock metadataProvider
        doNothing().when(metadataProvider).updateTopic(any(TopicInfo.class));

        // Call the method
        boolean result = topicService.updateTopic("test_topic", 8, 8, 6);

        // Verify
        Assert.assertTrue(result);
        verify(metadataProvider, times(1)).updateTopic(any(TopicInfo.class));
    }

    @Test
    public void testExamineTopicConfigUnsupported() {
        // examineTopicConfig is a default method that throws UnsupportedOperationException
        // TopicServiceImpl does not override it, so calling it should throw
        Assert.assertThrows(UnsupportedOperationException.class, () -> {
            topicService.examineTopicConfig("test_topic");
        });
    }

    @Test
    public void testSendTopicMessageRequestUnsupported() {
        // sendTopicMessageRequest is a default method that throws UnsupportedOperationException
        // TopicServiceImpl does not override it, so calling it should throw
        Assert.assertThrows(UnsupportedOperationException.class, () -> {
            topicService.sendTopicMessageRequest(null);
        });
    }
}