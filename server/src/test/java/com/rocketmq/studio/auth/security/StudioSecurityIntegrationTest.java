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
package com.rocketmq.studio.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.auth.security.StudioAuthorizationPolicy.Access;
import com.rocketmq.studio.auth.security.StudioAuthorizationPolicy.Route;
import com.rocketmq.studio.auth.security.StudioSessionStore.Session;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.cluster.broker.ClusterService;
import com.rocketmq.studio.cluster.k8s.K8sCertService;
import com.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import com.rocketmq.studio.cluster.metrics.MetricsService;
import com.rocketmq.studio.instance.acl.AclService;
import com.rocketmq.studio.ops.ai.AiService;
import com.rocketmq.studio.ops.ai.ChatDTO;
import com.rocketmq.studio.ops.audit.AuditService;
import com.rocketmq.studio.settings.SettingsService;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.WebUtils;
import org.springframework.web.util.pattern.PathPattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StudioSecurityIntegrationTest.TestTimeConfiguration.class)
class StudioSecurityIntegrationTest {
    private static final String USERNAME = "operator";
    private static final String PASSWORD = "operator-password";
    private static final String ADMIN_USERNAME = "administrator";
    private static final String ADMIN_PASSWORD = "administrator-password";
    private static final String CHANGED_PASSWORD = "changed-password";
    private static final String USER_HASH =
        "{bcrypt}" + new BCryptPasswordEncoder(12).encode(PASSWORD);
    private static final String ADMIN_HASH =
        "{bcrypt}" + new BCryptPasswordEncoder(12).encode(ADMIN_PASSWORD);
    private static final String CHANGED_HASH =
        "{bcrypt}" + new BCryptPasswordEncoder(12).encode(CHANGED_PASSWORD);
    private static final String VALID_TOKEN_SHAPE = "A".repeat(43);
    private static final String UNAUTHORIZED_JSON =
        "{\"code\":401,\"message\":\"Unauthorized\",\"data\":null}";
    private static final String FORBIDDEN_JSON =
        "{\"code\":403,\"message\":\"Forbidden\",\"data\":null}";
    private static final Path TEST_DIRECTORY = Path.of(
        "target",
        "studio-security-" + UUID.randomUUID()
    ).toAbsolutePath();
    private static final Path USER_FILE = TEST_DIRECTORY.resolve("users.json");
    private static final Set<Route> REVIEWED_USER_ALLOWLIST = Set.of(
        user(HttpMethod.POST, "/api/auth/logout"),
        user(HttpMethod.POST, "/api/metrics/query"),
        user(HttpMethod.POST, "/api/ai/chat"),
        user(HttpMethod.GET, "/api/dashboard"),
        user(HttpMethod.GET, "/api/clusters"),
        user(HttpMethod.GET, "/api/clusters/{id}"),
        user(HttpMethod.GET, "/api/clients"),
        user(HttpMethod.GET, "/api/instances"),
        user(HttpMethod.GET, "/api/topics"),
        user(HttpMethod.GET, "/api/topics/{name}/routes"),
        user(HttpMethod.GET, "/api/topics/{name}/consumers"),
        user(HttpMethod.GET, "/api/namespaces"),
        user(HttpMethod.GET, "/api/groups"),
        user(HttpMethod.GET, "/api/groups/{name}"),
        user(HttpMethod.GET, "/api/groups/{name}/progress"),
        user(HttpMethod.GET, "/api/groups/{name}/subscriptions"),
        user(HttpMethod.GET, "/api/messages"),
        user(HttpMethod.GET, "/api/messages/{msgId}/trace"),
        user(HttpMethod.GET, "/api/dlq"),
        user(HttpMethod.GET, "/api/alert-rules"),
        user(HttpMethod.GET, "/api/system-alerts"),
        user(HttpMethod.GET, "/api/ai/tools")
    );
    private static final Set<Route> SENSITIVE_ADMIN_GETS = Set.of(
        admin(HttpMethod.GET, "/api/settings/general"),
        admin(HttpMethod.GET, "/api/settings/datasources"),
        admin(HttpMethod.GET, "/api/acl/users"),
        admin(HttpMethod.GET, "/api/acl/rules"),
        admin(HttpMethod.GET, "/api/audit-logs"),
        admin(HttpMethod.GET, "/api/k8s-certs")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TestTicker ticker;

    @Autowired
    private StudioSessionStore sessions;

    @Autowired
    private StudioUserRegistry registry;

    @Autowired
    private StudioAuthorizationPolicy policy;

    @Autowired
    private StudioSecurityResponses securityResponses;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private SettingsService settingsService;

    @MockBean
    private AclService aclService;

    @MockBean
    private AuditService auditService;

    @MockBean
    private K8sCertService k8sCertService;

    @MockBean
    private MetricsService metricsService;

    @MockBean
    private AiService aiService;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        createTestDirectory();
        registry.add("studio.security.user-file", USER_FILE::toString);
        registry.add("studio.security.session-ttl", () -> "5m");
        registry.add("studio.security.registry-check-interval", () -> "250ms");
    }

