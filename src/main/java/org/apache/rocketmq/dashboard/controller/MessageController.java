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

import jakarta.annotation.Resource;
import org.apache.rocketmq.common.Pair;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.MessagePage;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.MessageService;
import org.apache.rocketmq.dashboard.util.JsonUtil;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/message")
@Permission
public class MessageController {
    private Logger logger = LoggerFactory.getLogger(MessageController.class);
    @Resource
    private MessageService messageService;

    @RequestMapping(value = "/viewMessage.query", method = RequestMethod.GET)
    @ResponseBody
    public Object viewMessage(@RequestParam(required = false) String topic, @RequestParam String msgId) {
        try {
        Pair<MessageView, List<MessageTrack>> messageViewListPair = messageService.viewMessage(topic, msgId);
            MessageView messageView = messageViewListPair.getObject1();
            if (messageView == null) {
                logger.error("viewMessage returned null MessageView for topic: {}, msgId: {}", topic, msgId);
                throw new ServiceException(-1, "Message not found");
            }
            // Return MessageView directly for frontend compatibility
            // Frontend expects resp.data to be the MessageView object
            return messageView;
        } catch (ServiceException e) {
            logger.error("ServiceException in viewMessage: topic={}, msgId={}, error={}", topic, msgId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Exception in viewMessage: topic={}, msgId={}", topic, msgId, e);
            throw new ServiceException(-1, String.format("Failed to query message by Id: %s", msgId));
        }
    }

    @PostMapping("/queryMessagePageByTopic.query")
    @ResponseBody
    public MessagePage queryMessagePageByTopic(@RequestBody MessageQuery query) {
        return messageService.queryMessageByPage(query);
    }

    @RequestMapping(value = "/queryMessageByTopicAndKey.query", method = RequestMethod.GET)
    @ResponseBody
    public Object queryMessageByTopicAndKey(@RequestParam String topic, @RequestParam String key) {
        return messageService.queryMessageByTopicAndKey(topic, key);
    }

    @RequestMapping(value = "/queryMessageByTopic.query", method = RequestMethod.GET)
    @ResponseBody
    public Object queryMessageByTopic(@RequestParam String topic, @RequestParam long begin,
                                      @RequestParam long end) {
        return messageService.queryMessageByTopic(topic, begin, end);
    }

    @RequestMapping(value = "/consumeMessageDirectly.do", method = RequestMethod.POST)
    @ResponseBody
    public Object consumeMessageDirectly(@RequestParam String topic, @RequestParam String consumerGroup,
                                         @RequestParam String msgId,
                                         @RequestParam(required = false) String clientId) {
        logger.info("msgId={} consumerGroup={} clientId={}", msgId, consumerGroup, clientId);
        try {
        ConsumeMessageDirectlyResult consumeMessageDirectlyResult = messageService.consumeMessageDirectly(topic, msgId, consumerGroup, clientId);
        logger.info("consumeMessageDirectlyResult={}", JsonUtil.obj2String(consumeMessageDirectlyResult));
        return consumeMessageDirectlyResult;
        } catch (RuntimeException e) {
            logger.error("RuntimeException in consumeMessageDirectly: msgId={}, consumerGroup={}, error={}", 
                    msgId, consumerGroup, e.getMessage());
            // Check if the cause is MQBrokerException with "not online" error
            Throwable cause = e.getCause();
            if (cause instanceof org.apache.rocketmq.client.exception.MQBrokerException) {
                org.apache.rocketmq.client.exception.MQBrokerException mqEx = 
                    (org.apache.rocketmq.client.exception.MQBrokerException) cause;
                if (mqEx.getMessage() != null && mqEx.getMessage().contains("not online")) {
                    throw new ServiceException(-1, 
                        String.format("Cannot resend message: Consumer group '%s' is not online. " +
                                "Please ensure the consumer is running before resending DLQ messages.", consumerGroup));
                }
                throw new ServiceException(-1, String.format("Failed to resend message: %s", mqEx.getMessage()));
            }
            // Check if the error message itself contains "not online"
            if (e.getMessage() != null && e.getMessage().contains("not online")) {
                throw new ServiceException(-1, 
                    String.format("Cannot resend message: Consumer group '%s' is not online. " +
                            "Please ensure the consumer is running before resending DLQ messages.", consumerGroup));
            }
            throw new ServiceException(-1, String.format("Failed to resend message: %s", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in consumeMessageDirectly: msgId={}, consumerGroup={}", msgId, consumerGroup, e);
            throw new ServiceException(-1, String.format("Failed to resend message: %s", e.getMessage()));
        }
    }
}
