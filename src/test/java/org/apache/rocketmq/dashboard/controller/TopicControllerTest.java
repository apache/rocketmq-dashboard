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

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.dashboard.model.request.SendTopicMessageRequest;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.apache.rocketmq.dashboard.model.request.TopicTypeList;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.impl.TopicServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TopicControllerTest extends BaseControllerTest {

    @InjectMocks
    private TopicController topicController;

    @Spy
    private TopicServiceImpl topicService;

    @Mock
    private ConsumerService consumerService;

    @Mock
    private ClusterInfoService clusterInfoService;

    private String topicName = "topic_test";

    @Before
    public void init() {
        super.mockRmqConfigure();
        ClusterInfo mockClusterInfo = getClusterInfo();
        when(clusterInfoService.get()).thenReturn(mockClusterInfo);

    }

    @Test
    public void testList() throws Exception {
        {
            // mock all topics using the new fetchAllTopicList API
            List<TopicInfo> topicInfoList = new ArrayList<>();
            topicInfoList.add(new TopicInfo() {{ setTopicName("common_topic1"); setTopicType(TopicType.NORMAL); }});
            topicInfoList.add(new TopicInfo() {{ setTopicName("common_topic2"); setTopicType(TopicType.NORMAL); }});
            topicInfoList.add(new TopicInfo() {{ setTopicName("%DLQ%topic"); setTopicType(TopicType.NORMAL); }});
            topicInfoList.add(new TopicInfo() {{ setTopicName("%RETRY%topic"); setTopicType(TopicType.NORMAL); }});
            doReturn(topicInfoList).when(topicService).fetchAllTopicList(anyBoolean(), anyBoolean());
        }
        final String url = "/topic/list.query";

        // 1、list all topic
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("skipSysProcess", String.valueOf(true));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)));

        // 2、list all topic filter DLQ and Retry topic
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("skipSysProcess", String.valueOf(false));
        requestBuilder.param("skipRetryAndDlq", String.valueOf(true));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));

        // 3、filter system topic
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)));
    }

    @Test
    public void testStat() throws Exception {
        {
            TopicStatsTable topicStatsTable = MockObjectUtil.createTopicStatsTable();
            when(mqAdminExt.examineTopicStats(anyString())).thenReturn(topicStatsTable);
        }
        final String url = "/topic/stats.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform);
    }

    @Test
    public void testRoute() throws Exception {
        {
            TopicRouteData topicRouteData = MockObjectUtil.createTopicRouteData();
            when(mqAdminExt.examineTopicRouteInfo(anyString())).thenReturn(topicRouteData);
        }
        final String url = "/topic/route.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        performOkExpect(perform);
    }

    @Test
    public void testCreateOrUpdate() throws Exception {
        final String url = "/topic/createOrUpdate.do";

        // 1、clusterName and brokerName all blank
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        TopicConfigInfo info = new TopicConfigInfo();
        requestBuilder.content(JSON.toJSONString(info));
        perform = mockMvc.perform(requestBuilder);
        performErrorExpect(perform);

        {
            ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
            when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
            doNothing().when(mqAdminExt).createAndUpdateTopicConfig(anyString(), any());
        }

        List<String> clusterNameList = Lists.newArrayList("DefaultCluster");
        info.setTopicName("topic_test");
        info.setReadQueueNums(4);
        info.setWriteQueueNums(4);
        info.setPerm(6);
        info.setClusterNameList(clusterNameList);
        // 2、create topic
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(info));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testExamineTopicConfig() throws Exception {
        final String url = "/topic/examineTopicConfig.query";
        {
            when(topicService.examineTopicConfig(anyString())).thenReturn(List.of(new TopicConfig(topicName)));
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].topicName").value(topicName));
    }

    @Test
    public void testQueryConsumerByTopic() throws Exception {
        final String url = "/topic/queryConsumerByTopic.query";
        {
            GroupList list = new GroupList();
            list.setGroupList(Sets.newHashSet("group1"));
            when(mqAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(list);
            ConsumeStats consumeStats = MockObjectUtil.createConsumeStats();
            when(mqAdminExt.examineConsumeStats(anyString(), anyString())).thenReturn(consumeStats);
            when(mqAdminExt.examineConsumerConnectionInfo(anyString())).thenReturn(new ConsumerConnection());
            when(mqAdminExt.getConsumerRunningInfo(anyString(), anyString(), anyBoolean())).thenReturn(new ConsumerRunningInfo());
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    public void testQueryTopicConsumerInfo() throws Exception {
        final String url = "/topic/queryTopicConsumerInfo.query";
        {
            GroupList list = new GroupList();
            list.setGroupList(Sets.newHashSet("group1", "group2", "group3"));
            when(mqAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(list);
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupList", hasSize(3)));
    }

    @Test
    public void testSendTopicMessage() throws Exception {
        final String url = "/topic/sendTopicMessage.do";
        {
            SendResult result = new SendResult(SendStatus.SEND_OK, "7F000001E41A2E5D6D978B82C20F003D",
                    "0A8E83C300002A9F00000000000013D3", new MessageQueue(), 1000L);
            when(topicService.sendTopicMessageRequest(any(SendTopicMessageRequest.class))).thenReturn(result);
        }
        SendTopicMessageRequest request = new SendTopicMessageRequest();
        request.setTopic(topicName);
        request.setMessageBody("hello world");
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(request));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sendStatus").value(SendStatus.SEND_OK.name()))
                .andExpect(jsonPath("$.data.msgId").value("7F000001E41A2E5D6D978B82C20F003D"));
    }

    @Test
    public void testDelete() throws Exception {
        final String url = "/topic/deleteTopic.do";
        {
            ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
            when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
            doNothing().when(mqAdminExt).deleteTopicInBroker(any(), anyString());
            doNothing().when(mqAdminExt).deleteTopicInNameServer(any(), anyString());
        }

        // 1、clusterName is blank
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.param("topic", topicName);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        // 2、clusterName is not blank
        requestBuilder.param("clusterName", "DefaultCluster");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testDeleteTopicByBroker() throws Exception {
        {
            ClusterInfo clusterInfo = MockObjectUtil.createClusterInfo();
            when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
            doNothing().when(mqAdminExt).deleteTopicInBroker(any(), anyString());
        }
        final String url = "/topic/deleteTopicByBroker.do";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.param("topic", topicName);
        requestBuilder.param("brokerName", "broker-a");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testListTopicType() throws Exception {
        // Build test environment
        // Set up scope at beginning with '{' and '}' to match the class pattern
        {
            // Create mock TopicTypeList to be returned by service
            ArrayList<String> topicNames = new ArrayList<>();
            topicNames.add("topic1");
            topicNames.add("topic2");
            topicNames.add("%SYS%topic3");

            ArrayList<String> messageTypes = new ArrayList<>();
            messageTypes.add("NORMAL");
            messageTypes.add("FIFO");
            messageTypes.add("SYSTEM");

            TopicTypeList topicTypeList = new TopicTypeList(topicNames, messageTypes);

            // Mock service method
            doReturn(topicTypeList).when(topicService).examineAllTopicType();
        }

        // Execute request
        final String url = "/topic/list.queryTopicType";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        // Verify response
        performOkExpect(perform)
                .andExpect(jsonPath("$.data.topicNameList", hasSize(3)))
                .andExpect(jsonPath("$.data.topicNameList[0]").value("topic1"))
                .andExpect(jsonPath("$.data.topicNameList[1]").value("topic2"))
                .andExpect(jsonPath("$.data.topicNameList[2]").value("%SYS%topic3"))
                .andExpect(jsonPath("$.data.messageTypeList", hasSize(3)))
                .andExpect(jsonPath("$.data.messageTypeList[0]").value("NORMAL"))
                .andExpect(jsonPath("$.data.messageTypeList[1]").value("FIFO"))
                .andExpect(jsonPath("$.data.messageTypeList[2]").value("SYSTEM"));
    }

    @Override
    protected Object getTestController() {
        return topicController;
    }
}