    @BeforeEach
    void resetUsersAndTime() throws IOException {
        reset(
            clusterService,
            settingsService,
            aclService,
            auditService,
            k8sCertService,
            metricsService,
            aiService
        );
        clock.advance(Duration.ofDays(1));
        ticker.advance(Duration.ofSeconds(1));
        writeUsers(
            userJson(USERNAME, USER_HASH, "USER"),
            userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN")
        );
    }

    @AfterAll
    static void deleteTemporaryUsers() throws IOException {
        Files.deleteIfExists(USER_FILE);
        Files.deleteIfExists(TEST_DIRECTORY);
    }

    @Test
    void publicLoginWorksWithoutAuthorizationOrHttpSession() throws Exception {
        mockMvc.perform(loginRequest())
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(cookie().doesNotExist("JSESSIONID"))
            .andExpect(jsonPath("$.data.token").isString());
    }

    @ParameterizedTest
    @MethodSource("ignoredPublicAuthorizationValues")
    void publicLoginIgnoresStaleAndMalformedAuthorization(String authorization) throws Exception {
        mockMvc.perform(loginRequest().header(HttpHeaders.AUTHORIZATION, authorization))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isString());
    }

    @Test
    void publicLoginIgnoresMultipleAuthorizationHeaders() throws Exception {
        mockMvc.perform(loginRequest().header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + VALID_TOKEN_SHAPE,
                "Basic Zm9vOmJhcg=="
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").isString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator/health/liveness",
        "/actuator/health/readiness"
    })
    void healthProbesArePublicAndIgnoreAuthorization(String path) throws Exception {
        mockMvc.perform(get(path).header(
                HttpHeaders.AUTHORIZATION,
                "Bearer malformed-public-probe"
            ))
            .andExpect(status().isOk())
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void protectedRouteWithoutTokenReturnsExactUnauthorizedResponse() throws Exception {
        assertUnauthorized(get("/api/clusters/cluster-a"));
        verifyNoInteractions(clusterService);
    }

    @Test
    void validUserReachesAllowlistedControllerWithCredentialFreePrincipal() throws Exception {
        AtomicReference<Authentication> observed = new AtomicReference<>();
        when(clusterService.getCluster("cluster-a")).thenAnswer(invocation -> {
            observed.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        });
        String token = login(USERNAME, PASSWORD);

        mockMvc.perform(get("/api/clusters/cluster-a")
                .header(HttpHeaders.AUTHORIZATION, "bEaReR " + token))
            .andExpect(status().isOk())
            .andExpect(cookie().doesNotExist("JSESSIONID"));

        Authentication authentication = observed.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(StudioPrincipal.class);
        StudioPrincipal principal = (StudioPrincipal) authentication.getPrincipal();
        assertThat(principal.username()).isEqualTo(USERNAME);
        assertThat(principal.role()).isEqualTo(Role.USER);
        assertThat(principal.sessionId()).isNotNull();
        assertThat(authentication.toString()).doesNotContain(token);
        verify(clusterService).getCluster("cluster-a");
    }

    @Test
    void validAdminReachesMutationController() throws Exception {
        when(clusterService.restartBroker("cluster-a", "broker-a")).thenReturn(true);
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/clusters/cluster-a/brokers/broker-a/restart")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true));

        verify(clusterService).restartBroker("cluster-a", "broker-a");
    }

    @Test
    void authorizationTableIsAnExactBijectionWithApplicationControllerMappings() {
        Set<Route> discovered = handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> isApplicationApiController(entry.getKey(), entry.getValue()))
            .flatMap(entry -> routes(entry.getKey()))
            .collect(Collectors.toUnmodifiableSet());

        assertThat(discovered).hasSize(69);
        assertThat(policy.routes()).doesNotHaveDuplicates();
        assertThat(Set.copyOf(policy.routes())).isEqualTo(discovered);
    }

    @Test
    void authorizationTableIsImmutablePreciseAndUsesMvcPathSemantics() {
        Set<Route> actualUserRoutes = policy.routes().stream()
            .filter(route -> route.access() == Access.USER)
            .collect(Collectors.toUnmodifiableSet());
        assertThat(actualUserRoutes).isEqualTo(REVIEWED_USER_ALLOWLIST);
        assertThat(actualUserRoutes)
            .allSatisfy(route -> assertThat(route.mvcPattern()).doesNotContain("/**"));
        assertThat(policy.routes()).contains(
            new Route(HttpMethod.POST, "/api/auth/login", Access.PUBLIC),
            new Route(HttpMethod.POST, "/api/auth/logout", Access.USER),
            new Route(HttpMethod.GET, "/api/clusters/{id}", Access.USER)
        );
        assertThat(policy.routes()).containsAll(SENSITIVE_ADMIN_GETS);
        assertThat(policy.routes())
            .noneSatisfy(route -> assertThat(route.mvcPattern()).contains("capabilities"));
        assertThat(access(HttpMethod.GET, "/api/clusters/cluster-a")).isEqualTo(Access.USER);
        assertThat(access(
            HttpMethod.GET,
            "/api/clusters/cluster-a/credentials"
        )).isEqualTo(Access.ADMIN);
        assertThat(access(HttpMethod.GET, "/api/capabilities")).isEqualTo(Access.ADMIN);
        assertThatThrownBy(() -> policy.routes().add(
            new Route(HttpMethod.GET, "/api/forbidden-test-mutation", Access.USER)
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void userIsDeniedEveryAdminMappingBeforeAnyControllerRuns() throws Exception {
        String token = login(USERNAME, PASSWORD);

        for (Route route : policy.routes()) {
            if (route.access() != Access.ADMIN) {
                continue;
            }
            MockHttpServletRequestBuilder request = request(
                route.method(),
                concretePath(route.mvcPattern())
            ).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            assertForbidden(request);
        }

        verifyNoInteractions(clusterService);
    }

    @Test
    void sensitiveAndCapabilityGetsFallBackToAdminAtRuntime() throws Exception {
        String userToken = login(USERNAME, PASSWORD);
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        for (Route route : SENSITIVE_ADMIN_GETS) {
            assertThat(route.access()).isEqualTo(Access.ADMIN);
            assertForbidden(get(route.mvcPattern())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken));
        }
        assertForbidden(get("/api/clusters/cluster-a/credentials")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken));
        assertForbidden(get("/api/capabilities")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken));
        verifyNoInteractions(
            settingsService,
            aclService,
            auditService,
            k8sCertService
        );

        mockMvc.perform(get("/api/clusters/cluster-a/credentials")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/capabilities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void validUserReachesMetricsController() throws Exception {
        String token = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/metrics/query")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "metric": "up",
                      "start": 1,
                      "end": 2,
                      "step": "1m"
                    }
                    """))
            .andExpect(status().isOk());

        verify(metricsService).query(any(MetricQueryDTO.class));
    }

    @Test
    void validUserReachesAiChatAsyncBoundary() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(aiService.chat(any(ChatDTO.class))).thenReturn(emitter);
        String token = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/ai/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\",\"mode\":\"chat\"}"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result
                .MockMvcResultMatchers.request().asyncStarted());

        verify(aiService).chat(any(ChatDTO.class));
        emitter.complete();
    }

    @Test
    void validUserAiChatCompletesAcrossAuthenticatedAsyncRedispatch() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(aiService.chat(any(ChatDTO.class))).thenReturn(emitter);
        String token = login(USERNAME, PASSWORD);
        MvcResult initial = mockMvc.perform(post("/api/ai/chat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\",\"mode\":\"chat\"}"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result
                .MockMvcResultMatchers.request().asyncStarted())
            .andReturn();

        emitter.send(SseEmitter.event().name("message").data("reply"));
        emitter.complete();

        mockMvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data:reply")));
    }

    @Test
    void revokedTokenFailsClosedBeforeAsyncRedispatchChain() throws Exception {
        String token = login(USERNAME, PASSWORD);
        Session session = sessions.resolve(token).orElseThrow();
        sessions.revoke(session.id());
        AtomicBoolean chainReached = new AtomicBoolean();
        MockHttpServletResponse response = new MockHttpServletResponse();

        bearerFilter().doFilter(
            dispatchRequest(DispatcherType.ASYNC, token),
            response,
            (request, servletResponse) -> chainReached.set(true)
        );

        assertThat(chainReached).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEqualTo(UNAUTHORIZED_JSON);
    }

    @Test
    void validTokenIsReauthenticatedForErrorDispatch() throws Exception {
        String token = login(USERNAME, PASSWORD);
        AtomicReference<Authentication> observed = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            bearerFilter().doFilter(
                dispatchRequest(DispatcherType.ERROR, token),
                response,
                (request, servletResponse) -> observed.set(
                    SecurityContextHolder.getContext().getAuthentication()
                )
            );
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getPrincipal()).isInstanceOf(StudioPrincipal.class);
    }

    @Test
    void expiredSessionIsRejectedWithoutSleeping() throws Exception {
        String token = login(USERNAME, PASSWORD);
        Session issued = sessions.resolve(token).orElseThrow();
        assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(5)));
        clock.advance(Duration.ofMinutes(5));

        assertUnauthorized(authorizedGet(token));
        verifyNoInteractions(clusterService);
    }

    @Test
    void revokedSessionIsRejected() throws Exception {
        String token = login(USERNAME, PASSWORD);
        Session session = sessions.resolve(token).orElseThrow();
        sessions.revoke(session.id());

        assertUnauthorized(authorizedGet(token));
        verifyNoInteractions(clusterService);
    }

    @Test
    void logoutRevokesOnlyTheAuthenticatedSessionAndTokenCannotBeReused() throws Exception {
        String token = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.message").value("success"))
            .andExpect(cookie().doesNotExist("JSESSIONID"));

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
    }

    @Test
    void missingRegistryUserRevokesSessionAndStopsController() throws Exception {
        String token = login(USERNAME, PASSWORD);
        writeUsers(userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN"));
        refreshRegistry();

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
        verifyNoInteractions(clusterService);
    }

    @Test
    void changedRoleRevokesSessionAndStopsController() throws Exception {
        String token = login(USERNAME, PASSWORD);
        writeUsers(
            userJson(USERNAME, USER_HASH, "ADMIN"),
            userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN")
        );
        refreshRegistry();

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
        verifyNoInteractions(clusterService);
    }

    @Test
    void changedPasswordRevokesSessionAndStopsController() throws Exception {
        String token = login(USERNAME, PASSWORD);
        writeUsers(
            userJson(USERNAME, CHANGED_HASH, "USER"),
            userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN")
        );
        refreshRegistry();

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
        verifyNoInteractions(clusterService);
    }

    @Test
    void changedRegistryRevisionRevokesOtherwiseUnchangedSession() throws Exception {
        String token = login(USERNAME, PASSWORD);
        writeUsers(
            userJson(USERNAME, USER_HASH, "USER"),
            userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN"),
            userJson("observer", CHANGED_HASH, "USER")
        );
        refreshRegistry();

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
        verifyNoInteractions(clusterService);
    }

    @Test
    void unavailableRegistryRevokesSessionAndStopsController() throws Exception {
        String token = login(USERNAME, PASSWORD);
        writeRaw("{\"schemaVersion\":\"v1\",\"users\":[");
        refreshRegistry();

        assertUnauthorized(authorizedGet(token));
        assertThat(sessions.resolve(token)).isEmpty();
        verifyNoInteractions(clusterService);
    }

    @ParameterizedTest
    @MethodSource("invalidAuthorizationValues")
    void malformedBearerValuesAreRejectedBeforeController(String authorization) throws Exception {
        assertUnauthorized(get("/api/clusters/cluster-a")
            .header(HttpHeaders.AUTHORIZATION, authorization));
        verifyNoInteractions(clusterService);
    }

    @Test
    void repeatedAuthorizationHeadersAreRejectedBeforeController() throws Exception {
        assertUnauthorized(get("/api/clusters/cluster-a").header(
            HttpHeaders.AUTHORIZATION,
            "Bearer " + VALID_TOKEN_SHAPE,
            "Bearer " + VALID_TOKEN_SHAPE
        ));
        verifyNoInteractions(clusterService);
    }

    @Test
    void validCorsPreflightIsPublicWithoutCredentialedCookieCors() throws Exception {
        mockMvc.perform(options("/api/clusters")
                .header(HttpHeaders.ORIGIN, "https://console.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                org.hamcrest.Matchers.containsString("GET")
            ))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void nonCorsOptionsRequestIsNotTreatedAsPublicPreflight() throws Exception {
        assertUnauthorized(options("/api/clusters"));
    }

    @Test
    void disallowedCorsPreflightMethodIsRejected() throws Exception {
        mockMvc.perform(options("/api/clusters")
                .header(HttpHeaders.ORIGIN, "https://console.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void crossOriginUnauthorizedResponseRetainsCorsHeaders() throws Exception {
        assertUnauthorized(get("/api/clusters/cluster-a")
            .header(HttpHeaders.ORIGIN, "https://console.example"));
        mockMvc.perform(get("/api/clusters/cluster-a")
                .header(HttpHeaders.ORIGIN, "https://console.example"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    @Test
    void crossOriginForbiddenResponseRetainsCorsHeaders() throws Exception {
        String token = login(USERNAME, PASSWORD);

        mockMvc.perform(post("/api/clusters/cluster-a/brokers/broker-a/restart")
                .header(HttpHeaders.ORIGIN, "https://console.example")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
            .andExpect(content().string(FORBIDDEN_JSON));
    }

    @Test
    void securityTooManyRequestsResponseIsExactAndSafe() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityResponses.tooManyRequests(response, 7);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("7");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(response.getContentAsString()).isEqualTo(
            "{\"code\":429,\"message\":\"Too Many Requests\",\"data\":null}"
        );
    }

    @Test
    void studioPrincipalContainsOnlySessionIdUsernameAndRole() {
        assertThat(Arrays.stream(StudioPrincipal.class.getRecordComponents())
            .map(component -> component.getName() + ":" + component.getType().getSimpleName()))
            .containsExactly(
                "sessionId:UUID",
                "username:String",
                "role:Role"
            );
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
            .andExpect(cookie().doesNotExist("JSESSIONID"))
            .andExpect(content().string(UNAUTHORIZED_JSON));
    }

    private void assertForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
            .andExpect(cookie().doesNotExist("JSESSIONID"))
            .andExpect(content().string(FORBIDDEN_JSON));
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\""
                    + password + "\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().doesNotExist("JSESSIONID"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.path("data").path("token").asText();
    }

    private static MockHttpServletRequestBuilder loginRequest() {
        return post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + USERNAME + "\",\"password\":\""
                + PASSWORD + "\"}");
    }

    private static MockHttpServletRequestBuilder authorizedGet(String token) {
        return get("/api/clusters/cluster-a")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private Access access(HttpMethod method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method.name());
        request.setRequestURI(path);
        return policy.access(request);
    }

    private StudioBearerAuthenticationFilter bearerFilter() {
        return new StudioBearerAuthenticationFilter(
            policy,
            sessions,
            registry,
            securityResponses
        );
    }

    private static MockHttpServletRequest dispatchRequest(
        DispatcherType dispatcherType,
        String token
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(dispatcherType);
        request.setMethod(HttpMethod.GET.name());
        request.setRequestURI("/api/clusters/cluster-a");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (dispatcherType == DispatcherType.ERROR) {
            request.setAttribute(
                WebUtils.ERROR_REQUEST_URI_ATTRIBUTE,
                request.getRequestURI()
            );
        }
        return request;
    }

    private static boolean isApplicationApiController(
        RequestMappingInfo info,
        HandlerMethod method
    ) {
        return method.getBeanType().getPackageName().startsWith("com.rocketmq.studio")
            && AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RestController.class)
            && info.getPathPatternsCondition() != null
            && info.getPathPatternsCondition().getPatterns().stream()
                .map(PathPattern::getPatternString)
                .anyMatch(pattern -> pattern.startsWith("/api/"));
    }

    private static Stream<Route> routes(RequestMappingInfo info) {
        Set<org.springframework.web.bind.annotation.RequestMethod> methods =
            info.getMethodsCondition().getMethods();
        return info.getPathPatternsCondition().getPatterns().stream()
            .flatMap(pattern -> methods.stream().map(method -> {
                HttpMethod httpMethod = HttpMethod.valueOf(method.name());
                String mvcPattern = pattern.getPatternString();
                return new Route(
                    httpMethod,
                    mvcPattern,
                    expectedAccess(httpMethod, mvcPattern)
                );
            }));
    }

    private static Access expectedAccess(
        HttpMethod method,
        String pattern
    ) {
        if (method == HttpMethod.POST && "/api/auth/login".equals(pattern)) {
            return Access.PUBLIC;
        }
        if (REVIEWED_USER_ALLOWLIST.contains(user(method, pattern))) {
            return Access.USER;
        }
        return Access.ADMIN;
    }

    private static Route user(HttpMethod method, String pattern) {
        return new Route(method, pattern, Access.USER);
    }

    private static Route admin(HttpMethod method, String pattern) {
        return new Route(method, pattern, Access.ADMIN);
    }

    private static String concretePath(String pattern) {
        return pattern.replaceAll("\\{[^/]+}", "sample");
    }

    private void refreshRegistry() {
        ticker.advance(Duration.ofSeconds(1));
    }

    private static Stream<String> ignoredPublicAuthorizationValues() {
        return Stream.of(
            "Bearer " + VALID_TOKEN_SHAPE,
            "Bearer malformed",
            "Basic Zm9vOmJhcg==",
            "Bearer " + VALID_TOKEN_SHAPE + ", Basic Zm9vOmJhcg=="
        );
    }

    private static Stream<String> invalidAuthorizationValues() {
        return Stream.of(
            "Basic Zm9vOmJhcg==",
            "Bearer",
            "Bearer ",
            "Bearer  " + VALID_TOKEN_SHAPE,
            "Bearer\t" + VALID_TOKEN_SHAPE,
            " Bearer " + VALID_TOKEN_SHAPE,
            "Bearer " + "A".repeat(42),
            "Bearer " + "A".repeat(44),
            "Bearer " + VALID_TOKEN_SHAPE + " ",
            "Bearer " + "A".repeat(42) + "=",
            "Bearer " + "A".repeat(42) + "+",
            "Bearer " + VALID_TOKEN_SHAPE + ",Basic marker",
            "Bearer " + VALID_TOKEN_SHAPE + "\r\nInjected: marker",
            "Bearer " + "A".repeat(4096),
            "Token " + VALID_TOKEN_SHAPE
        );
    }

    private static void writeUsers(String... users) throws IOException {
        writeRaw("{\"schemaVersion\":\"v1\",\"users\":["
            + String.join(",", users) + "]}");
    }

    private static void writeRaw(String content) throws IOException {
        Files.writeString(USER_FILE, content, StandardCharsets.UTF_8);
        securePermissions(USER_FILE, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        ));
    }

    private static String userJson(String username, String hash, String role) {
        return "{\"username\":\"" + username + "\",\"passwordHash\":\""
            + hash + "\",\"role\":\"" + role + "\"}";
    }

    private static void createTestDirectory() {
        try {
            Files.createDirectories(TEST_DIRECTORY);
            securePermissions(TEST_DIRECTORY, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            ));
            writeUsers(
                userJson(USERNAME, USER_HASH, "USER"),
                userJson(ADMIN_USERNAME, ADMIN_HASH, "ADMIN")
            );
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void securePermissions(
        Path path,
        Set<PosixFilePermission> permissions
    ) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTimeConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        }

        @Bean
        @Primary
        TestTicker testTicker() {
            return new TestTicker();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant initial) {
            now = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    static final class TestTicker implements LongSupplier {
        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long getAsLong() {
            return nanos.get();
        }
    }
}
