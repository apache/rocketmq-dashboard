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
     * Reports the policy {@link AuthInterceptor} actually enforces, and answers exactly as it does.
     * Both read the same static property and the same runtime "requireLogin" row, so the failure
     * branches have to line up too: the frontend reads this flag to choose between the login page
     * and the console, and a policy that fails differently in the two places strands the console.
     * The interceptor fails closed (see its own {@code isLoginRequired}), so an unreadable policy
     * still demands a login here; letting the read failure escape as a 500 instead used to leave the
     * frontend on its error screen, where the retry could never succeed and the login page that
     * would in fact work was unreachable.
     */
    private boolean isLoginRequired() {
        if (authProperties.isLoginRequired()) {
            return true;
        }
        try {
            GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
            // Fail closed, matching the interceptor: an absent policy row means it still demands a
            // login, so this endpoint must report that rather than the opposite.
            return settings == null || settings.isRequireLogin();
        } catch (Exception exception) {
            return true;
        }
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody(required = false) LoginDTO request,
                                 HttpServletRequest servletRequest,
                                 HttpServletResponse response) {
        if (request == null) {
            throw new BusinessException(400, "Login request is required");
        }
        LoginVO login = authService.login(request);
        if (AuthCookie.requestsBearerToken(servletRequest)) {
            return Result.ok(login);
        }
        AuthCookie.write(response, authProperties, login.getToken(), Duration.ofSeconds(login.getExpiresIn()));
        login.setToken(null);
        return Result.ok(login);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(AuthCookie.authorization(request, authProperties));
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
        return Result.ok();
    }
}
