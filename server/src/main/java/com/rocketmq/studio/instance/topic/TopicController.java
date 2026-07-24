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
package com.rocketmq.studio.instance.topic;

import com.rocketmq.studio.common.domain.Result;
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
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final MetadataService metadataService;

    @GetMapping
    public Result<List<TopicVO>> listTopics(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        return Result.ok(metadataService.listTopics(clusterId, type, search));
    }

    @PostMapping("/create")
    public Result<TopicVO> createTopic(@RequestBody TopicVO topic) {
        return Result.ok(metadataService.createTopic(topic));
    }

    @PostMapping("/update")
    public Result<TopicVO> updateTopic(@RequestBody TopicVO topic) {
        return Result.ok(metadataService.updateTopic(topic));
    }

    @PostMapping("/delete")
    public Result<Void> deleteTopic(@Valid @RequestBody DeleteTopicDTO request) {
        metadataService.deleteTopic(request.getName());
        return Result.ok();
    }

    @GetMapping("/{name}/routes")
    public Result<List<BrokerRouteVO>> getTopicRoutes(@PathVariable String name) {
        return Result.ok(metadataService.getTopicRoutes(name));
    }

    @GetMapping("/{name}/consumers")
    public Result<List<TopicConsumerVO>> getTopicConsumers(@PathVariable String name) {
        return Result.ok(metadataService.getTopicConsumers(name));
    }

    @PostMapping("/send")
    public Result<SendMessageVO> sendMessage(@RequestBody SendMessageDTO request) {
        return Result.ok(metadataService.sendMessage(request));
    }
}
