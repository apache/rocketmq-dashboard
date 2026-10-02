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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final SettingsRepository settingsRepository;
    private final OperationAuditService operationAuditService;

    @GetMapping("/status")
    public ResponseEntity<Result<AuthStatusVO>> status(
            HttpServletRequest request) {
        Optional<LoginVO.UserInfo> user = authService.getAuthenticatedUser(
                AuthCookie.authorization(request, authProperties));
        AuthStatusVO status = AuthStatusVO.builder()
                .loginRequired(isLoginRequired())
                .authenticated(user.isPresent())
                .user(user.orElse(null))
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(status));
    }

    /**
     * Records an authentication event for an explicit actor. A login attempt happens before the
     * request has a principal (the interceptor never runs for {@code /api/auth/login}) and a logout
     * has just given its token up, so the acting operator is published for the duration of the
     * audit call instead of being read from the request context.
     */
    private void recordAs(String operator, String operationType, String resourceType,
                          String target, String detail, String result) {
        AuthenticatedUserContext.setUsername(operator);
        try {
            operationAuditService.record(operationType, resourceType, target, null, detail, result,
                    null);
        } finally {
            AuthenticatedUserContext.clear();
        }
    }

    private boolean isLoginRequired() {
        if (authProperties.isLoginRequired()) {
            return true;
        }
        GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
        return settings != null && settings.isRequireLogin();
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody(required = false) LoginDTO request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse response) {
        if (request == null) {
            throw new BusinessException(400, "Login request is required");
        }
        LoginVO login;
        try {
            login = authService.login(request);
        } catch (BusinessException failure) {
            // The answered message is recorded verbatim: a failed login leaves the same uniform
            // information the caller received and never reveals whether the account exists.
            recordAs(request.getUsername(), "LOGIN", "AUTH", request.getUsername(),
                    failure.getMessage(), "FAILURE");
            throw failure;
        }
        recordAs(request.getUsername(), "LOGIN", "AUTH", request.getUsername(), null, "SUCCESS");
        if (AuthCookie.requestsBearerToken(servletRequest)) {
            return Result.ok(login);
        }
        AuthCookie.write(response, authProperties, login.getToken(), Duration.ofSeconds(login.getExpiresIn()));
        login.setToken(null);
        return Result.ok(login);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String authorization = AuthCookie.authorization(request, authProperties);
        // Resolved before the token is revoked: afterwards no principal is left to name.
        String username = authService.getAuthenticatedUser(authorization)
                .map(LoginVO.UserInfo::getUsername).orElse(null);
        authService.logout(authorization);
        if (username != null) {
            recordAs(username, "LOGOUT", "AUTH", username, null, "SUCCESS");
        }
        AuthCookie.clear(response, authProperties);
        return Result.ok();
    }

    @PostMapping("/password")
    public Result<Void> changePassword(
            HttpServletRequest servletRequest,
            @Valid @RequestBody ChangePasswordDTO request) {
        LoginVO.UserInfo user = authService.getAuthenticatedUser(AuthCookie.authorization(servletRequest, authProperties))
                .orElseThrow(() -> new BusinessException(401, "Unauthorized"));
        if (user.getUserId() == null) {
            throw new BusinessException(503, "Studio user management is not initialized");
        }
        authService.changePassword(user.getUserId(), request.getCurrentPassword(), request.getNewPassword(), true);
        recordAs(user.getUsername(), "UPDATE_OWN_PASSWORD", "AUTH", user.getUsername(), null, "SUCCESS");
        return Result.ok();
    }
}
