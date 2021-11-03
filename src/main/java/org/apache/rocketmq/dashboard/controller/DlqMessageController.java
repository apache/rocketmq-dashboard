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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.DlqMessageExcelModel;
import org.apache.rocketmq.dashboard.model.DlqMessageRequest;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.DlqMessageService;
import org.apache.rocketmq.dashboard.util.ExcelUtil;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/dlqMessage")
@Permission
@Slf4j
public class DlqMessageController {

    @Resource
    private DlqMessageService dlqMessageService;

    @Resource
    private MQAdminExt mqAdminExt;

    @RequestMapping(value = "/queryDlqMessageByConsumerGroup.query", method = RequestMethod.POST)
    @ResponseBody
    public Object queryDlqMessageByConsumerGroup(@RequestBody MessageQuery query) {
        return dlqMessageService.queryDlqMessageByPage(query);
    }

    @GetMapping(value = "/exportDlqMessage.do")
    public void exportDlqMessage(HttpServletResponse response, @RequestParam String consumerGroup,
        @RequestParam String msgId) {
        MessageExt messageExt = null;
        try {
            String topic = MixAll.DLQ_GROUP_TOPIC_PREFIX + consumerGroup;
            messageExt = mqAdminExt.viewMessage(topic, msgId);
        } catch (Exception e) {
            throw new ServiceException(-1, String.format("Failed to query message by Id: %s", msgId));
        }
        DlqMessageExcelModel excelModel = new DlqMessageExcelModel(messageExt);
        try {
            ExcelUtil.writeExcel(response, Lists.newArrayList(excelModel), "dlq", "dlq", DlqMessageExcelModel.class);
        } catch (Exception e) {
            throw new ServiceException(-1, String.format("export dlq message failed!"));
        }
    }

    @PostMapping(value = "/batchResendDlqMessage.do")
    @ResponseBody
    public Object batchResendDlqMessage(@RequestBody List<DlqMessageRequest> dlqMessages) {
        return dlqMessageService.batchResendDlqMessage(dlqMessages);
    }

    @PostMapping(value = "/batchExportDlqMessage.do")
    public void batchExportDlqMessage(HttpServletResponse response, @RequestBody List<DlqMessageRequest> dlqMessages) {
        List<DlqMessageExcelModel> dlqMessageExcelModelList = new ArrayList<>(dlqMessages.size());
        for (DlqMessageRequest dlqMessage : dlqMessages) {
            DlqMessageExcelModel excelModel = new DlqMessageExcelModel();
            try {
                String topic = MixAll.DLQ_GROUP_TOPIC_PREFIX + dlqMessage.getConsumerGroup();
                MessageExt messageExt = mqAdminExt.viewMessage(topic, dlqMessage.getMsgId());
                excelModel = new DlqMessageExcelModel(messageExt);
            } catch (Exception e) {
                log.error("Failed to query message by Id:{}", dlqMessage.getMsgId(), e);
                excelModel.setMsgId(dlqMessage.getMsgId());
                excelModel.setException(e.getMessage());
            }
            dlqMessageExcelModelList.add(excelModel);
        }
        try {
            ExcelUtil.writeExcel(response, dlqMessageExcelModelList, "dlqs", "dlqs", DlqMessageExcelModel.class);
        } catch (Exception e) {
            throw new ServiceException(-1, String.format("export dlq message failed!"));
        }
    }
}
