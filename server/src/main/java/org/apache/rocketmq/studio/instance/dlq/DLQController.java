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
package org.apache.rocketmq.studio.instance.dlq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DLQController {

    private final DLQService dlqService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<List<DLQGroupVO>> listDLQGroups(@RequestParam String instanceId) {
        return Result.ok(dlqService.listDLQGroups(instanceId));
    }

    @PostMapping("/resend")
    public Result<DLQResendResultVO> resendMessages(@Valid @RequestBody(required = false) DLQResendRequestDTO request) {
        requireRequest(request);
        return Result.ok(dlqService.resendMessages(request.getInstanceId(), request.getGroupName(),
                request.getStartTime(), request.getEndTime(), request.getTargetTopic()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportDLQMessages(@RequestParam String instanceId,
                                                    @RequestParam String groupName,
                                                    @RequestParam(required = false) Long startTime,
                                                    @RequestParam(required = false) Long endTime,
                                                    @RequestParam(required = false) Integer maxCount) {
        List<DLQMessageVO> messages = dlqService.exportMessages(
                instanceId, groupName, startTime, endTime, maxCount);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(messages);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "Failed to serialize DLQ export");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dlq-" + groupName + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private void requireRequest(DLQResendRequestDTO request) {
        if (request == null) {
            throw new BusinessException(400, "DLQ resend request is required");
        }
    }
}
