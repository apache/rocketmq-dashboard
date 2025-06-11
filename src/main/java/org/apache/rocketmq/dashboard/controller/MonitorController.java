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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.model.ConsumerMonitorConfig;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.MonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/monitor")
@Permission
@RequiredArgsConstructor
@Slf4j
public class MonitorController {

    private final MonitorService monitorService;

    @PostMapping(value = "/createOrUpdateConsumerMonitor.do")
    public ResponseEntity<Object> createOrUpdateConsumerMonitor(@RequestParam String consumeGroupName, @RequestParam int minCount,
                                                               @RequestParam int maxDiffTotal) {
        return ResponseEntity.ok(monitorService.createOrUpdateConsumerMonitor(consumeGroupName, new ConsumerMonitorConfig(minCount, maxDiffTotal)));
    }

    @GetMapping(value = "/consumerMonitorConfig.query")
    public ResponseEntity<Object> consumerMonitorConfig() {
        return ResponseEntity.ok(monitorService.queryConsumerMonitorConfig());
    }

    @GetMapping(value = "/consumerMonitorConfigByGroupName.query")
    public ResponseEntity<Object> consumerMonitorConfigByGroupName(@RequestParam String consumeGroupName) {
        return ResponseEntity.ok(monitorService.queryConsumerMonitorConfigByGroupName(consumeGroupName));
    }

    @PostMapping(value = "/deleteConsumerMonitor.do")
    public ResponseEntity<Object> deleteConsumerMonitor(@RequestParam String consumeGroupName) {
        return ResponseEntity.ok(monitorService.deleteConsumerMonitor(consumeGroupName));
    }
}
