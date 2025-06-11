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

import com.google.common.base.Strings;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@Permission
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping(value = "/broker.query")
    public ResponseEntity<Object> broker(@RequestParam String date) {
        return ResponseEntity.ok(dashboardService.queryBrokerData(date));
    }

    @GetMapping(value = "/topic.query")
    public ResponseEntity<Object> topic(@RequestParam String date, String topicName) {
        if (Strings.isNullOrEmpty(topicName)) {
            return ResponseEntity.ok(dashboardService.queryTopicData(date));
        }
        return ResponseEntity.ok(dashboardService.queryTopicData(date, topicName));
    }

    @GetMapping(value = "/topicCurrent")
    public ResponseEntity<Object> topicCurrent() {
        return ResponseEntity.ok(dashboardService.queryTopicCurrentData());
    }

}
