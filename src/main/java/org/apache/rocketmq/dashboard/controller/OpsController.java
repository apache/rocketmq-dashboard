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
import org.apache.rocketmq.dashboard.permisssion.Permission;
import org.apache.rocketmq.dashboard.service.OpsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ops")
@Permission
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;

    @GetMapping(value = "/homePage.query")
    public ResponseEntity<Object> homePage() {
        return ResponseEntity.ok(opsService.homePageInfo());
    }

    @PostMapping(value = "/updateNameSvrAddr.do")
    public ResponseEntity<Object> updateNameSvrAddr(@RequestParam String nameSvrAddrList) {
        opsService.updateNameSvrAddrList(nameSvrAddrList);
        return ResponseEntity.ok(true);
    }

    @PostMapping(value = "/updateIsVIPChannel.do")
    public ResponseEntity<Object> updateIsVIPChannel(@RequestParam String useVIPChannel) {
        opsService.updateIsVIPChannel(useVIPChannel);
        return ResponseEntity.ok(true);
    }

    @GetMapping(value = "/rocketMqStatus.query")
    public ResponseEntity<Object> clusterStatus() {
        return ResponseEntity.ok(opsService.rocketMqStatusCheck());
    }

    @PostMapping(value = "/updateUseTLS.do")
    public ResponseEntity<Object> updateUseTLS(@RequestParam String useTLS) {
        opsService.updateUseTLS(Boolean.parseBoolean(useTLS));
        return ResponseEntity.ok(true);
    }
}
