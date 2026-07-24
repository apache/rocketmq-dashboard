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

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rocketmq.studio.auth.security.LoginAttemptLimiter;
import com.rocketmq.studio.auth.security.StudioLoginException;
import com.rocketmq.studio.auth.security.StudioSecurityProperties;
import com.rocketmq.studio.auth.security.StudioSessionStore;
import com.rocketmq.studio.auth.security.StudioSessionStore.IssuedSession;
import com.rocketmq.studio.auth.security.StudioSessionStore.Session;
import com.rocketmq.studio.auth.security.StudioUserRegistry;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String USER_HASH =
        "{bcrypt}$2a$12$0Csye9dL3p9pzcYkoQyAwOzMpdWxpCvXYYy33vKXn6S9qXzAocB1C";
    private static final long REVISION = 7L;
    private static final String REMOTE_ADDRESS = "192.0.2.10";

    @Mock
    private StudioUserRegistry registry;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private StudioSessionStore sessions;
    @Mock
    private LoginAttemptLimiter limiter;
    @Mock
    private LoginAttemptLimiter.Permit permit;

    private AuthService authService;
    private LoginAttemptLimiter.Key attemptKey;

    @BeforeEach
    void setUp() {
        StudioSecurityProperties properties = new StudioSecurityProperties(
            null,
            Duration.ofMinutes(37),
            5,
            100,
            Duration.ofSeconds(1),
            4
        );
        LoginAttemptLimiter keyFactory = new LoginAttemptLimiter(
            properties,
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC)
        );
        attemptKey = keyFactory.beforeAttempt("Admin", REMOTE_ADDRESS).key();
        authService = new AuthService(registry, passwordEncoder, sessions, limiter, properties);
        lenient().when(limiter.beforeAttempt(any(), any()))
            .thenReturn(new LoginAttemptLimiter.Decision(true, 0, attemptKey));
        lenient().when(limiter.acquirePasswordPermit()).thenReturn(permit);
        lenient().when(permit.acquired()).thenReturn(true);
    }

    @Test
    void authenticatesAConfiguredAdminAndIssuesSession() {
        User admin = user("Admin", Role.ADMIN);
        Snapshot snapshot = available(admin);
        IssuedSession issued = issued(admin);
        when(registry.snapshot()).thenReturn(snapshot);
        when(passwordEncoder.matches("correct", admin.passwordHash())).thenReturn(true);
        when(sessions.issue(admin, snapshot.revision())).thenReturn(issued);

        LoginVO result = authService.login(dto("Admin", "correct"), REMOTE_ADDRESS);

        assertThat(result.getToken()).isEqualTo(issued.token());
        assertThat(result.getExpiresIn()).isEqualTo(2_220);
        assertThat(result.getUser().getUsername()).isEqualTo("Admin");
        assertThat(result.getUser().isAdmin()).isTrue();
        verify(passwordEncoder).matches("correct", admin.passwordHash());
        InOrder issueBeforeSuccess = inOrder(sessions, limiter);
        issueBeforeSuccess.verify(sessions).issue(admin, REVISION);
        issueBeforeSuccess.verify(limiter).recordSuccess(attemptKey);
        verify(limiter, never()).recordFailure(any());
        verify(permit).close();
    }

    @Test
    void wrongPasswordRunsOneRealUserMatchAndRecordsGenericFailure() {
        User user = user("alice", Role.USER);
        when(registry.snapshot()).thenReturn(available(user));
        when(passwordEncoder.matches("wrong", user.passwordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("alice", "wrong"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches("wrong", user.passwordHash());
        verify(limiter).recordFailure(attemptKey);
        verify(limiter, never()).recordSuccess(any());
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @Test
    void unknownUserRunsOneEmbeddedNonUserBcryptMatchAndRecordsGenericFailure() {
        when(registry.snapshot()).thenReturn(new Snapshot(REVISION, true, Map.of()));
        when(passwordEncoder.matches(eq("guess"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("unknown", "guess"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("guess"), argThat(AuthServiceTest::isDummyCostTwelveHash));
        verify(limiter).recordFailure(attemptKey);
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @Test
    void unavailableRegistryRunsOneEmbeddedNonUserBcryptMatchAndRecordsGenericFailure() {
        when(registry.snapshot()).thenReturn(new Snapshot(REVISION, false, Map.of()));
        when(passwordEncoder.matches(eq("guess"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("guess"), argThat(AuthServiceTest::isDummyCostTwelveHash));
        verify(limiter).recordFailure(attemptKey);
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @Test
    void registryReadFailureRunsOneDummyMatchAndRecordsGenericFailure() {
        when(registry.snapshot()).thenThrow(new IllegalStateException("user file detail"));
        when(passwordEncoder.matches(eq("guess"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("guess"), argThat(AuthServiceTest::isDummyCostTwelveHash));
        verify(limiter).recordFailure(attemptKey);
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @Test
    void nullRegistrySnapshotRunsOneDummyMatchAndRecordsGenericFailure() {
        when(registry.snapshot()).thenReturn(null);
        when(passwordEncoder.matches(eq("guess"), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches(eq("guess"), argThat(AuthServiceTest::isDummyCostTwelveHash));
        verify(limiter).recordFailure(attemptKey);
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @Test
    void configuredUserWithTheDummyHashCanNeverAuthenticate() throws Exception {
        User collision = new User(
            "collision-user",
            dummyPasswordHash(),
            Role.ADMIN,
            "collision-fingerprint"
        );
        when(registry.snapshot()).thenReturn(available(collision));
        when(passwordEncoder.matches("guess", collision.passwordHash())).thenReturn(true);

        assertThatThrownBy(() ->
            authService.login(dto(collision.username(), "guess"), REMOTE_ADDRESS))
            .isInstanceOf(StudioLoginException.class)
            .hasMessage("Invalid username or password");

        verify(passwordEncoder).matches("guess", collision.passwordHash());
        verify(limiter).recordFailure(attemptKey);
        verify(limiter, never()).recordSuccess(any());
        verify(sessions, never()).issue(any(), anyLong());
        verify(permit).close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("structurallyInvalidCredentials")
    void structuralRejectionsSkipLimiterRegistryBcryptAndSessions(
        String description,
        LoginDTO request
    ) {
        assertThatThrownBy(() -> authService.login(request, REMOTE_ADDRESS))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Invalid login request");

        verifyNoInteractions(registry, passwordEncoder, sessions);
        verifyNoInteractions(limiter);
        verifyNoInteractions(permit);
    }

    @Test
    void limiterDenialSkipsRegistryPermitAndBcryptAndKeepsRetryAfter() {
        when(limiter.beforeAttempt("alice", REMOTE_ADDRESS))
            .thenReturn(new LoginAttemptLimiter.Decision(false, 42, attemptKey));

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOfSatisfying(StudioLoginException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(429);
                assertThat(exception.retryAfterSeconds()).isEqualTo(42);
            });

        verifyNoInteractions(registry, passwordEncoder, sessions);
        verify(limiter, never()).acquirePasswordPermit();
        verifyNoInteractions(permit);
    }

    @Test
    void passwordPermitSaturationSkipsRegistryAndBcryptAndClosesPermit() {
        when(permit.acquired()).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOfSatisfying(StudioLoginException.class, exception -> {
                assertThat(exception.status().value()).isEqualTo(429);
                assertThat(exception.retryAfterSeconds()).isEqualTo(1);
            });

        verifyNoInteractions(registry, passwordEncoder, sessions);
        verify(permit).close();
    }

    @Test
    void acquiredPasswordPermitClosesWhenEncoderThrows() {
        User user = user("alice", Role.USER);
        when(registry.snapshot()).thenReturn(available(user));
        when(passwordEncoder.matches("guess", user.passwordHash()))
            .thenThrow(new IllegalArgumentException("encoder detail"));

        assertThatThrownBy(() -> authService.login(dto("alice", "guess"), REMOTE_ADDRESS))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("encoder detail");

        verify(permit).close();
        verify(sessions, never()).issue(any(), anyLong());
    }

    @Test
    void logsNeverContainPasswordHashTokenOrRawInvalidUsername() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String invalidUsername = "invalid\nraw-username-marker";
        String password = "password-marker";
        try {
            assertThatThrownBy(() ->
                authService.login(dto(invalidUsername, password), REMOTE_ADDRESS))
                .isInstanceOf(BusinessException.class);

            reset(registry, passwordEncoder, sessions, limiter, permit);
            User admin = user("Admin", Role.ADMIN);
            when(limiter.beforeAttempt(any(), any()))
                .thenReturn(new LoginAttemptLimiter.Decision(true, 0, attemptKey));
            when(limiter.acquirePasswordPermit()).thenReturn(permit);
            when(permit.acquired()).thenReturn(true);
            when(registry.snapshot()).thenReturn(available(admin));
            when(passwordEncoder.matches(password, admin.passwordHash())).thenReturn(true);
            when(sessions.issue(admin, REVISION)).thenReturn(issued(admin));

            assertThatCode(() ->
                authService.login(dto("Admin", password), REMOTE_ADDRESS)).doesNotThrowAnyException();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String messages = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (left, right) -> left + "\n" + right);
        assertThat(messages)
            .doesNotContain(invalidUsername)
            .doesNotContain("raw-username-marker")
            .doesNotContain(password)
            .doesNotContain(USER_HASH)
            .doesNotContain("opaque-session-token");
    }

    @Test
    void authenticationValueObjectsRedactSecretsFromToString() throws Exception {
        LoginDTO request = dto("raw-username-marker", "raw-password-marker");
        LoginVO response = LoginVO.builder()
            .token("raw-token-marker")
            .expiresIn(300)
            .user(LoginVO.UserInfo.builder()
                .username("canonical-user")
                .admin(false)
                .build())
            .build();

        Class<?> credentialsType = Stream.of(AuthService.class.getDeclaredClasses())
            .filter(type -> type.getSimpleName().equals("Credentials"))
            .findFirst()
            .orElseThrow();
        var constructor = credentialsType.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object credentials = constructor.newInstance(
            "credentials-username-marker",
            "credentials-password-marker"
        );

        assertThat(request.toString())
            .isEqualTo("LoginDTO(username=<redacted>, password=<redacted>)");
        assertThat(response.toString())
            .contains("token=<redacted>")
            .doesNotContain("raw-token-marker");
        assertThat(credentials.toString())
            .isEqualTo("Credentials[username=<redacted>, password=<redacted>]");
    }

    @Test
    void ordinarySecurityBeansStartWithoutAUserFileAndUsePrefixAwareCostTwelveBcrypt() {
        new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withPropertyValues(
                "studio.security.user-file=",
                "studio.security.session-ttl=37m",
                "studio.security.max-sessions-per-user=5",
                "studio.security.max-users=100",
                "studio.security.registry-check-interval=1s",
                "studio.security.max-concurrent-password-checks=4"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(Clock.class);
                assertThat(context).hasSingleBean(LongSupplier.class);
                assertThat(context).hasSingleBean(StudioUserRegistry.class);
                assertThat(context).hasSingleBean(StudioSessionStore.class);
                assertThat(context).hasSingleBean(LoginAttemptLimiter.class);
                assertThat(context).hasSingleBean(PasswordEncoder.class);
                assertThat(context.getBean(StudioUserRegistry.class).snapshot().available())
                    .isFalse();

                PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                String dummyHash;
                try {
                    dummyHash = dummyPasswordHash();
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
                assertThat(dummyHash)
                    .matches("\\{bcrypt}\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}");
                assertThat(encoder.matches("known-non-matching-probe", dummyHash)).isFalse();
            });
    }

    @Test
    void conditionalSecurityBeansPreserveClockTickerAndEncoderOverrides() {
        Clock customClock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        LongSupplier customTicker = () -> 123L;
        PasswordEncoder customEncoder = passwordEncoder;

        new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withBean(Clock.class, () -> customClock)
            .withBean(LongSupplier.class, () -> customTicker)
            .withBean(PasswordEncoder.class, () -> customEncoder)
            .withPropertyValues(
                "studio.security.user-file=",
                "studio.security.session-ttl=37m",
                "studio.security.max-sessions-per-user=5",
                "studio.security.max-users=100",
                "studio.security.registry-check-interval=1s",
                "studio.security.max-concurrent-password-checks=4"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(Clock.class)).isSameAs(customClock);
                assertThat(context.getBean(LongSupplier.class)).isSameAs(customTicker);
                assertThat(context.getBean(PasswordEncoder.class)).isSameAs(customEncoder);
            });
    }

    private static Stream<Arguments> structurallyInvalidCredentials() {
        return Stream.of(
            Arguments.of("blank username", dto("   ", "guess")),
            Arguments.of("non-ASCII username", dto("álîce", "guess")),
            Arguments.of("username over 128 characters", dto("a".repeat(129), "guess")),
            Arguments.of("blank password", dto("alice", "   ")),
            Arguments.of("blank password over 72 characters", dto("alice", " ".repeat(73))),
            Arguments.of("password over 72 characters", dto("alice", "a".repeat(73))),
            Arguments.of("password within 72 chars but over 72 UTF-8 bytes",
                dto("alice", new String(Character.toChars(0x5bc6)).repeat(25)))
        );
    }

    private static boolean isDummyCostTwelveHash(String hash) {
        return hash != null
            && !USER_HASH.equals(hash)
            && hash.matches("\\{bcrypt}\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}");
    }

    private static String dummyPasswordHash() throws ReflectiveOperationException {
        Field field = AuthService.class.getDeclaredField("DUMMY_PASSWORD_HASH");
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static Snapshot available(User user) {
        return new Snapshot(REVISION, true, Map.of(user.username(), user));
    }

    private static User user(String username, Role role) {
        return new User(username, USER_HASH, role, "fingerprint");
    }

    private static IssuedSession issued(User user) {
        Instant issuedAt = Instant.parse("2026-07-24T00:00:00Z");
        Session session = new Session(
            UUID.fromString("00000000-0000-0000-0000-000000000007"),
            user.username(),
            user.role(),
            user.fingerprint(),
            REVISION,
            issuedAt,
            issuedAt.plus(Duration.ofMinutes(37)),
            1
        );
        return new IssuedSession("opaque-session-token", session);
    }

    private static LoginDTO dto(String username, String password) {
        LoginDTO request = new LoginDTO();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
