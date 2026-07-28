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
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.impl.MessageServiceImpl;
import org.apache.rocketmq.remoting.protocol.body.CMResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.apache.rocketmq.tools.admin.api.TrackType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MessageControllerTest extends BaseControllerTest {

    private static final String MSG_ID = "0A9A003F00002A9F0000000000000319";

    @InjectMocks
    private MessageController messageController;

    @Spy
    private MessageServiceImpl messageService;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ClusterProvider clusterProvider;

    @Before
    public void init() throws Exception {
        super.mockRmqConfigure();
        // enable message related capabilities for the architecture abstraction layer
        ClusterCapability capability = new ClusterCapability();
        capability.setExtendedCapabilities(new HashSet<>(Arrays.asList(
                "MESSAGE_QUERY", "MESSAGE_QUERY_BY_KEY", "MESSAGE_CONSUME_DIRECTLY")));
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
    }

    private MessageInfo createMessageInfo() {
        MessageInfo messageInfo = new MessageInfo();
        messageInfo.setMsgId(MSG_ID);
        messageInfo.setTopic("topic_test");
        return messageInfo;
    }

    @Test
    public void testViewMessage() throws Exception {
        final String url = "/message/viewMessage.query";
        {
            MessageView messageView = new MessageView();
            messageView.setTopic("topic_test");
            messageView.setMsgId(MSG_ID);
            MessageTrack track = new MessageTrack();
            track.setConsumerGroup("group_test");
            track.setTrackType(TrackType.CONSUMED);
            List<MessageTrack> tracks = new ArrayList<>();
            tracks.add(track);
            // 1st call: message not found -> error; 2nd: no track; 3rd: with track
            doThrow(new RuntimeException("no message"))
                    .doReturn(new Pair<>(messageView, new ArrayList<MessageTrack>()))
                    .doReturn(new Pair<>(messageView, tracks))
                    .when(messageService).viewMessage(anyString(), anyString());
        }
        // no message
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", "topic_test");
        requestBuilder.param("msgId", MSG_ID);
        perform = mockMvc.perform(requestBuilder);
        performErrorExpect(perform);

        // consumer not online
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageView.msgId").value(MSG_ID))
                .andExpect(jsonPath("$.data.messageTrackList", hasSize(0)));

        // query message success and has a group consumed.
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageView.msgId").value(MSG_ID))
                .andExpect(jsonPath("$.data.messageTrackList", hasSize(1)))
                .andExpect(jsonPath("$.data.messageTrackList[0].consumerGroup").value("group_test"))
                .andExpect(jsonPath("$.data.messageTrackList[0].trackType").value(TrackType.CONSUMED.name()));
    }

    @Test
    public void testQueryMessagePageByTopic() throws Exception {
        final String url = "/message/queryMessagePageByTopic.query";
        {
            // 1st query finds nothing, 2nd query returns one message
            when(metadataProvider.queryMessageByTopic(anyString(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(new ArrayList<>())
                    .thenReturn(List.of(createMessageInfo()));
        }
        MessageQuery query = new MessageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTopic("topic_test");
        query.setTaskId("");
        query.setBegin(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000);
        query.setEnd(System.currentTimeMillis());

        // no message found
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(query));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.content", hasSize(0)));

        // message found
        requestBuilder.content(JSON.toJSONString(query));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.content", hasSize(1)))
                .andExpect(jsonPath("$.data.page.content[0].msgId").value(MSG_ID));
    }

    @Test
    public void testQueryMessageByTopicAndKey() throws Exception {
        final String url = "/message/queryMessageByTopicAndKey.query";
        {
            when(metadataProvider.queryMessageByTopicAndKey(anyString(), anyString(), anyLong(), anyLong()))
                    .thenReturn(List.of(createMessageInfo()));
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", "topic_test");
        requestBuilder.param("key", "KeyA");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].msgId").value(MSG_ID));
    }

    @Test
    public void testQueryMessageByTopic() throws Exception {
        final String url = "/message/queryMessageByTopic.query";
        {
            when(metadataProvider.queryMessageByTopic(anyString(), anyLong(), anyLong(), anyInt()))
                    .thenReturn(List.of(createMessageInfo()));
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("topic", "topic_test")
                .param("begin", Long.toString(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000))
                .param("end", Long.toString(System.currentTimeMillis()));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].msgId").value(MSG_ID));
    }

    @Test
    public void testConsumeMessageDirectly() throws Exception {
        final String url = "/message/consumeMessageDirectly.do";
        {
            ConsumeMessageDirectlyResult result1 = new ConsumeMessageDirectlyResult();
            result1.setConsumeResult(CMResult.CR_SUCCESS);
            ConsumeMessageDirectlyResult result2 = new ConsumeMessageDirectlyResult();
            result2.setConsumeResult(CMResult.CR_LATER);
            // clientId is optional so the second request passes null
            when(metadataProvider.consumeMessageDirectly(anyString(), anyString(), anyString(), nullable(String.class)))
                    .thenReturn(result1).thenReturn(result2);
        }

        // clientId is not empty
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.param("topic", "topic_test")
                .param("consumerGroup", "group_test")
                .param("msgId", MSG_ID)
                .param("clientId", "127.0.0.1@37540#2295913058176000");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consumeResult").value(CMResult.CR_SUCCESS.name()));

        // clientId is empty
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.param("topic", "topic_test")
                .param("consumerGroup", "group_test")
                .param("msgId", MSG_ID);
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consumeResult").value(CMResult.CR_LATER.name()));
    }

    @Override
    protected Object getTestController() {
        return messageController;
    }
}
