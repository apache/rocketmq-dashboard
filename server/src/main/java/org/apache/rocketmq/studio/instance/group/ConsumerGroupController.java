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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.topic.MetadataService;

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
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class ConsumerGroupController {

    private final MetadataService metadataService;
    private final ConsumerDiagnosticsService consumerDiagnosticsService;
    private final org.apache.rocketmq.studio.instance.InstanceService instanceService;

    @GetMapping
    public Result<List<ConsumerGroupVO>> listConsumerGroups(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String search) {
        return Result.ok(metadataService.listConsumerGroups(instanceId, clusterId, search));
    }

    @GetMapping("/page")
    public Result<PageResult<ConsumerGroupVO>> listConsumerGroupsPage(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(metadataService.listConsumerGroupsPage(instanceId, clusterId, search, page, pageSize));
    }

    @GetMapping("/{name}")
    public Result<ConsumerGroupVO> getConsumerGroup(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(metadataService.getConsumerGroup(instanceId, name));
    }

    @GetMapping("/{name}/settings")
    public Result<ConsumerGroupSettingsVO> getConsumerGroupSettings(@PathVariable String name,
                                                                      @RequestParam String instanceId) {
        return Result.ok(metadataService.getConsumerGroupSettings(instanceId, name));
    }

    @PostMapping("/settings")
    public Result<ConsumerGroupSettingsVO> updateConsumerGroupSettings(
            @Valid @RequestBody UpdateConsumerGroupSettingsDTO request) {
        return Result.ok(metadataService.updateConsumerGroupSettings(request.getInstanceId(), request.getName(),
                request.getRetryQueueNums(), request.getRetryMaxTimes()));
    }

    @GetMapping("/{name}/progress")
    public Result<List<QueueProgressVO>> getGroupProgress(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(metadataService.getGroupProgress(instanceId, name));
    }

    @GetMapping("/{name}/subscriptions")
    public Result<List<SubscriptionEntryVO>> getGroupSubscriptions(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(metadataService.getGroupSubscriptions(instanceId, name));
    }

    @GetMapping("/{name}/instances/{clientId}/stack")
    public Result<ConsumerStackTraceVO> getConsumerStack(
            @PathVariable String name,
            @PathVariable String clientId,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(consumerDiagnosticsService.getConsumerStack(instanceId, name, clientId));
    }

    @PostMapping("/create")
    public Result<ConsumerGroupVO> createConsumerGroup(@Valid @RequestBody CreateConsumerGroupDTO group) {
        ConsumerGroupVO vo = group.toConsumerGroupVO();
        vo.setInstanceId(instanceService.normalizeIdentifier(group.getInstanceId()));
        return Result.ok(metadataService.createConsumerGroup(vo));
    }

    @PostMapping("/delete")
    public Result<Void> deleteConsumerGroup(@Valid @RequestBody DeleteConsumerGroupDTO request) {
        metadataService.deleteConsumerGroup(request.getInstanceId(), request.getName());
        return Result.ok();
    }

    @PostMapping("/reset-offset")
    public Result<Void> resetOffset(@Valid @RequestBody ResetConsumerOffsetDTO request) {
        metadataService.resetOffset(request.getInstanceId(), request.getName(),
                request.getTimestamp(), request.getTopic());
        return Result.ok();
    }
}
