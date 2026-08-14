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
package org.apache.rocketmq.studio.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/studio-users")
@RequiredArgsConstructor
public class StudioUserController {

    private final AuthService authService;

    @GetMapping
    public Result<List<StudioUserVO>> list() {
        return Result.ok(authService.listUsers().stream().map(StudioUserVO::from).toList());
    }

    @PostMapping
    public Result<StudioUserVO> create(@Valid @RequestBody CreateStudioUserDTO request) {
        return Result.ok(StudioUserVO.from(authService.createUser(
                request.getUsername(), request.getPassword(), request.isAdmin())));
    }

    @PostMapping("/{userId}/status")
    public Result<StudioUserVO> updateStatus(@PathVariable String userId,
                                              @Valid @RequestBody UpdateStudioUserStatusDTO request) {
        return Result.ok(StudioUserVO.from(authService.setUserEnabled(userId, request.getEnabled())));
    }

    @PostMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable String userId,
                                      @Valid @RequestBody ResetPasswordDTO request) {
        authService.changePassword(userId, null, request.getNewPassword(), false);
        return Result.ok();
    }
}
