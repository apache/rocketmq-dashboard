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

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final SettingsRepository settingsRepository;

    @GetMapping("/status")
    public ResponseEntity<Result<AuthStatusVO>> status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AuthStatusVO status = AuthStatusVO.builder()
                .loginRequired(isLoginRequired())
                .authenticated(authService.isAuthenticated(authorization))
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(status));
    }

    private boolean isLoginRequired() {
        if (authProperties.isLoginRequired()) {
            return true;
        }
        GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
        return settings != null && settings.isRequireLogin();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody(required = false) LoginDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Login request is required");
        }
        return Result.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                               String authorization) {
        authService.logout(authorization);
        return Result.ok();
    }
}
