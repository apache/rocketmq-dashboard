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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.domain.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public Result<List<MessageRecordVO>> queryMessages(
            @RequestParam String instanceId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String msgId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        return Result.ok(messageService.queryMessages(instanceId, topic, msgId, tag, key, startTime, endTime));
    }

    @GetMapping("/page")
    public Result<MessageQueryPageVO> queryMessagesPage(@RequestParam String instanceId,
            @RequestParam(required = false) String topic, @RequestParam(required = false) String msgId,
            @RequestParam(required = false) String tag, @RequestParam(required = false) String key,
            @RequestParam(required = false) Long startTime, @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(messageService.queryMessagesPage(instanceId, topic, msgId, tag, key, startTime, endTime,
                page, pageSize));
    }

    @GetMapping("/{msgId}/trace")
    public Result<TraceRecordVO> getMessageTrace(@PathVariable String msgId, @RequestParam String instanceId,
                                                 // Optional for the Apache/Aliyun providers; the Tencent
                                                 // provider requires a non-empty topic (DescribeMessageTrace).
                                                 @RequestParam(required = false) String topic) {
        return Result.ok(messageService.getMessageTrace(instanceId, msgId, topic));
    }

    @PostMapping("/resend")
    public Result<MessageResendResultVO> resendMessage(@Valid @RequestBody MessageResendRequestDTO request) {
        return Result.ok(messageService.resendMessage(request.getInstanceId(), request.getTopic(),
                request.getMsgId(), request.getConsumerGroup(), request.getClientId()));
    }
}
