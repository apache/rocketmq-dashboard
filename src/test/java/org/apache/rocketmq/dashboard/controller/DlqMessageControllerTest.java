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
import java.util.List;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.CMResult;
import org.apache.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.common.protocol.route.TopicRouteData;
import org.apache.rocketmq.dashboard.model.DlqMessageRequest;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.impl.DlqMessageServiceImpl;
import org.apache.rocketmq.dashboard.service.impl.MessageServiceImpl;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class DlqMessageControllerTest extends BaseControllerTest {

    @InjectMocks
    private DlqMessageController dlqMessageController;

    @Spy
    private DlqMessageServiceImpl dlqMessageService;

    @Mock
    private MessageServiceImpl messageService;

    @Test
    public void testQueryDlqMessageByConsumerGroup() throws Exception {
        final String url = "/dlqMessage/queryDlqMessageByConsumerGroup.query";
        MessageQuery query = new MessageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTopic(MixAll.DLQ_GROUP_TOPIC_PREFIX + "group_test");
        query.setTaskId("");
        query.setBegin(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000);
        query.setEnd(System.currentTimeMillis());
        {
            TopicRouteData topicRouteData = MockObjectUtil.createTopicRouteData();
            when(mqAdminExt.examineTopicRouteInfo(any()))
                .thenThrow(new MQClientException(ResponseCode.TOPIC_NOT_EXIST, "topic not exist"))
                .thenThrow(new MQClientException(ResponseCode.NO_MESSAGE, "query no message"))
                .thenThrow(new RuntimeException())
                .thenReturn(topicRouteData);
            MessageView messageView = MessageView.fromMessageExt(MockObjectUtil.createMessageExt());
            PageRequest page = PageRequest.of(query.getPageNum(), query.getPageSize());
            MessagePage messagePage = new MessagePage
                (new PageImpl<>(Lists.newArrayList(messageView), page, 0), query.getTaskId());
            when(messageService.queryMessageByPage(any())).thenReturn(messagePage);
        }
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(query));
        // 1、%DLQ%group_test is not exist
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page.content", hasSize(0)));

        // 2、Other MQClientException occur
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").isNotEmpty());

        // 3、Other Exception occur
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").isNotEmpty());

        // 4、query dlq message success
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page.content", hasSize(1)))
            .andExpect(jsonPath("$.data.page.content[0].msgId").value("0A9A003F00002A9F0000000000000319"));
    }

    @Test
    public void testExportDlqMessage() throws Exception {
        final String url = "/dlqMessage/exportDlqMessage.do";
        {
            when(mqAdminExt.viewMessage(any(), any()))
                .thenThrow(new RuntimeException())
                .thenReturn(MockObjectUtil.createMessageExt());
        }
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("consumerGroup", "group_test");
        requestBuilder.param("msgId", "0A9A003F00002A9F0000000000000319");
        // 1、viewMessage exception
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(jsonPath("$.status").value(-1))
            .andExpect(jsonPath("$.errMsg").isNotEmpty());

        // 2、export dlqMessage success
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().is(200))
            .andExpect(content().contentType("application/vnd.ms-excel"));

    }

    @Test
    public void testBatchResendDlqMessage() throws Exception {
        final String url = "/dlqMessage/batchResendDlqMessage.do";
        List<DlqMessageRequest> dlqMessages = MockObjectUtil.createDlqMessageRequest();
        {
            ConsumeMessageDirectlyResult result = new ConsumeMessageDirectlyResult();
            result.setConsumeResult(CMResult.CR_SUCCESS);
            when(messageService.consumeMessageDirectly(any(), any(), any(), any())).thenReturn(result);
        }
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(dlqMessages));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].consumeResult").value("CR_SUCCESS"));
    }

    @Test
    public void testBatchExportDlqMessage() throws Exception {
        final String url = "/dlqMessage/batchExportDlqMessage.do";
        {
            when(mqAdminExt.viewMessage("%DLQ%group_test", "0A9A003F00002A9F0000000000000310"))
                .thenThrow(new RuntimeException());
            when(mqAdminExt.viewMessage("%DLQ%group_test", "0A9A003F00002A9F0000000000000311"))
                .thenReturn(MockObjectUtil.createMessageExt());
        }
        List<DlqMessageRequest> dlqMessages = MockObjectUtil.createDlqMessageRequest();
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON_UTF8);
        requestBuilder.content(JSON.toJSONString(dlqMessages));
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().is(200))
            .andExpect(content().contentType("application/vnd.ms-excel"));
    }

    @Override protected Object getTestController() {
        return dlqMessageController;
    }
}
