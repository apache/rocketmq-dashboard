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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolTokenServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneOffset.UTC);
    private static final Map<String, Object> INPUT = Map.of("instanceId", "instance-dev", "topicName", "orders", "writeQueues", 8);
    // A v1 token (no unique token id) from before single-use consumption was introduced.
    private static final String LEGACY_TOKEN = "djEuMTc4OTA5MjYwMC5BjvIrDnoYt1d8qKs67Cu8qBBPauIQJ6uJg6OUDD5IIg==";
    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolTokenService tokens = new ToolTokenService(new ObjectMapper(), CLOCK, SECRET);

    @Test
    void issuesCompactTokenWithUniqueIdAndVerifiesReorderedInputWithoutControlFields() {
        String token = tokens.issue(context(INPUT));
        assertThat(token).hasSize(84);
        byte[] content = Base64.getDecoder().decode(token);
        assertThat(content).hasSize(63);
        assertThat(new String(content, 0, content.length - 32, StandardCharsets.UTF_8))
                .matches("v2\\.1789092600\\.[0-9a-f]{16}\\.");

        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("writeQueues", 8);
        apply.put("topicName", "orders");
        apply.put("instanceId", "instance-dev");
        apply.put("dry_run", false);
        apply.put("break_glass", true);
        apply.put("reason", "reviewed");
        assertThatCode(() -> tokens.verifyAndConsume(withToken(context(apply), token))).doesNotThrowAnyException();

        Map<String, Object> largeInput = new LinkedHashMap<>(INPUT);
        largeInput.put("description", "x".repeat(100_000));
        assertThat(tokens.issue(context(largeInput))).hasSize(84);
    }

    @Test
    void everyPreviewIssuesAFreshTokenId() {
        List<String> issued = List.of(tokens.issue(context(INPUT)), tokens.issue(context(INPUT)));
        assertThat(issued.get(0)).isNotEqualTo(issued.get(1));
        // Distinct previews of the same operation are independent consumptions.
        issued.forEach(token ->
                assertThatCode(() -> tokens.verifyAndConsume(withToken(context(INPUT), token)))
                        .doesNotThrowAnyException());
    }

    @Test
    void bindsToolCallerInstanceAndBusinessInput() {
        String token = tokens.issue(context(INPUT));
        List<ToolExecutionContext> changedRequests = List.of(
                ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.delete"), INPUT, "alice"),
                ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.update"), INPUT, "bob"),
                ToolExecutionContext.of("other-instance", catalog.getDefinition("rmq.topic.update"), INPUT, "alice"),
                context(Map.of("instanceId", "instance-dev", "topicName", "orders", "writeQueues", 16)));
        // A binding mismatch is rejected as invalid without consuming the token, so the
        // legitimate caller is unaffected by the rejected attempts.
        changedRequests.forEach(request -> assertInvalid(tokens, withToken(request, token)));
        assertThatCode(() -> tokens.verifyAndConsume(withToken(context(INPUT), token))).doesNotThrowAnyException();
    }

    @Test
    void rejectsExpiredTokenAndChangesToVersionExpiryIdOrSignature() {
        String token = tokens.issue(context(INPUT));
        ToolExecutionContext request = withToken(context(INPUT), token);
        ToolTokenService beforeExpiry = new ToolTokenService(new ObjectMapper(),
                Clock.offset(CLOCK, Duration.ofSeconds(599)), SECRET, new Random(7L));
        assertThatCode(() -> beforeExpiry.verifyAndConsume(request)).doesNotThrowAnyException();
        ToolTokenService atExpiry = new ToolTokenService(new ObjectMapper(),
                Clock.offset(CLOCK, Duration.ofMinutes(10)), SECRET, new Random(7L));
        assertInvalid(atExpiry, request);

        // Each mutation preserves the rest of the authenticated token.
        byte[] decoded = Base64.getDecoder().decode(token);
        int tokenIdOffset = "v2.1789092600.".length();
        for (int offset : new int[] {1, 12, tokenIdOffset, tokenIdOffset + 15, 45, decoded.length - 1}) {
            byte[] changed = decoded.clone();
            changed[offset] ^= 1;
            assertInvalid(tokens, withToken(context(INPUT), Base64.getEncoder().encodeToString(changed)));
        }
    }

    @Test
    void rejectsMalformedAndLegacyTokens() {
        List<String> invalid = List.of("not base64!", "A".repeat(97),
                Base64.getEncoder().encodeToString(new byte[32]),
                Base64.getEncoder().encodeToString(("v2.invalid." + "0".repeat(16) + "." + "x".repeat(32))
                        .getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(("v2.1789092600.short." + "x".repeat(32))
                        .getBytes(StandardCharsets.UTF_8)),
                LEGACY_TOKEN,
                "RMQ-HMAC-SHA256 ExpiresAt=1789092600, Signature=" + "A".repeat(64));
        invalid.forEach(token -> assertInvalid(tokens, withToken(context(INPUT), token)));
    }

    @Test
    void consumedTokenIsRejectedWithConflictWhileOtherRejectionsStayInvalid() {
        String token = tokens.issue(context(INPUT));
        ToolExecutionContext request = withToken(context(INPUT), token);
        tokens.verifyAndConsume(request);

        assertThatThrownBy(() -> tokens.verifyAndConsume(request))
                .isInstanceOfSatisfying(ToolExecutionException.class, failure -> {
                    assertThat(failure.getErrorCode()).isEqualTo("CONFLICT");
                    assertThat(failure.getMessage()).contains("already consumed");
                });

        // Expiry takes precedence over the consumed state, and a tampered copy of a consumed
        // token stays invalid: neither rejection leaks that another token was consumed.
        ToolTokenService atExpiry = new ToolTokenService(new ObjectMapper(),
                Clock.offset(CLOCK, Duration.ofMinutes(10)), SECRET, new Random(7L));
        assertInvalid(atExpiry, request);
        byte[] changed = Base64.getDecoder().decode(token);
        changed[changed.length - 1] ^= 1;
        assertInvalid(tokens, withToken(context(INPUT), Base64.getEncoder().encodeToString(changed)));
    }

    @Test
    @Timeout(30)
    void concurrentConsumptionAdmitsExactlyOneCaller() throws Exception {
        String token = tokens.issue(context(INPUT));
        ToolExecutionContext request = withToken(context(INPUT), token);
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier rendezvous = new CyclicBarrier(callers);
            List<Future<String>> outcomes = new ArrayList<>();
            for (int caller = 0; caller < callers; caller++) {
                outcomes.add(pool.submit(consumeAfterBarrier(rendezvous, request)));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> outcome : outcomes) {
                results.add(outcome.get(10, TimeUnit.SECONDS));
            }
            assertThat(results).containsOnlyOnce("CONSUMED");
            assertThat(results).filteredOn("CONFLICT"::equals).hasSize(callers - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void acceptsRawSignatureContainingDotsAndNonUtf8Bytes() {
        // Deterministic token ids make the search for a signature with dots/non-UTF8 bytes stable.
        ToolTokenService seeded = new ToolTokenService(new ObjectMapper(), CLOCK, SECRET, new Random(11L));
        ToolExecutionContext request = context(INPUT);
        String token = null;
        for (int attempt = 0; attempt < 1000 && token == null; attempt++) {
            String candidate = seeded.issue(request);
            byte[] content = Base64.getDecoder().decode(candidate);
            byte[] signature = Arrays.copyOfRange(content, content.length - 32, content.length);
            boolean hasDot = false;
            boolean hasNonUtf8Byte = false;
            for (byte value : signature) {
                hasDot |= value == '.';
                hasNonUtf8Byte |= (value & 0xff) >= 0x80;
            }
            if (hasDot && hasNonUtf8Byte) {
                token = candidate;
            }
        }
        assertThat(token).isNotNull();
        String issued = token;
        assertThatCode(() -> seeded.verifyAndConsume(withToken(request, issued))).doesNotThrowAnyException();
    }

    private Callable<String> consumeAfterBarrier(CyclicBarrier rendezvous, ToolExecutionContext request) {
        return () -> {
            rendezvous.await(10, TimeUnit.SECONDS);
            try {
                tokens.verifyAndConsume(request);
                return "CONSUMED";
            } catch (ToolExecutionException failure) {
                return failure.getErrorCode();
            }
        };
    }

    private ToolExecutionContext context(Map<String, Object> input) {
        return ToolExecutionContext.of("instance-dev", catalog.getDefinition("rmq.topic.update"), input, "alice");
    }

    private static ToolExecutionContext withToken(ToolExecutionContext context, String token) {
        Map<String, Object> input = new LinkedHashMap<>(context.input());
        input.put("confirm_token", token);
        return ToolExecutionContext.of(context.instanceId(), context.definition(), input, context.principal());
    }

    private static void assertInvalid(ToolTokenService service, ToolExecutionContext context) {
        assertThatThrownBy(() -> service.verifyAndConsume(context)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("INVALID_ARGUMENT"));
    }
}
