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

package com.rocketmq.studio.auth;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.auth.security.StudioLoginException;
import com.rocketmq.studio.auth.security.StudioPrincipal;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.cluster.metrics.PrometheusException;
import com.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.GroupSequence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginReturnsNestedUserAndPassesRemoteAddressWithoutCaching() throws Exception {
        LoginVO mockResponse = LoginVO.builder()
                .token("mock-jwt-abc123")
                .expiresIn(86400)
                .user(LoginVO.UserInfo.builder()
                        .username("testuser")
                        .admin(false)
                        .build())
                .build();

        when(authService.login(any(LoginDTO.class), eq("203.0.113.9"))).thenReturn(mockResponse);

        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(mockRequest -> {
                            mockRequest.setRemoteAddr("203.0.113.9");
                            return mockRequest;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-abc123"))
                .andExpect(jsonPath("$.data.expiresIn").value(86400))
                .andExpect(jsonPath("$.data.user.username").value("testuser"))
                .andExpect(jsonPath("$.data.user.admin").value(false));

        verify(authService).login(any(LoginDTO.class), eq("203.0.113.9"));
    }

    @Test
    void loginRejectsUnacceptableResponseMediaTypeBeforeService() throws Exception {
        mockMvc.perform(validLogin().accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isNotAcceptable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsUnsupportedRequestMediaTypeBeforeService() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsMissingRequestMediaTypeBeforeService() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verifyNoInteractions(authService);
    }

    @Test
    void loginRejectsGetBeforeServiceAndAdvertisesPost() throws Exception {
        mockMvc.perform(get("/api/auth/login").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().string(""));

        verifyNoInteractions(authService);
    }

    @Test
    void loginTrailingSlashRemainsNotFound() throws Exception {
        mockMvc.perform(post("/api/auth/login/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"testpass\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        verify(authService, never()).login(any(LoginDTO.class), any());
    }

    @ParameterizedTest
    @MethodSource("invalidLoginBodies")
    void loginRejectsInvalidCredentialsWithFixedSafeBadRequest(String body) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid login request"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, never()).login(any(LoginDTO.class), any());
    }

    @ParameterizedTest
    @MethodSource("nonStringLoginBodies")
    void loginRejectsNonStringCredentialScalarsBeforeService(String body) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid login request"));

        verifyNoInteractions(authService);
    }

    @Test
    void loginValidationChecksLengthsBeforeCredentialContents() throws Exception {
        GroupSequence sequence = LoginDTO.class.getAnnotation(GroupSequence.class);

        assertThat(sequence).isNotNull();
        assertThat(Arrays.stream(sequence.value()).map(Class::getSimpleName))
                .containsExactly("LoginDTO", "ContentChecks");
        assertThat(LoginDTO.class.getDeclaredField("username").getAnnotation(Size.class))
                .satisfies(size -> {
                    assertThat(size.min()).isEqualTo(1);
                    assertThat(size.max()).isEqualTo(128);
                });
        assertThat(LoginDTO.class.getDeclaredField("password").getAnnotation(Size.class))
                .satisfies(size -> {
                    assertThat(size.min()).isEqualTo(1);
                    assertThat(size.max()).isEqualTo(72);
                });
        assertThat(LoginDTO.class.getDeclaredField("username").getAnnotation(Pattern.class))
                .satisfies(pattern -> {
                    assertThat(pattern.regexp()).isEqualTo("[A-Za-z0-9._@-]{1,128}");
                    assertThat(Arrays.stream(pattern.groups()).map(Class::getSimpleName))
                            .containsExactly("ContentChecks");
                });
        assertThat(LoginDTO.class.getDeclaredField("password").getAnnotation(NotBlank.class))
                .satisfies(notBlank ->
                        assertThat(Arrays.stream(notBlank.groups()).map(Class::getSimpleName))
                                .containsExactly("ContentChecks"));
    }

    @Test
    void loginRejectsMalformedJsonWithFixedSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid login request"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void loginMapsBusinessExceptionToFixedSafeBadRequest() throws Exception {
        when(authService.login(any(LoginDTO.class), any()))
                .thenThrow(new BusinessException(418, "underlying-business-marker"));

        mockMvc.perform(validLogin())
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid login request"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("underlying-business-marker"))));
    }

    @Test
    void loginMapsInvalidCredentialsToFixedUnauthorizedResponse() throws Exception {
        StudioLoginException exception = mock(StudioLoginException.class);
        when(exception.status()).thenReturn(HttpStatus.UNAUTHORIZED);
        when(exception.getMessage()).thenReturn("underlying-auth-marker");
        when(authService.login(any(LoginDTO.class), any()))
                .thenThrow(exception);

        mockMvc.perform(validLogin())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("underlying-auth-marker"))));
    }

    @Test
    void loginMapsRateLimitToFixedResponseWithPositiveRetryAfter() throws Exception {
        when(authService.login(any(LoginDTO.class), any()))
                .thenThrow(StudioLoginException.rateLimited(17));

        mockMvc.perform(validLogin())
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "17"))
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("Too many login attempts"));
    }

    @Test
    void loginMapsUnexpectedExceptionToFixedResponseWithoutDetails() throws Exception {
        when(authService.login(any(LoginDTO.class), any()))
                .thenThrow(new IllegalStateException("underlying-exception-marker"));

        mockMvc.perform(validLogin())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("underlying-exception-marker"))));
    }

    @Test
    void loginOverridesSpecializedExceptionHandlerWithFixedInternalServerError() throws Exception {
        when(authService.login(any(LoginDTO.class), any()))
                .thenThrow(new PrometheusException(503, "underlying-prometheus-marker"));

        mockMvc.perform(validLogin())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("underlying-prometheus-marker"))));
    }

    @Test
    void loginMapsUnknownStudioLoginStatusToFixedInternalServerError() throws Exception {
        StudioLoginException exception = mock(StudioLoginException.class);
        when(exception.status()).thenReturn(HttpStatus.BAD_GATEWAY);
        when(exception.getMessage()).thenReturn("underlying-unknown-auth-marker");
        when(authService.login(any(LoginDTO.class), any())).thenThrow(exception);

        mockMvc.perform(validLogin())
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Internal Server Error"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "underlying-unknown-auth-marker"))));
    }

    @Test
    void springDebugLogsDoNotContainLoginSecretsOrUnderlyingExceptionMessage() throws Exception {
        String username = "username-log-marker";
        String password = "password-log-marker";
        String token = "token-log-marker";
        String exceptionMessage = "underlying-log-marker";
        String unacceptableUsername = "accept-username-marker";
        String unacceptablePassword = "accept-password-marker";
        String overlongUsernameMarker = "overlong-username-marker";
        String overlongPasswordMarker = "overlong-password-marker";
        LoginVO response = LoginVO.builder()
                .token(token)
                .expiresIn(300)
                .user(LoginVO.UserInfo.builder()
                        .username("canonical-user")
                        .admin(false)
                        .build())
                .build();
        when(authService.login(any(LoginDTO.class), any())).thenReturn(response);

        Logger rootLogger = (Logger) getLogger(ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username
                                    + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_PLAIN)
                            .content("{\"username\":\"" + unacceptableUsername
                                    + "\",\"password\":\"" + unacceptablePassword + "\"}"))
                    .andExpect(status().isNotAcceptable());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + overlongUsernameMarker
                                    + "a".repeat(129) + "\",\"password\":\""
                                    + overlongPasswordMarker + "a".repeat(73) + "\"}"))
                    .andExpect(status().isBadRequest());

            reset(authService);
            when(authService.login(any(LoginDTO.class), any()))
                    .thenThrow(new IllegalStateException(exceptionMessage));
            mockMvc.perform(validLogin())
                    .andExpect(status().isInternalServerError());
        } finally {
            rootLogger.detachAppender(appender);
            appender.stop();
        }

        String messages = appender.list.stream()
                .map(AuthControllerTest::eventText)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(messages)
                .doesNotContain(username)
                .doesNotContain(password)
                .doesNotContain(token)
                .doesNotContain(exceptionMessage)
                .doesNotContain(unacceptableUsername)
                .doesNotContain(unacceptablePassword)
                .doesNotContain(overlongUsernameMarker)
                .doesNotContain(overlongPasswordMarker);
    }

    @Test
    void logoutShouldReturnSuccess() throws Exception {
        UUID sessionId = UUID.fromString("ac0431cc-2d1f-491f-b622-7ee12b537870");
        StudioPrincipal principal = new StudioPrincipal(sessionId, "testuser", Role.USER);
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        doNothing().when(authService).logout(sessionId);

        TestSecurityContextHolder.setAuthentication(authentication);
        try {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("success"));
        } finally {
            TestSecurityContextHolder.clearContext();
        }

        verify(authService).logout(sessionId);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        validLogin() {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"testuser\",\"password\":\"testpass\"}");
    }

    private static Stream<String> invalidLoginBodies() {
        return Stream.of(
                "{}",
                "{\"password\":\"testpass\"}",
                "{\"username\":\"testuser\"}",
                "{\"username\":\" \",\"password\":\"\"}",
                "{\"username\":\"álîce\",\"password\":\"testpass\"}",
                "{\"username\":\"" + "a".repeat(129) + "\",\"password\":\"testpass\"}",
                "{\"username\":\"testuser\",\"password\":\"" + "a".repeat(73) + "\"}",
                "{\"username\":\"testuser\",\"password\":\"" + " ".repeat(73) + "\"}"
        );
    }

    private static Stream<String> nonStringLoginBodies() {
        return Stream.of(
                "{\"username\":123,\"password\":\"testpass\"}",
                "{\"username\":true,\"password\":\"testpass\"}",
                "{\"username\":[],\"password\":\"testpass\"}",
                "{\"username\":{},\"password\":\"testpass\"}",
                "{\"username\":\"testuser\",\"password\":123}",
                "{\"username\":\"testuser\",\"password\":false}",
                "{\"username\":\"testuser\",\"password\":[]}",
                "{\"username\":\"testuser\",\"password\":{}}"
        );
    }

    private static String eventText(ILoggingEvent event) {
        return event.getFormattedMessage() + throwableText(event.getThrowableProxy());
    }

    private static String throwableText(IThrowableProxy throwable) {
        if (throwable == null) {
            return "";
        }
        StringBuilder text = new StringBuilder()
                .append('\n')
                .append(throwable.getClassName())
                .append(": ")
                .append(throwable.getMessage());
        Arrays.stream(throwable.getStackTraceElementProxyArray())
                .forEach(element -> text.append('\n').append(element));
        Arrays.stream(throwable.getSuppressed())
                .forEach(suppressed -> text.append(throwableText(suppressed)));
        text.append(throwableText(throwable.getCause()));
        return text.toString();
    }
}
