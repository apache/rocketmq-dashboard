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
package com.rocketmq.studio.instance.dlq;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DLQController {

    private final DLQService dlqService;

    @GetMapping
    public Result<List<DLQGroupVO>> listDLQGroups(@RequestParam(required = false) String clusterId) {
        return Result.ok(dlqService.listDLQGroups(clusterId));
    }

    @PostMapping("/resend")
    public Result<Void> resendMessages(@RequestBody Map<String, Object> request) {
        String groupName = (String) request.get("groupName");
        Long startTime = request.get("startTime") != null ? ((Number) request.get("startTime")).longValue() : null;
        Long endTime = request.get("endTime") != null ? ((Number) request.get("endTime")).longValue() : null;
        String targetTopic = (String) request.get("targetTopic");
        dlqService.resendMessages(groupName, startTime, endTime, targetTopic);
        return Result.ok();
    }
}
