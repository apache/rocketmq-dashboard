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

import org.apache.rocketmq.dashboard.model.MessageBatchOperationRequest;
import org.apache.rocketmq.dashboard.model.MessageBatchOperationResult;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.MessageBatchOperationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/message-batch")
@Permission
public class MessageBatchOperationController {

    @Autowired
    private MessageBatchOperationService messageBatchOperationService;

    @RequestMapping(value = "/resend.do", method = RequestMethod.POST)
    @ResponseBody
    public MessageBatchOperationResult batchResend(@RequestBody MessageBatchOperationRequest request) {
        return messageBatchOperationService.batchResendMessages(request);
    }

    @RequestMapping(value = "/export.do", method = RequestMethod.POST)
    public ResponseEntity<byte[]> exportCsv(@RequestBody MessageBatchOperationRequest request) {
        String csvData = messageBatchOperationService.exportMessagesAsCsv(request);
        byte[] bytes = csvData.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "message_audit_export.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}
