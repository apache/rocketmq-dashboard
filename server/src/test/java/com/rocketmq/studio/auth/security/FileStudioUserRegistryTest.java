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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStudioUserRegistryTest {
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final String ADMIN_HASH = "{bcrypt}$2a$12$" + "A".repeat(53);
    private static final String USER_HASH = "{bcrypt}$2b$12$" + "B".repeat(53);

    @TempDir
    Path tempDir;

    @Test
    void loadsAdminAndUserIntoImmutableSnapshot() throws IOException {
        Path userFile = write("users.json", validUsersJson(
            userJson("admin", ADMIN_HASH, "ADMIN"),
            userJson("operator", USER_HASH, "USER")
        ));

        Snapshot snapshot = registry(userFile).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.revision()).isPositive();
        assertThat(snapshot.users()).containsOnlyKeys("admin", "operator");
        assertThat(snapshot.users().get("admin"))
            .extracting(User::username, User::passwordHash, User::role)
            .containsExactly("admin", ADMIN_HASH, Role.ADMIN);
        assertThat(snapshot.users().get("operator").role()).isEqualTo(Role.USER);
        assertThat(snapshot.users().values())
            .extracting(User::fingerprint)
            .allSatisfy(fingerprint -> assertThat(fingerprint).matches("[0-9a-f]{64}"));
        assertThat(snapshot.users().get("admin").fingerprint())
            .isNotEqualTo(snapshot.users().get("operator").fingerprint());
        assertThatThrownBy(() -> snapshot.users().put(
            "new-user",
            new User("new-user", ADMIN_HASH, Role.USER, "fingerprint")
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsEmptyUsersWithoutCreatingDefaultAccount() throws IOException {
        Path userFile = write("users.json", validUsersJson());

        Snapshot snapshot = registry(userFile).snapshot();

        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.users()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSchemaVersions")
    void rejectsInvalidSchemaVersion(String description, String rootJson) throws IOException {
        assertUnavailable(write("users.json", rootJson));
    }

    static Stream<Arguments> invalidSchemaVersions() {
        return Stream.of(
            Arguments.of("missing", "{\"users\":[]}"),
            Arguments.of("null", "{\"schemaVersion\":null,\"users\":[]}"),
            Arguments.of("empty", "{\"schemaVersion\":\"\",\"users\":[]}"),
            Arguments.of("future", "{\"schemaVersion\":\"v2\",\"users\":[]}"),
            Arguments.of("non-textual", "{\"schemaVersion\":1,\"users\":[]}")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("duplicateMembers")
    void rejectsDuplicateJsonMembers(String description, String json) throws IOException {
        assertUnavailable(write("users.json", json));
    }

    static Stream<Arguments> duplicateMembers() {
        return Stream.of(
            Arguments.of(
                "root schemaVersion",
                "{\"schemaVersion\":\"v1\",\"schemaVersion\":\"v1\",\"users\":[]}"
            ),
            Arguments.of(
                "root users",
                "{\"schemaVersion\":\"v1\",\"users\":[],\"users\":[]}"
            ),
            Arguments.of(
                "nested username",
                validUsersJson("{\"username\":\"alice\",\"username\":\"alice\","
                    + "\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}")
            ),
            Arguments.of(
                "nested passwordHash",
                validUsersJson("{\"username\":\"alice\",\"passwordHash\":\"" + ADMIN_HASH + "\","
                    + "\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}")
            ),
            Arguments.of(
                "nested role",
                validUsersJson("{\"username\":\"alice\",\"passwordHash\":\"" + ADMIN_HASH + "\","
                    + "\"role\":\"USER\",\"role\":\"USER\"}")
            )
        );
    }

    @Test
    void acceptsExactlyOneMebibyteWhenJsonIsOtherwiseValid() throws IOException {
        byte[] prefix = validUsersJson().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[MAX_FILE_BYTES];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        java.util.Arrays.fill(bytes, prefix.length, bytes.length, (byte) ' ');
        Path userFile = tempDir.resolve("exact-limit.json");
        Files.write(userFile, bytes);
        securePermissions(userFile);

        assertThat(registry(userFile).snapshot().available()).isTrue();
    }

    @Test
    void rejectsOneByteOverLimitBeforeJsonBinding() throws IOException {
        byte[] prefix = validUsersJson().getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[MAX_FILE_BYTES + 1];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        java.util.Arrays.fill(bytes, prefix.length, bytes.length, (byte) ' ');
        Path userFile = tempDir.resolve("over-limit.json");
        Files.write(userFile, bytes);
        securePermissions(userFile);

        assertUnavailable(userFile);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonUtf8RegistryEncodings")
    void rejectsNonUtf8RegistryEncoding(String description, byte[] bytes) throws IOException {
        Path userFile = tempDir.resolve("non-utf8-users.json");
        Files.write(userFile, bytes);
        securePermissions(userFile);

        assertUnavailable(userFile);
    }

    static Stream<Arguments> nonUtf8RegistryEncodings() {
        String json = validUsersJson(userJson("alice", ADMIN_HASH, "ADMIN"));
        return Stream.of(
            Arguments.of("UTF-16 with BOM", json.getBytes(StandardCharsets.UTF_16)),
            Arguments.of("UTF-32 with BOM", utf32WithBom(json))
        );
    }

    @Test
    void rejectsMalformedUtf8RegistryContent() throws IOException {
        byte[] prefix = "{\"schemaVersion\":\"v1\",\"users\":[{\"username\":\""
            .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\",\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}]}")
            .getBytes(StandardCharsets.UTF_8);
        byte[] malformed = new byte[prefix.length + 2 + suffix.length];
        System.arraycopy(prefix, 0, malformed, 0, prefix.length);
        malformed[prefix.length] = (byte) 0xc3;
        malformed[prefix.length + 1] = 0x28;
        System.arraycopy(suffix, 0, malformed, prefix.length + 2, suffix.length);
        Path userFile = tempDir.resolve("malformed-utf8-users.json");
        Files.write(userFile, malformed);
        securePermissions(userFile);

        assertUnavailable(userFile);
    }

    @Test
    void unavailableSnapshotNeverRetainsUsers() {
        User user = new User("alice", ADMIN_HASH, Role.USER, "fixed-fingerprint");

        Snapshot snapshot = new Snapshot(7, false, Map.of("alice", user));

        assertThat(snapshot.users()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRootShapes")
    void rejectsMissingNullAndUnknownRootFields(String description, String json) throws IOException {
        assertUnavailable(write("users.json", json));
    }

    static Stream<Arguments> invalidRootShapes() {
        return Stream.of(
            Arguments.of("missing users", "{\"schemaVersion\":\"v1\"}"),
            Arguments.of("null users", "{\"schemaVersion\":\"v1\",\"users\":null}"),
            Arguments.of("non-array users", "{\"schemaVersion\":\"v1\",\"users\":{}}"),
            Arguments.of(
                "unknown root field",
                "{\"schemaVersion\":\"v1\",\"users\":[],\"unexpected\":\"root-marker\"}"
            ),
            Arguments.of(
                "trailing root value",
                "{\"schemaVersion\":\"v1\",\"users\":[]} {\"schemaVersion\":\"v1\",\"users\":[]}"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNestedShapes")
    void rejectsMissingNullUnknownAndNonTextualUserFields(String description, String user)
        throws IOException {
        assertUnavailable(write("users.json", validUsersJson(user)));
    }

    static Stream<Arguments> invalidNestedShapes() {
        return Stream.of(
            Arguments.of(
                "missing username",
                "{\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}"
            ),
            Arguments.of(
                "null username",
                "{\"username\":null,\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}"
            ),
            Arguments.of(
                "missing hash",
                "{\"username\":\"alice\",\"role\":\"USER\"}"
            ),
            Arguments.of(
                "null hash",
                "{\"username\":\"alice\",\"passwordHash\":null,\"role\":\"USER\"}"
            ),
            Arguments.of(
                "missing role",
                "{\"username\":\"alice\",\"passwordHash\":\"" + ADMIN_HASH + "\"}"
            ),
            Arguments.of(
                "null role",
                "{\"username\":\"alice\",\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":null}"
            ),
            Arguments.of(
                "unknown user field",
                "{\"username\":\"alice\",\"passwordHash\":\"" + ADMIN_HASH
                    + "\",\"role\":\"USER\",\"secret-marker\":\"nested-marker\"}"
            ),
            Arguments.of(
                "numeric username",
                "{\"username\":42,\"passwordHash\":\"" + ADMIN_HASH + "\",\"role\":\"USER\"}"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUsernames")
    void rejectsInvalidUsername(String description, String username) throws IOException {
        assertUnavailable(write(
            "users.json",
            validUsersJson(userJson(username, ADMIN_HASH, "USER"))
        ));
    }

    static Stream<Arguments> invalidUsernames() {
        return Stream.of(
            Arguments.of("empty", ""),
            Arguments.of("space", "alice smith"),
            Arguments.of("slash", "alice/root"),
            Arguments.of("non-ascii", "alic\u00e9"),
            Arguments.of("129 characters", "a".repeat(129))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPasswordHashes")
    void rejectsInvalidPasswordHash(String description, String passwordHash) throws IOException {
        assertUnavailable(write(
            "users.json",
            validUsersJson(userJson("alice", passwordHash, "USER"))
        ));
    }

    static Stream<Arguments> invalidPasswordHashes() {
        return Stream.of(
            Arguments.of("plaintext", "correct-horse-battery-staple"),
            Arguments.of("malformed bcrypt", "{bcrypt}$2a$12$" + "A".repeat(52)),
            Arguments.of("wrong encoder", "{argon2}$argon-marker"),
            Arguments.of("wrong cost", "{bcrypt}$2a$10$" + "A".repeat(53)),
            Arguments.of("missing encoder id", "$2a$12$" + "A".repeat(53)),
            Arguments.of("invalid alphabet", "{bcrypt}$2a$12$" + "A".repeat(52) + ":")
        );
    }

    @ParameterizedTest(name = "invalid role {0}")
    @MethodSource("invalidRoles")
    void rejectsNonExactRoles(String role) throws IOException {
        assertUnavailable(write(
            "users.json",
            validUsersJson(userJson("alice", ADMIN_HASH, role))
        ));
    }

    static Stream<String> invalidRoles() {
        return Stream.of("user", "admin", "OWNER", "");
    }

    @Test
    void rejectsDuplicateUsernames() throws IOException {
        Path userFile = write("users.json", validUsersJson(
            userJson("Alice", ADMIN_HASH, "USER"),
            userJson("Alice", USER_HASH, "ADMIN")
        ));

        assertUnavailable(userFile);
    }

    @Test
    void preservesUsernameCaseSensitivity() throws IOException {
        Path userFile = write("users.json", validUsersJson(
            userJson("Alice", ADMIN_HASH, "USER"),
            userJson("alice", USER_HASH, "ADMIN")
        ));

        assertThat(registry(userFile).snapshot().users()).containsOnlyKeys("Alice", "alice");
    }

    @Test
    void rejectsUserCountAboveConfiguredMaximum() throws IOException {
        Path userFile = write("users.json", validUsersJson(
            userJson("one", ADMIN_HASH, "USER"),
            userJson("two", USER_HASH, "ADMIN")
        ));

        Snapshot snapshot = registry(properties(userFile.toString(), 1), new AtomicLong()).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.users()).isEmpty();
    }

    @Test
    void parserRejectsStringLongerThanConfiguredConstraint() throws IOException {
        LoggerCapture capture = attachLogger();
        try {
            Path userFile = write(
                "users.json",
                "{\"schemaVersion\":\"" + "x".repeat(257) + "\",\"users\":[]}"
            );

            assertUnavailable(userFile);

            assertThat(capture.messages()).singleElement()
                .asString()
                .contains("INVALID_JSON");
        } finally {
            capture.close();
        }
    }

    @Test
    void missingFilePublishesUnavailableInitialRevision() {
        Path missing = tempDir.resolve("missing-users.json");

        Snapshot snapshot = registry(missing).snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.revision()).isEqualTo(1);
        assertThat(snapshot.users()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("emptyFileContents")
    void rejectsEmptyFileContent(String description, byte[] content) throws IOException {
        Path userFile = tempDir.resolve("empty-users.json");
        Files.write(userFile, content);
        securePermissions(userFile);

        assertUnavailable(userFile);
    }

    static Stream<Arguments> emptyFileContents() {
        return Stream.of(
            Arguments.of("zero bytes", new byte[0]),
            Arguments.of("whitespace bytes", " \n\t".getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void rejectsUnreadablePosixFile() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        assumePosix(userFile);
        Files.setPosixFilePermissions(userFile, Set.of(PosixFilePermission.OWNER_WRITE));

        assertUnavailable(userFile);
    }

    @Test
    void sanitizesReaderAccessFailure() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        String exceptionMarker = "READER-EXCEPTION-SECRET-47";
        LoggerCapture capture = attachLogger();
        try {
            FileStudioUserRegistry registry = new FileStudioUserRegistry(
                properties(userFile.toString()),
                () -> 0L,
                currentOwner(userFile),
                (path, maximumBytes) -> {
                    throw new AccessDeniedException(exceptionMarker);
                }
            );

            assertThatCode(registry::snapshot).doesNotThrowAnyException();

            assertThat(capture.messages()).noneMatch(message -> message.contains(exceptionMarker));
        } finally {
            capture.close();
        }
    }

    @Test
    void rejectsSymbolicLink() throws IOException {
        Path target = write("real-users.json", validUsersJson());
        Path link = tempDir.resolve("linked-users.json");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("Symbolic links are unavailable");
        }

        assertUnavailable(link);
    }

    @Test
    void rejectsWrongPosixOwnerUsingInjectedExpectation() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        assumePosix(userFile);
        FileStudioUserRegistry registry = new FileStudioUserRegistry(
            properties(userFile.toString()),
            () -> 0L,
            "definitely-not-" + currentOwner(userFile),
            FileStudioUserRegistry::readBounded
        );

        assertThat(registry.snapshot().available()).isFalse();
    }

    @Test
    void rejectsGroupOrOtherPosixPermissions() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        assumePosix(userFile);
        Files.setPosixFilePermissions(
            userFile,
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ)
        );

        assertUnavailable(userFile);
    }

    @Test
    void publishesUnavailableImmediatelyWhenPosixPermissionsBecomeUnsafe() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        assumePosix(userFile);
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot available = registry.snapshot();

        Files.setPosixFilePermissions(
            userFile,
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ)
        );
        Snapshot unavailable = registry.snapshot();

        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.users()).isEmpty();
        assertThat(unavailable.revision()).isGreaterThan(available.revision());
    }

    @Test
    void advancesRevisionWhenInsecurePosixPermissionStateChanges() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        assumePosix(userFile);
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        registry.snapshot();
        Files.setPosixFilePermissions(
            userFile,
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ)
        );
        Snapshot groupReadable = registry.snapshot();

        Files.setPosixFilePermissions(
            userFile,
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_READ)
        );
        Snapshot othersReadable = registry.snapshot();

        assertThat(groupReadable.available()).isFalse();
        assertThat(othersReadable.available()).isFalse();
        assertThat(othersReadable.revision()).isGreaterThan(groupReadable.revision());
    }

    @Test
    void invalidOperatingSystemPathDoesNotPreventConstructionOrSnapshot() {
        String invalidPath = "users\u0000-secret-path-marker.json";

        FileStudioUserRegistry registry = new FileStudioUserRegistry(properties(invalidPath));
        Snapshot snapshot = registry.snapshot();

        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.revision()).isEqualTo(1);
        assertThat(snapshot.users()).isEmpty();
    }

    @Test
    void reloadsImmediatelyWhenFastFileSignalChanges() throws IOException {
        Path userFile = write(
            "users.json",
            validUsersJson(userJson("alice", ADMIN_HASH, "USER"))
        );
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot first = registry.snapshot();

        write(
            "users.json",
            validUsersJson(
                userJson("alice", ADMIN_HASH, "USER"),
                userJson("bob", USER_HASH, "ADMIN")
            )
        );
        Snapshot reloaded = registry.snapshot();

        assertThat(reloaded.revision()).isGreaterThan(first.revision());
        assertThat(reloaded.users()).containsOnlyKeys("alice", "bob");
    }

    @Test
    void roleHashAndRemovalTransitionsUpdateExpectedFingerprints() throws IOException {
        Path userFile = write("users.json", validUsersJson(
            userJson("alice", ADMIN_HASH, "USER"),
            userJson("bob", USER_HASH, "ADMIN")
        ));
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot initial = registry.snapshot();
        String initialAliceFingerprint = initial.users().get("alice").fingerprint();
        String initialBobFingerprint = initial.users().get("bob").fingerprint();

        write("users.json", validUsersJson(
            userJson("alice", ADMIN_HASH, "ADMIN"),
            userJson("bob", USER_HASH, "ADMIN")
        ));
        Snapshot roleChanged = registry.snapshot();
        assertThat(roleChanged.revision()).isGreaterThan(initial.revision());
        assertThat(roleChanged.users().get("alice").fingerprint())
            .isNotEqualTo(initialAliceFingerprint);
        assertThat(roleChanged.users().get("bob").fingerprint()).isEqualTo(initialBobFingerprint);

        write("users.json", validUsersJson(
            userJson("alice", USER_HASH, "ADMIN"),
            userJson("bob", USER_HASH, "ADMIN")
        ));
        Snapshot hashChanged = registry.snapshot();
        String hashChangedAliceFingerprint = hashChanged.users().get("alice").fingerprint();
        assertThat(hashChanged.revision()).isGreaterThan(roleChanged.revision());
        assertThat(hashChangedAliceFingerprint)
            .isNotEqualTo(roleChanged.users().get("alice").fingerprint());

        write(
            "users.json",
            validUsersJson(userJson("alice", USER_HASH, "ADMIN"))
        );
        Snapshot removed = registry.snapshot();
        assertThat(removed.revision()).isGreaterThan(hashChanged.revision());
        assertThat(removed.users()).containsOnlyKeys("alice");
        assertThat(removed.users().get("alice").fingerprint())
            .isEqualTo(hashChangedAliceFingerprint);
    }

    @Test
    void deletionAndInvalidReplacementDropOldUsersAndRecoveryIncrementsRevision()
        throws IOException {
        String validContent = validUsersJson(userJson("alice", ADMIN_HASH, "ADMIN"));
        Path userFile = write("users.json", validContent);
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot initial = registry.snapshot();

        Files.delete(userFile);
        Snapshot deleted = registry.snapshot();
        assertThat(deleted.available()).isFalse();
        assertThat(deleted.users()).isEmpty();
        assertThat(deleted.revision()).isGreaterThan(initial.revision());

        write("users.json", "{\"schemaVersion\":\"v1\",\"users\":[");
        Snapshot invalid = registry.snapshot();
        assertThat(invalid.available()).isFalse();
        assertThat(invalid.users()).isEmpty();
        assertThat(invalid.revision()).isGreaterThan(deleted.revision());

        write("users.json", validContent);
        Snapshot recovered = registry.snapshot();
        assertThat(recovered.available()).isTrue();
        assertThat(recovered.users()).containsOnlyKeys("alice");
        assertThat(recovered.revision()).isGreaterThan(invalid.revision());
    }

    @Test
    void periodicDigestFindsSameSizeSameMtimeContentChange() throws IOException {
        String aliceJson = validUsersJson(userJson("alice", ADMIN_HASH, "USER"));
        String carolJson = validUsersJson(userJson("carol", ADMIN_HASH, "USER"));
        assertThat(carolJson).hasSameSizeAs(aliceJson);
        Path userFile = write("users.json", aliceJson);
        FileTime originalModifiedTime = Files.getLastModifiedTime(userFile);
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot initial = registry.snapshot();

        write("users.json", carolJson);
        Files.setLastModifiedTime(userFile, originalModifiedTime);
        assertThat(registry.snapshot()).isSameAs(initial);

        clock.addAndGet(Duration.ofMillis(250).toNanos());
        Snapshot reloaded = registry.snapshot();

        assertThat(reloaded.revision()).isGreaterThan(initial.revision());
        assertThat(reloaded.users()).containsOnlyKeys("carol");
    }

    @Test
    void differentInvalidBytesWithSameSignalAdvanceRevision() throws IOException {
        String invalidA = "{\"schemaVersion\":\"v1\",\"users\":[A]}";
        String invalidB = "{\"schemaVersion\":\"v1\",\"users\":[B]}";
        Path userFile = write("users.json", invalidA);
        FileTime originalModifiedTime = Files.getLastModifiedTime(userFile);
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = registry(properties(userFile.toString()), clock);
        Snapshot first = registry.snapshot();

        write("users.json", invalidB);
        Files.setLastModifiedTime(userFile, originalModifiedTime);
        assertThat(registry.snapshot()).isSameAs(first);

        clock.addAndGet(Duration.ofMillis(250).toNanos());
        Snapshot second = registry.snapshot();

        assertThat(second.available()).isFalse();
        assertThat(second.revision()).isGreaterThan(first.revision());
    }

    @Test
    void periodicCheckHandlesNanoTimeOverflow() throws IOException {
        Path userFile = write("users.json", validUsersJson());
        long initialNanos = Long.MAX_VALUE - Duration.ofMillis(100).toNanos();
        AtomicLong clock = new AtomicLong(initialNanos);
        AtomicInteger reads = new AtomicInteger();
        FileStudioUserRegistry registry = new FileStudioUserRegistry(
            properties(userFile.toString()),
            clock::get,
            currentOwner(userFile),
            (path, maximumBytes) -> {
                reads.incrementAndGet();
                return FileStudioUserRegistry.readBounded(path, maximumBytes);
            }
        );
        registry.snapshot();

        clock.set(Long.MIN_VALUE + Duration.ofMillis(200).toNanos());
        registry.snapshot();

        assertThat(reads).hasValue(2);
    }

    @Test
    void concurrentDueCallersPerformOnePhysicalBoundedRead() throws Exception {
        Path userFile = write("users.json", validUsersJson());
        AtomicLong clock = new AtomicLong();
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch dueReadEntered = new CountDownLatch(1);
        CountDownLatch releaseDueRead = new CountDownLatch(1);
        FileStudioUserRegistry registry = new FileStudioUserRegistry(
            properties(userFile.toString()),
            clock::get,
            currentOwner(userFile),
            (path, maximumBytes) -> {
                int readNumber = reads.incrementAndGet();
                if (readNumber == 2) {
                    dueReadEntered.countDown();
                    await(releaseDueRead);
                }
                return FileStudioUserRegistry.readBounded(path, maximumBytes);
            }
        );
        Snapshot initial = registry.snapshot();
        clock.addAndGet(Duration.ofMillis(250).toNanos());

        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            CountDownLatch callersReady = new CountDownLatch(12);
            CountDownLatch begin = new CountDownLatch(1);
            List<Future<Snapshot>> futures = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                futures.add(executor.submit(() -> {
                    callersReady.countDown();
                    await(begin);
                    return registry.snapshot();
                }));
            }
            assertThat(callersReady.await(5, TimeUnit.SECONDS)).isTrue();
            begin.countDown();
            assertThat(dueReadEntered.await(5, TimeUnit.SECONDS)).isTrue();
            releaseDueRead.countDown();

            for (Future<Snapshot> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(initial);
            }
        } finally {
            releaseDueRead.countDown();
            executor.shutdownNow();
        }

        assertThat(reads).hasValue(2);
    }

    @Test
    void logsOneSanitizedWarningPerRealUnavailableTransition() throws IOException {
        String pathMarker = "PATH-SECRET-MARKER-19";
        String contentMarker = "CONTENT-SECRET-MARKER-23";
        String usernameMarker = "USERNAME-SECRET-MARKER-29";
        String hashMarker = "HASH-SECRET-MARKER-31";
        Path userFile = tempDir.resolve(pathMarker + ".json");
        AtomicLong clock = new AtomicLong();
        FileStudioUserRegistry registry = new FileStudioUserRegistry(
            properties(userFile.toString()),
            clock::get,
            currentOwner(tempDir),
            FileStudioUserRegistry::readBounded
        );
        LoggerCapture capture = attachLogger();
        try {
            Snapshot missing = registry.snapshot();
            assertThat(registry.snapshot()).isSameAs(missing);
            assertThat(capture.messages()).hasSize(1);

            String invalidA = "{\"schemaVersion\":\"v1\",\"users\":[{\"username\":\""
                + usernameMarker + "\",\"passwordHash\":\"" + hashMarker
                + "\",\"role\":\"" + contentMarker + "\"}]}";
            write(pathMarker + ".json", invalidA);
            Snapshot firstInvalid = registry.snapshot();
            assertThat(firstInvalid.revision()).isGreaterThan(missing.revision());
            assertThat(registry.snapshot()).isSameAs(firstInvalid);
            assertThat(capture.messages()).hasSize(2);

            FileTime modifiedTime = Files.getLastModifiedTime(userFile);
            String invalidB = invalidA.replace(contentMarker, "CONTENT-SECRET-MARKER-37");
            assertThat(invalidB).hasSameSizeAs(invalidA);
            write(pathMarker + ".json", invalidB);
            Files.setLastModifiedTime(userFile, modifiedTime);
            clock.addAndGet(Duration.ofMillis(250).toNanos());
            Snapshot secondInvalid = registry.snapshot();
            assertThat(secondInvalid.revision()).isGreaterThan(firstInvalid.revision());
            assertThat(capture.messages()).hasSize(3);

            assertThat(capture.events()).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(capture.messages()).allSatisfy(message -> {
                assertThat(message)
                    .doesNotContain(pathMarker)
                    .doesNotContain(contentMarker)
                    .doesNotContain(usernameMarker)
                    .doesNotContain(hashMarker)
                    .doesNotContain("CONTENT-SECRET-MARKER-37");
            });
        } finally {
            capture.close();
        }
    }

    private FileStudioUserRegistry registry(Path userFile) {
        return new FileStudioUserRegistry(properties(userFile.toString()));
    }

    private FileStudioUserRegistry registry(
        StudioSecurityProperties properties,
        AtomicLong clock
    ) throws IOException {
        return new FileStudioUserRegistry(
            properties,
            clock::get,
            currentOwner(Path.of(properties.userFile())),
            FileStudioUserRegistry::readBounded
        );
    }

    private StudioSecurityProperties properties(String userFile) {
        return properties(userFile, 100);
    }

    private StudioSecurityProperties properties(String userFile, int maxUsers) {
        return new StudioSecurityProperties(
            userFile,
            Duration.ofHours(24),
            5,
            maxUsers,
            Duration.ofMillis(250),
            8
        );
    }

    private Path write(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        securePermissions(path);
        return path;
    }

    private static void securePermissions(Path path) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(
                path,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
            );
        }
    }

    private static void assumePosix(Path path) throws IOException {
        Assumptions.assumeTrue(
            Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class),
            "POSIX file attributes are unavailable"
        );
    }

    private static String currentOwner(Path path) throws IOException {
        return Files.getOwner(path).getName();
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test coordination timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Test coordination interrupted");
        }
    }

    private static LoggerCapture attachLogger() {
        Logger logger = (Logger) LoggerFactory.getLogger(FileStudioUserRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new LoggerCapture(logger, appender);
    }

    private void assertUnavailable(Path userFile) {
        Snapshot snapshot = registry(userFile).snapshot();
        assertThat(snapshot.available()).isFalse();
        assertThat(snapshot.users()).isEmpty();
    }

    private static String validUsersJson(String... users) {
        return "{\"schemaVersion\":\"v1\",\"users\":[" + String.join(",", users) + "]}";
    }

    private static String userJson(String username, String passwordHash, String role) {
        return "{\"username\":\"" + username + "\",\"passwordHash\":\"" + passwordHash
            + "\",\"role\":\"" + role + "\"}";
    }

    private static byte[] utf32WithBom(String value) {
        byte[] content = value.getBytes(Charset.forName("UTF-32BE"));
        byte[] withBom = new byte[content.length + 4];
        withBom[0] = 0x00;
        withBom[1] = 0x00;
        withBom[2] = (byte) 0xfe;
        withBom[3] = (byte) 0xff;
        System.arraycopy(content, 0, withBom, 4, content.length);
        return withBom;
    }

    private record LoggerCapture(Logger logger, ListAppender<ILoggingEvent> appender)
        implements AutoCloseable {
        private List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        private List<String> messages() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
