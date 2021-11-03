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

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.dashboard.model.DlqMessageResendResult;
import org.apache.rocketmq.dashboard.model.DlqMessageRequest;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.service.DlqMessageService;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DlqMessageServiceImpl implements DlqMessageService {

    @Resource
    private MQAdminExt mqAdminExt;

    @Resource
    private MessageService messageService;

    @Override
    public MessagePage queryDlqMessageByPage(MessageQuery query) {
        List<MessageView> messageViews = new ArrayList<>();
        PageRequest page = PageRequest.of(query.getPageNum(), query.getPageSize());
        String topic = query.getTopic();
        try {
            mqAdminExt.examineTopicRouteInfo(topic);
        } catch (MQClientException e) {
            // If the %DLQ%Group does not exist, the message returns null
            if (topic.startsWith(MixAll.DLQ_GROUP_TOPIC_PREFIX)
                && e.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
                return new MessagePage(new PageImpl<>(messageViews, page, 0), query.getTaskId());
            } else {
                throw Throwables.propagate(e);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return messageService.queryMessageByPage(query);
    }

    @Override
    public List<DlqMessageResendResult> batchResendDlqMessage(List<DlqMessageRequest> dlqMessages) {
        List<DlqMessageResendResult> batchResendResults = new LinkedList<>();
        for (DlqMessageRequest dlqMessage : dlqMessages) {
            ConsumeMessageDirectlyResult result = messageService.consumeMessageDirectly(dlqMessage.getTopicName(),
                dlqMessage.getMsgId(), dlqMessage.getConsumerGroup(),
                dlqMessage.getClientId());
            DlqMessageResendResult resendResult = new DlqMessageResendResult(result, dlqMessage.getMsgId());
            batchResendResults.add(resendResult);
        }
        return batchResendResults;
    }
}
