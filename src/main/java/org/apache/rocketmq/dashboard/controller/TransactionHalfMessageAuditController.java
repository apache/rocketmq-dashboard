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

import org.apache.rocketmq.dashboard.model.TransactionHalfMessageAuditReport;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.TransactionHalfMessageAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/transaction")
@Permission
public class TransactionHalfMessageAuditController {

    @Autowired
    private TransactionHalfMessageAuditService transactionHalfMessageAuditService;

    @RequestMapping(value = "/halfAudit.query", method = RequestMethod.GET)
    @ResponseBody
    public TransactionHalfMessageAuditReport queryHalfAudit(
        @RequestParam(value = "topic", required = false) String topic,
        @RequestParam(value = "producerGroup", required = false) String producerGroup) {
        return transactionHalfMessageAuditService.auditPendingHalfMessages(topic, producerGroup);
    }

    @RequestMapping(value = "/halfResolve.do", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> resolveTransaction(
        @RequestParam("msgId") String msgId,
        @RequestParam(value = "transactionId", required = false) String transactionId,
        @RequestParam("action") String action) {
        boolean success = transactionHalfMessageAuditService.resolveTransaction(msgId, transactionId, action);
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("msgId", msgId);
        result.put("action", action);
        return result;
    }
}
