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
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.DlqMessageExcelModel;
import org.apache.rocketmq.dashboard.model.DlqMessageRequest;
import org.apache.rocketmq.dashboard.model.DlqMessageResendResult;
import org.apache.rocketmq.dashboard.model.request.MessageQuery;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.DlqMessageService;
import org.apache.rocketmq.dashboard.util.ExcelUtil;
import org.apache.rocketmq.remoting.protocol.body.CMResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

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
            log.info("Exporting DLQ message: msgId={}, topic={}, consumerGroup={}", msgId, topic, consumerGroup);
            messageExt = mqAdminExt.viewMessage(topic, msgId);
            if (messageExt == null) {
                log.error("Message not found: msgId={}, topic={}", msgId, topic);
                throw new ServiceException(-1, String.format("Message not found: %s", msgId));
            }
            log.info("Message retrieved successfully: msgId={}, bodyLength={}", msgId, 
                    messageExt.getBody() != null ? messageExt.getBody().length : 0);
        } catch (ServiceException e) {
            log.error("ServiceException while querying message: msgId={}, consumerGroup={}", msgId, consumerGroup, e);
            throw e;
        } catch (Exception e) {
            log.error("Exception while querying message: msgId={}, consumerGroup={}", msgId, consumerGroup, e);
            throw new ServiceException(-1, String.format("Failed to query message by Id: %s, error: %s", msgId, e.getMessage()));
        }
        
        DlqMessageExcelModel excelModel = null;
        try {
            excelModel = new DlqMessageExcelModel(messageExt);
            log.info("Excel model created: msgId={}, topic={}, bodyLength={}", 
                    excelModel.getMsgId(), excelModel.getTopic(), 
                    excelModel.getMessageBody() != null ? excelModel.getMessageBody().length() : 0);
        } catch (Exception e) {
            log.error("Failed to create Excel model: msgId={}", msgId, e);
            throw new ServiceException(-1, String.format("Failed to create Excel model: %s", e.getMessage()));
        }
        
        try {
            List<DlqMessageExcelModel> dataList = Lists.newArrayList(excelModel);
            log.info("Writing Excel file: dataList size={}, msgId={}", dataList.size(), msgId);
            ExcelUtil.writeExcel(response, dataList, "dlq", "dlq", DlqMessageExcelModel.class);
            log.info("Excel file written successfully: msgId={}", msgId);
        } catch (Exception e) {
            log.error("Failed to write Excel file: msgId={}, error={}", msgId, e.getMessage(), e);
            // Don't try to reset response if already committed - it will cause another error
            if (!response.isCommitted()) {
                try {
                    response.reset();
                    response.setContentType("application/json;charset=UTF-8");
                } catch (Exception resetEx) {
                    log.error("Failed to reset response: {}", resetEx.getMessage());
                }
            }
            throw new ServiceException(-1, String.format("export dlq message failed: %s", e.getMessage()));
        }
    }

    @PostMapping(value = "/resendDlqMessage.do")
    @ResponseBody
    public Object resendDlqMessage(@RequestParam String msgId,
                                    @RequestParam String consumerGroup,
                                    @RequestParam String topic) {
        DlqMessageRequest dlqMessage = new DlqMessageRequest();
        dlqMessage.setMsgId(msgId);
        dlqMessage.setConsumerGroup(consumerGroup);
        dlqMessage.setTopicName(topic);
        dlqMessage.setClientId(null);

        List<DlqMessageResendResult> results = dlqMessageService.batchResendDlqMessage(
                Lists.newArrayList(dlqMessage));

        if (results != null && !results.isEmpty()) {
            DlqMessageResendResult result = results.get(0);
            if (result.getConsumeResult() == CMResult.CR_SUCCESS) {
                ConsumeMessageDirectlyResult consumeResult = new ConsumeMessageDirectlyResult();
                consumeResult.setConsumeResult(CMResult.CR_SUCCESS);
                consumeResult.setRemark(result.getRemark());
                return consumeResult;
            } else {
                throw new ServiceException(-1, result.getRemark() != null ? result.getRemark() : "Failed to resend DLQ message");
            }
        }
        throw new ServiceException(-1, "Failed to resend DLQ message");
    }

    @PostMapping(value = "/batchResendDlqMessage.do")
    @ResponseBody
    public Object batchResendDlqMessage(@RequestBody List<DlqMessageRequest> dlqMessages) {
        return dlqMessageService.batchResendDlqMessage(dlqMessages);
    }

    @PostMapping(value = "/batchExportDlqMessage.do")
    public void batchExportDlqMessage(HttpServletResponse response, @RequestBody List<DlqMessageRequest> dlqMessages) {
        log.info("Batch export request received: {} messages", dlqMessages != null ? dlqMessages.size() : 0);
        if (dlqMessages == null || dlqMessages.isEmpty()) {
            log.warn("Empty message list received for batch export");
            throw new ServiceException(-1, "No messages to export");
        }
        
        List<DlqMessageExcelModel> dlqMessageExcelModelList = new ArrayList<>(dlqMessages.size());
        int successCount = 0;
        int errorCount = 0;
        
        for (int i = 0; i < dlqMessages.size(); i++) {
            DlqMessageRequest dlqMessage = dlqMessages.get(i);
            DlqMessageExcelModel excelModel = new DlqMessageExcelModel();
            try {
                String topic = MixAll.DLQ_GROUP_TOPIC_PREFIX + dlqMessage.getConsumerGroup();
                log.debug("Exporting message {}/{}: msgId={}, topic={}, consumerGroup={}", 
                        i + 1, dlqMessages.size(), dlqMessage.getMsgId(), topic, dlqMessage.getConsumerGroup());
                MessageExt messageExt = mqAdminExt.viewMessage(topic, dlqMessage.getMsgId());
                if (messageExt == null) {
                    log.warn("Message not found: msgId={}, topic={}", dlqMessage.getMsgId(), topic);
                    excelModel.setMsgId(dlqMessage.getMsgId());
                    excelModel.setException("Message not found");
                    errorCount++;
                } else {
                excelModel = new DlqMessageExcelModel(messageExt);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("Failed to query message by Id: {}, error: {}", dlqMessage.getMsgId(), e.getMessage(), e);
                excelModel.setMsgId(dlqMessage.getMsgId());
                excelModel.setException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errorCount++;
            }
            dlqMessageExcelModelList.add(excelModel);
        }
        
        log.info("Batch export: processed {}/{} messages successfully, {} errors, Excel model list size: {}", 
                successCount, dlqMessages.size(), errorCount, dlqMessageExcelModelList.size());
        
        if (dlqMessageExcelModelList.isEmpty()) {
            log.warn("No messages to export after processing");
            throw new ServiceException(-1, "No messages could be exported");
        }
        
        // Log each message ID that will be exported
        log.info("Messages to export ({} total):", dlqMessageExcelModelList.size());
        for (int i = 0; i < dlqMessageExcelModelList.size(); i++) {
            DlqMessageExcelModel model = dlqMessageExcelModelList.get(i);
            log.info("  {}. msgId={}, topic={}, exception={}", 
                    i + 1, model.getMsgId(), model.getTopic(), 
                    model.getException() != null ? model.getException() : "none");
        }
        
        // Verify the list size matches what we expect
        if (dlqMessageExcelModelList.size() != dlqMessages.size()) {
            log.warn("WARNING: Excel model list size ({}) does not match input messages size ({})", 
                    dlqMessageExcelModelList.size(), dlqMessages.size());
        }
        
        // Ensure response is not committed before writing
        if (response.isCommitted()) {
            log.error("Response already committed, cannot write Excel file");
            throw new ServiceException(-1, "Response already committed");
        }
        
        try {
            log.info("Calling ExcelUtil.writeExcel with {} rows", dlqMessageExcelModelList.size());
            ExcelUtil.writeExcel(response, dlqMessageExcelModelList, "dlqs", "dlqs", DlqMessageExcelModel.class);
            log.info("Batch export Excel file written successfully: {} messages exported", dlqMessageExcelModelList.size());
        } catch (Exception e) {
            log.error("Failed to write batch export Excel file", e);
            throw new ServiceException(-1, String.format("export dlq message failed: %s", e.getMessage()));
        }
    }
}
