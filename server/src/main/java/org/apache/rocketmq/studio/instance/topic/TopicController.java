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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final MetadataService metadataService;
    private final org.apache.rocketmq.studio.instance.InstanceService instanceService;

    @GetMapping
    public Result<List<TopicVO>> listTopics(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return Result.ok(metadataService.listTopics(instanceId, clusterId, type, search));
    }

    @GetMapping("/page")
    public Result<PageResult<TopicVO>> listTopicsPage(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(metadataService.listTopicsPage(instanceId, clusterId, type, search, page, pageSize));
    }

    @GetMapping("/export")
    public Result<String> exportTopics(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String names) {
        return Result.ok(metadataService.exportTopics(instanceId, type, search, parseNames(names)));
    }

    @PostMapping("/import")
    public Result<ImportTopicsResultVO> importTopics(@Valid @RequestBody ImportTopicsDTO request) {
        String instanceId = instanceService.normalizeIdentifier(request.getInstanceId());
        return Result.ok(metadataService.importTopics(instanceId, request.getTopics()));
    }

    @PostMapping("/create")
    public Result<TopicVO> createTopic(@Valid @RequestBody(required = false) CreateTopicDTO topic) {
        requireCreateTopicRequest(topic);
        TopicVO vo = topic.toTopicVO();
        vo.setInstanceId(instanceService.normalizeIdentifier(topic.getInstanceId()));
        return Result.ok(metadataService.createTopic(vo));
    }

    @PostMapping("/update")
    public Result<TopicVO> updateTopic(@Valid @RequestBody(required = false) UpdateTopicDTO topic) {
        requireTopicRequest(topic);
        TopicVO vo = topic.toTopicVO();
        vo.setInstanceId(instanceService.normalizeIdentifier(topic.getInstanceId()));
        return Result.ok(metadataService.updateTopic(vo));
    }

    @PostMapping("/delete")
    public Result<Void> deleteTopic(@Valid @RequestBody(required = false) DeleteTopicDTO request) {
        requireDeleteTopicRequest(request);
        metadataService.deleteTopic(request.getInstanceId(), request.getName());
        return Result.ok();
    }

    @GetMapping("/{name}/routes")
    public Result<List<BrokerRouteVO>> getTopicRoutes(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(metadataService.getTopicRoutes(instanceId, name));
    }

    @GetMapping("/{name}/consumers")
    public Result<List<TopicConsumerVO>> getTopicConsumers(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId) {
        return Result.ok(metadataService.getTopicConsumers(instanceId, name));
    }

    @GetMapping("/{name}/consumers/page")
    public Result<TopicConsumerPageVO> getTopicConsumersPage(
            @PathVariable String name,
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(metadataService.getTopicConsumersPage(instanceId, name, page, pageSize));
    }

    @PostMapping("/send")
    public Result<SendMessageVO> sendMessage(@Valid @RequestBody(required = false) SendMessageDTO request) {
        requireSendMessageRequest(request);
        return Result.ok(metadataService.sendMessage(request));
    }

    private void requireTopicRequest(UpdateTopicDTO topic) {
        if (topic == null) {
            throw new BusinessException(400, "Topic request is required");
        }
    }

    private void requireCreateTopicRequest(CreateTopicDTO topic) {
        if (topic == null) {
            throw new BusinessException(400, "Topic request is required");
        }
    }

    private void requireDeleteTopicRequest(DeleteTopicDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Topic delete request is required");
        }
    }

    private void requireSendMessageRequest(SendMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Topic send message request is required");
        }
    }

    private List<String> parseNames(String names) {
        if (names == null || names.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(names.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }
}
