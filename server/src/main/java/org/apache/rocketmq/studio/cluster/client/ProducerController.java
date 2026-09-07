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
package org.apache.rocketmq.studio.cluster.client;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerConnectionService producerConnectionService;

    @GetMapping("/groups")
    public Result<List<String>> listProducerGroups(
            @RequestParam String instanceId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer limit) {
        return Result.ok(producerConnectionService.listProducerGroups(instanceId, topic, query, limit));
    }

    @GetMapping("/connection")
    public ProducerConnectionResultVO listConnections(
            @RequestParam(required = false) String instanceId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String producerGroup) {
        requireParameter(instanceId, "instanceId");
        requireParameter(topic, "topic");
        return new ProducerConnectionResultVO(
                producerConnectionService.listConnections(instanceId, topic, producerGroup));
    }

    private void requireParameter(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, name + " is required");
        }
    }
}
