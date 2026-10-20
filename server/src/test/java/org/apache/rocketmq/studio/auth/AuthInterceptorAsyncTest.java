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

import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthInterceptorAsyncTest {
    private final AuthService authService = mock(AuthService.class);
    private final StreamController controller = new StreamController();

    @AfterEach
    void clearContext() {
        controller.emitter.complete();
        AuthenticatedUserContext.clear();
    }

    @Test
    void asyncHandoffClearsOriginalThreadAndRedispatchAuthenticatesAgainTest() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        when(authService.getAuthenticatedUser("Bearer token"))
                .thenReturn(Optional.of(new LoginVO.UserInfo(17L, "stream-admin", true)));
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService,
                mock(SettingsRepository.class), mock(ToolCatalog.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();

        MvcResult pending = mvc.perform(get("/api/context-test/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(controller.username).isEqualTo("stream-admin");
        assertThat(controller.userId).isEqualTo("17");
        assertThat(controller.admin).isTrue();
        assertCleanContext();

        // Async redispatch must reauthenticate rather than relying on the initial principal.
        when(authService.getAuthenticatedUser("Bearer token")).thenReturn(Optional.empty());
        controller.emitter.complete();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isUnauthorized());
        assertCleanContext();
    }

    @Test
    void synchronousCompletionStillClearsRequestThreadTest() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        when(authService.getAuthenticatedUser("Bearer token"))
                .thenReturn(Optional.of(new LoginVO.UserInfo(17L, "stream-admin", true)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new AuthInterceptor(properties, authService,
                        mock(SettingsRepository.class), mock(ToolCatalog.class))).build();

        mvc.perform(get("/api/context-test/sync").header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(status().isOk());

        assertThat(controller.username).isEqualTo("stream-admin");
        assertCleanContext();
    }

    private static void assertCleanContext() {
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
        assertThat(AuthenticatedUserContext.currentUserId()).isNull();
        assertThat(AuthenticatedUserContext.currentUserIsAdmin()).isFalse();
    }

    @RestController
    static class StreamController {
        private final SseEmitter emitter = new SseEmitter(60_000L);
        private String username;
        private String userId;
        private boolean admin;

        @GetMapping(value = "/api/context-test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            captureIdentity();
            return emitter;
        }

        @GetMapping("/api/context-test/sync")
        String sync() {
            captureIdentity();
            return "ok";
        }

        private void captureIdentity() {
            username = AuthenticatedUserContext.currentUsernameOrSystem();
            userId = AuthenticatedUserContext.currentUserId();
            admin = AuthenticatedUserContext.currentUserIsAdmin();
        }
    }
}
