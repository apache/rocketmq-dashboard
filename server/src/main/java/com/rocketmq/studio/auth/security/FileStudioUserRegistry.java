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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileStudioUserRegistry implements StudioUserRegistry {
    static final int MAX_FILE_BYTES = 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(FileStudioUserRegistry.class);
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final Pattern PASSWORD_HASH_PATTERN =
        Pattern.compile("\\{bcrypt}\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}");
    private static final Set<PosixFilePermission> FORBIDDEN_POSIX_PERMISSIONS = Set.of(
        PosixFilePermission.GROUP_READ,
        PosixFilePermission.GROUP_WRITE,
        PosixFilePermission.GROUP_EXECUTE,
        PosixFilePermission.OTHERS_READ,
        PosixFilePermission.OTHERS_WRITE,
        PosixFilePermission.OTHERS_EXECUTE
    );
    private static final String SHA_256 = "SHA-256";
    private static final ObjectMapper STRICT_MAPPER = createStrictMapper();

    private final StudioSecurityProperties properties;
    private final LongSupplier nanoTime;
    private final String expectedOwnerName;
    private final BoundedFileReader boundedFileReader;
    private final long checkIntervalNanos;
    private final java.util.concurrent.locks.ReentrantLock refreshLock =
        new java.util.concurrent.locks.ReentrantLock();

    private volatile CachedState cachedState = CachedState.initialState();

    public FileStudioUserRegistry(StudioSecurityProperties properties) {
        this(
            properties,
            System::nanoTime,
            currentProcessOwner(),
            FileStudioUserRegistry::readBounded
        );
    }

    FileStudioUserRegistry(
        StudioSecurityProperties properties,
        LongSupplier nanoTime,
        String expectedOwnerName,
        BoundedFileReader boundedFileReader
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.expectedOwnerName = expectedOwnerName;
        this.boundedFileReader = Objects.requireNonNull(boundedFileReader, "boundedFileReader");
        this.checkIntervalNanos = properties.registryCheckInterval().toNanos();
    }

    @Override
    public Snapshot snapshot() {
        long now = nanoTime.getAsLong();
        CachedState observed = cachedState;
        StatResult stat = guardedStat();
        if (!needsRefresh(observed, stat.fastSignal(), now)) {
            return observed.snapshot();
        }

        refreshLock.lock();
        try {
            now = nanoTime.getAsLong();
            CachedState current = cachedState;
            StatResult currentStat = guardedStat();
            if (!needsRefresh(current, currentStat.fastSignal(), now)) {
                return current.snapshot();
            }
            RefreshOutcome outcome = refresh(currentStat);
            CachedState published = publish(current, currentStat.fastSignal(), outcome, now);
            cachedState = published;
            return published.snapshot();
        } finally {
            refreshLock.unlock();
        }
    }

    private boolean needsRefresh(CachedState state, FastSignal signal, long now) {
        return state.uninitialized()
            || !state.fastSignal().equals(signal)
            || Long.compareUnsigned(now - state.lastCheckNanos(), checkIntervalNanos) >= 0;
    }

    private StatResult guardedStat() {
        String configuredPath = properties.configuredUserFile().orElse(null);
        if (configuredPath == null) {
            return StatResult.failure(FailureReason.NOT_CONFIGURED);
        }
        try {
            Path path = Path.of(configuredPath);
            BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            if (attributes.isSymbolicLink()) {
                return StatResult.failure(FailureReason.SYMLINK);
            }
            if (!attributes.isRegularFile()) {
                return StatResult.failure(FailureReason.NOT_REGULAR);
            }
            if (!Files.isReadable(path)) {
                return StatResult.failure(FailureReason.UNREADABLE);
            }
            FileSignal fileSignal = new FileSignal(
                attributes.fileKey(),
                attributes.size(),
                attributes.lastModifiedTime()
            );
            SecurityState securityState = readSecurityState(path);
            if (securityState.failureReason() != null) {
                return StatResult.securityFailure(path, fileSignal, securityState);
            }
            return StatResult.success(path, fileSignal, securityState.identity());
        } catch (InvalidPathException e) {
            return StatResult.failure(FailureReason.INVALID_PATH);
        } catch (NoSuchFileException e) {
            return StatResult.failure(FailureReason.MISSING);
        } catch (AccessDeniedException | SecurityException e) {
            return StatResult.failure(FailureReason.UNREADABLE);
        } catch (IOException | RuntimeException e) {
            return StatResult.failure(FailureReason.STAT_FAILED);
        }
    }

    private RefreshOutcome refresh(StatResult stat) {
        if (stat.failureReason() != null) {
            return RefreshOutcome.unavailable(
                stat.failureReason(),
                stat.fastSignal().stateIdentity()
            );
        }

        try {
            SecurityState securityState = validateFileSecurity(stat.path());
            if (securityState.failureReason() != null) {
                return RefreshOutcome.unavailable(
                    securityState.failureReason(),
                    securityState.identity()
                );
            }

            byte[] bytes = boundedFileReader.read(stat.path(), MAX_FILE_BYTES);
            String digest = sha256Hex(bytes);
            if (bytes.length > MAX_FILE_BYTES) {
                return RefreshOutcome.unavailable(FailureReason.FILE_TOO_LARGE, digest);
            }
            return parse(bytes, digest);
        } catch (AccessDeniedException | SecurityException e) {
            return RefreshOutcome.unavailable(
                FailureReason.UNREADABLE,
                opaqueStateIdentity(FailureReason.UNREADABLE.name())
            );
        } catch (IOException | RuntimeException e) {
            return RefreshOutcome.unavailable(
                FailureReason.READ_FAILED,
                opaqueStateIdentity(FailureReason.READ_FAILED.name())
            );
        }
    }

    private SecurityState validateFileSecurity(Path path) throws IOException {
        BasicFileAttributes current = Files.readAttributes(
            path,
            BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        if (current.isSymbolicLink()) {
            return SecurityState.failure(FailureReason.SYMLINK);
        }
        if (!current.isRegularFile()) {
            return SecurityState.failure(FailureReason.NOT_REGULAR);
        }
        if (!Files.isReadable(path)) {
            return SecurityState.failure(FailureReason.UNREADABLE);
        }
        return readSecurityState(path);
    }

    private SecurityState readSecurityState(Path path) throws IOException {
        if (!Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class)) {
            return SecurityState.available(opaqueStateIdentity("NON_POSIX"));
        }

        PosixFileAttributes attributes = Files.readAttributes(
            path,
            PosixFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS
        );
        String identity = posixSecurityStateIdentity(
            attributes.owner().getName(),
            attributes.permissions()
        );
        if (attributes.isSymbolicLink()) {
            return new SecurityState(FailureReason.SYMLINK, identity);
        }
        if (!Objects.equals(attributes.owner().getName(), expectedOwnerName)) {
            return new SecurityState(FailureReason.WRONG_OWNER, identity);
        }
        Set<PosixFilePermission> permissions = attributes.permissions();
        if (!permissions.contains(PosixFilePermission.OWNER_READ)
            || permissions.stream().anyMatch(FORBIDDEN_POSIX_PERMISSIONS::contains)) {
            return new SecurityState(FailureReason.INSECURE_PERMISSIONS, identity);
        }
        return SecurityState.available(identity);
    }

    private RefreshOutcome parse(byte[] bytes, String digest) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            RegistryDocument document = STRICT_MAPPER.readValue(json, RegistryDocument.class);
            if (!"v1".equals(document.schemaVersion())) {
                return RefreshOutcome.unavailable(FailureReason.INVALID_SCHEMA, digest);
            }
            if (document.users().size() > properties.maxUsers()) {
                return RefreshOutcome.unavailable(FailureReason.TOO_MANY_USERS, digest);
            }

            Map<String, User> users = new LinkedHashMap<>();
            for (UserDocument source : document.users()) {
                User user = validateUser(source);
                if (user == null) {
                    return RefreshOutcome.unavailable(FailureReason.INVALID_USER, digest);
                }
                if (users.putIfAbsent(user.username(), user) != null) {
                    return RefreshOutcome.unavailable(FailureReason.DUPLICATE_USERNAME, digest);
                }
            }
            return RefreshOutcome.available(users, digest);
        } catch (IOException | RuntimeException e) {
            return RefreshOutcome.unavailable(FailureReason.INVALID_JSON, digest);
        }
    }

    private User validateUser(UserDocument source) {
        if (!USERNAME_PATTERN.matcher(source.username()).matches()) {
            return null;
        }
        if (!PASSWORD_HASH_PATTERN.matcher(source.passwordHash()).matches()) {
            return null;
        }

        Role role;
        try {
            role = Role.valueOf(source.role());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new User(
            source.username(),
            source.passwordHash(),
            role,
            fingerprint(source.username(), source.passwordHash(), source.role())
        );
    }

    private CachedState publish(
        CachedState current,
        FastSignal fastSignal,
        RefreshOutcome outcome,
        long now
    ) {
        boolean transition = current.uninitialized()
            || current.snapshot().available() != outcome.available()
            || !Objects.equals(current.contentIdentity(), outcome.contentIdentity())
            || current.failureReason() != outcome.failureReason();
        long revision = transition ? current.snapshot().revision() + 1 : current.snapshot().revision();
        Snapshot snapshot = transition
            ? new Snapshot(revision, outcome.available(), outcome.users())
            : current.snapshot();
        CachedState published = new CachedState(
            false,
            snapshot,
            fastSignal,
            outcome.contentIdentity(),
            outcome.failureReason(),
            now
        );
        if (transition && !outcome.available()) {
            log.warn(
                "Studio user registry unavailable: reason={}, revision={}",
                outcome.failureReason(),
                revision
            );
        }
        return published;
    }

    static byte[] readBounded(Path path, int maximumBytes) throws IOException {
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (
            SeekableByteChannel channel = Files.newByteChannel(path, options);
            InputStream input = Channels.newInputStream(channel)
        ) {
            return input.readNBytes(maximumBytes + 1);
        }
    }

    private static ObjectMapper createStrictMapper() {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
            .maxStringLength(256)
            .build();
        JsonFactory factory = JsonFactory.builder()
            .streamReadConstraints(constraints)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();
        ObjectMapper mapper = new ObjectMapper(factory);
        mapper.enable(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
            DeserializationFeature.FAIL_ON_TRAILING_TOKENS
        );
        mapper.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    private static String fingerprint(String username, String passwordHash, String role) {
        MessageDigest digest = sha256();
        updateCanonical(digest, username);
        updateCanonical(digest, passwordHash);
        updateCanonical(digest, role);
        return toHex(digest.digest());
    }

    private static String posixSecurityStateIdentity(
        String owner,
        Set<PosixFilePermission> permissions
    ) {
        MessageDigest digest = sha256();
        updateCanonical(digest, "POSIX");
        updateCanonical(digest, owner);
        permissions.stream()
            .sorted()
            .map(PosixFilePermission::name)
            .forEach(permission -> updateCanonical(digest, permission));
        return toHex(digest.digest());
    }

    private static String opaqueStateIdentity(String value) {
        MessageDigest digest = sha256();
        updateCanonical(digest, "FILE_STATE");
        updateCanonical(digest, value);
        return toHex(digest.digest());
    }

    private static void updateCanonical(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String sha256Hex(byte[] bytes) {
        return toHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required message digest is unavailable");
        }
    }

    private static String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static String currentProcessOwner() {
        try {
            return System.getProperty("user.name");
        } catch (SecurityException e) {
            return null;
        }
    }

    @FunctionalInterface
    interface BoundedFileReader {
        byte[] read(Path path, int maximumBytes) throws IOException;
    }

    private enum FailureReason {
        NOT_CONFIGURED,
        INVALID_PATH,
        MISSING,
        SYMLINK,
        NOT_REGULAR,
        UNREADABLE,
        STAT_FAILED,
        WRONG_OWNER,
        INSECURE_PERMISSIONS,
        FILE_TOO_LARGE,
        READ_FAILED,
        INVALID_JSON,
        INVALID_SCHEMA,
        TOO_MANY_USERS,
        INVALID_USER,
        DUPLICATE_USERNAME
    }

    private record RegistryDocument(String schemaVersion, List<UserDocument> users) {
    }

    private record UserDocument(String username, String passwordHash, String role) {
    }

    private record FileSignal(Object fileKey, long size, FileTime lastModifiedTime) {
    }

    private record FastSignal(
        FileSignal fileSignal,
        FailureReason failureReason,
        String stateIdentity
    ) {
    }

    private record StatResult(Path path, FastSignal fastSignal, FailureReason failureReason) {
        private static StatResult success(Path path, FileSignal signal, String stateIdentity) {
            return new StatResult(path, new FastSignal(signal, null, stateIdentity), null);
        }

        private static StatResult failure(FailureReason reason) {
            return new StatResult(
                null,
                new FastSignal(null, reason, opaqueStateIdentity(reason.name())),
                reason
            );
        }

        private static StatResult securityFailure(
            Path path,
            FileSignal signal,
            SecurityState securityState
        ) {
            return new StatResult(
                path,
                new FastSignal(signal, securityState.failureReason(), securityState.identity()),
                securityState.failureReason()
            );
        }
    }

    private record SecurityState(FailureReason failureReason, String identity) {
        private static SecurityState available(String identity) {
            return new SecurityState(null, identity);
        }

        private static SecurityState failure(FailureReason reason) {
            return new SecurityState(reason, opaqueStateIdentity(reason.name()));
        }
    }

    private record RefreshOutcome(
        boolean available,
        Map<String, User> users,
        String contentIdentity,
        FailureReason failureReason
    ) {
        private RefreshOutcome {
            users = Map.copyOf(users);
        }

        private static RefreshOutcome available(Map<String, User> users, String contentIdentity) {
            return new RefreshOutcome(true, users, contentIdentity, null);
        }

        private static RefreshOutcome unavailable(FailureReason reason, String contentIdentity) {
            return new RefreshOutcome(false, Map.of(), contentIdentity, reason);
        }
    }

    private record CachedState(
        boolean uninitialized,
        Snapshot snapshot,
        FastSignal fastSignal,
        String contentIdentity,
        FailureReason failureReason,
        long lastCheckNanos
    ) {
        private static CachedState initialState() {
            return new CachedState(
                true,
                new Snapshot(0, false, Map.of()),
                new FastSignal(null, null, opaqueStateIdentity("UNINITIALIZED")),
                null,
                null,
                0
            );
        }
    }
}
