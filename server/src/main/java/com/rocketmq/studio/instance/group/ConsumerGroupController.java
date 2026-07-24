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
package com.rocketmq.studio.instance.group;

import com.rocketmq.studio.instance.topic.MetadataService;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ConsumerGroupController {

    private final MetadataService metadataService;
    private final ConsumerDiagnosticsService consumerDiagnosticsService;

    @GetMapping
    public Result<List<ConsumerGroupVO>> listConsumerGroups(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String search) {
        return Result.ok(metadataService.listConsumerGroups(clusterId, search));
    }

    @GetMapping("/{name}")
    public Result<ConsumerGroupVO> getConsumerGroup(@PathVariable String name) {
        return Result.ok(metadataService.getConsumerGroup(name));
    }

    @GetMapping("/{name}/progress")
    public Result<List<QueueProgressVO>> getGroupProgress(@PathVariable String name) {
        return Result.ok(metadataService.getGroupProgress(name));
    }

    @GetMapping("/{name}/subscriptions")
    public Result<List<SubscriptionEntryVO>> getGroupSubscriptions(@PathVariable String name) {
        return Result.ok(metadataService.getGroupSubscriptions(name));
    }

    @GetMapping("/{name}/instances/{clientId}/stack")
    public Result<ConsumerStackTraceVO> getConsumerStack(
            @PathVariable String name,
            @PathVariable String clientId) {
        return Result.ok(consumerDiagnosticsService.getConsumerStack(name, clientId));
    }

    @PostMapping("/create")
    public Result<ConsumerGroupVO> createConsumerGroup(@RequestBody ConsumerGroupVO group) {
        return Result.ok(metadataService.createConsumerGroup(group));
    }

    @PostMapping("/delete")
    public Result<Void> deleteConsumerGroup(@RequestBody Map<String, String> request) {
        metadataService.deleteConsumerGroup(request.get("name"));
        return Result.ok();
    }

    @PostMapping("/reset-offset")
    public Result<Void> resetOffset(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        long timestamp = ((Number) request.get("timestamp")).longValue();
        String topic = (String) request.get("topic");
        metadataService.resetOffset(name, timestamp, topic);
        return Result.ok();
    }
}
