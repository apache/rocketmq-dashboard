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

package com.rocketmq.studio.ops;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;

    @GetMapping("/homePage")
    public Result<OpsHomeVO> homePage() {
        return Result.ok(opsService.getHomePage());
    }

    @PostMapping("/updateNameSvrAddr")
    public Result<Void> updateNameSvrAddr(@RequestBody OpsNameServerDTO request) {
        opsService.updateNameServer(request.getNamesrvAddr());
        return Result.ok();
    }

    @PostMapping("/addNameSvrAddr")
    public Result<Void> addNameSvrAddr(@RequestBody OpsNameServerDTO request) {
        opsService.addNameServer(request.getNamesrvAddr());
        return Result.ok();
    }

    @PostMapping("/updateIsVIPChannel")
    public Result<Void> updateIsVIPChannel(@RequestBody OpsVipChannelDTO request) {
        opsService.updateVipChannel(request.isUseVIPChannel());
        return Result.ok();
    }

    @PostMapping("/updateUseTLS")
    public Result<Void> updateUseTLS(@RequestBody OpsTlsDTO request) {
        opsService.updateUseTLS(request.isUseTLS());
        return Result.ok();
    }
}
